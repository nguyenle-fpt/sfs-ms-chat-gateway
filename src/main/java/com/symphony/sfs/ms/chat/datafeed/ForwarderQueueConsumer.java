package com.symphony.sfs.ms.chat.datafeed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.symphony.oss.models.chat.canon.AuthenticationKey;
import com.symphony.oss.models.chat.canon.Entities;
import com.symphony.oss.models.chat.canon.IMaestroMessage;
import com.symphony.oss.models.chat.canon.ISNSSQSWireObject;
import com.symphony.oss.models.chat.canon.MaestroEventType;
import com.symphony.oss.models.chat.canon.MaestroMessage;
import com.symphony.oss.models.chat.canon.MaestroPayload;
import com.symphony.oss.models.chat.canon.SNSSQSWireObjectEntity;
import com.symphony.oss.models.chat.canon.UserMention;
import com.symphony.oss.models.chat.canon.facade.ISocialMessage;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.oss.models.chat.canon.facade.MaestroPayloadContainer;
import com.symphony.oss.models.chat.canon.facade.SocialMessage;
import com.symphony.oss.models.chat.canon.facade.User;
import com.symphony.oss.models.core.canon.facade.Envelope;
import com.symphony.oss.models.core.canon.facade.IEnvelope;
import com.symphony.oss.models.core.canon.facade.PodAndUserId;
import com.symphony.oss.models.core.canon.facade.UserId;
import com.symphony.oss.models.fundamental.FundamentalModelRegistry;
import com.symphony.sfs.ms.chat.exception.UnknownDatafeedUserException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.cloud.aws.messaging.listener.SqsMessageDeletionPolicy;
import org.springframework.cloud.aws.messaging.listener.annotation.SqsListener;
import org.springframework.stereotype.Component;
import org.symphonyoss.s2.canon.runtime.ModelRegistry;
import org.symphonyoss.s2.common.dom.json.IJsonObject;
import org.symphonyoss.s2.common.dom.json.jackson.JacksonAdaptor;
import org.symphonyoss.s2.common.immutable.ImmutableByteArray;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ForwarderQueueConsumer {

  private final ObjectMapper objectMapper;
  private final ModelRegistry modelRegistry;
  private final MessageDecryptor messageDecryptor;

  @Getter
  private final MultiListener<String> rawListeners = new MultiListener<>();

  @Getter
  private final MultiDatafeedListener datafeedListener = new MultiDatafeedListener();

  public ForwarderQueueConsumer(ObjectMapper objectMapper, MessageDecryptor messageDecryptor) {
    this.objectMapper = objectMapper;
    this.messageDecryptor = messageDecryptor;

    // To refine, is ChatModel.FACTORIES a viable alternative?
    modelRegistry = new FundamentalModelRegistry().withFactories(
      SNSSQSWireObjectEntity.FACTORY,
      Envelope.FACTORY,
      MaestroMessage.FACTORY,
      SocialMessage.FACTORY,
      Entities.FACTORY,
      User.FACTORY,
      UserMention.FACTORY,
      MaestroPayloadContainer.FACTORY,
      MaestroPayload.FACTORY,
      AuthenticationKey.FACTORY);
  }

  @SqsListener(value = {"${aws.sqs.ingestion:sfs-dev-federation-events}"}, deletionPolicy = SqsMessageDeletionPolicy.ON_SUCCESS)
  public void consume(String notification) throws IOException {
    ISNSSQSWireObject sqsObject = SNSSQSWireObjectEntity.FACTORY.newInstance(JacksonAdaptor.adaptObject((ObjectNode) objectMapper.readTree(notification)).immutify(), modelRegistry);
    String payloadType = sqsObject.getJsonObject().getObject("MessageAttributes").getObject("payloadType").get("Value").toString();

    System.out.println(payloadType);
    switch (payloadType) {
      case SocialMessage.TYPE_ID:
        notifySocialMessage(sqsObject);
        break;
      case MaestroMessage.TYPE_ID:
        notifyMaestroMessage(sqsObject);
        break;
      default:
        LOG.debug("Unsupported payload type {}", payloadType);
    }

    rawListeners.accept(notification);
  }

  private void notifySocialMessage(ISNSSQSWireObject sqsObject) throws IOException {
    IEnvelope envelope = parseEnvelope(sqsObject);

    // Getting the SocialMessage from the envelope.
    ISocialMessage socialMessage = SocialMessage.FACTORY.newInstance(envelope.getPayload().getJsonObject(), modelRegistry);

    String messageId = socialMessage.getMessageId().toBase64UrlSafeString();
    String streamId = socialMessage.getThreadId().toBase64UrlSafeString();
    Long timestamp = socialMessage.getIngestionDate().asLong(); // or getActualIngestionDate()?

    try {
      LOG.debug("onIMMessage streamId={} messageId={} timestamp={} message={}", streamId, messageId, timestamp, socialMessage.getText());
      String text = messageDecryptor.decrypt(socialMessage);
      datafeedListener.onIMMessage(streamId, messageId, timestamp, text);
    } catch (UnknownDatafeedUserException e) {
      LOG.debug("Unmanaged user {}", e.getMessage());
    }
  }

  private void notifyMaestroMessage(ISNSSQSWireObject sqsObject) throws IOException {
    IEnvelope envelope = parseEnvelope(sqsObject);

    // Getting the MaestroMessage from the envelope.
    IMaestroMessage maestroMessage = MaestroMessage.FACTORY.newInstance(envelope.getPayload().getJsonObject(), modelRegistry);

    MaestroEventType eventType = maestroMessage.getEvent();
    System.out.println(eventType);
    switch (eventType) {
      case CREATE_IM:
        notifyCreateIm(maestroMessage);
        break;
      case CONNECTION_REQUEST_ALERT:
        notifyConnectionRequest(maestroMessage);
        break;
      case CREATE_ROOM:
      case JOIN_ROOM:
      case LEAVE_ROOM:
        // TODO implement these events
        System.out.println("==== RAW ====");
        System.out.println(sqsObject);
        System.out.println("==== PAY ====");
        System.out.println(maestroMessage);
        System.out.println("=============");
        break;
    }
  }

  private void notifyConnectionRequest(IMaestroMessage message) {
    // apparently, there is no payload class for the CONNECTION_REQUEST_ALERT type -_-"
    String status = message.getPayload().getJsonObject().getRequiredString("status");
    IUser affectedUser = message.getAffectedUsers().get(0);
    IUser requesting = message.getRequestingUser();

    if ("pending_incoming".equalsIgnoreCase(status)) {
      LOG.debug("onIMCreated requesting={} requested={}", requesting.getUsername(), affectedUser.getUsername());
      datafeedListener.onConnectionRequested(requesting, affectedUser);
    } else if ("accepted".equalsIgnoreCase(status)) {
      LOG.debug("onConnectionAccepted requesting={} requested={}", affectedUser.getUsername(), requesting.getUsername());
      datafeedListener.onConnectionAccepted(affectedUser, requesting);
    } else if ("refused".equalsIgnoreCase(status)) {
      LOG.debug("onConnectionRefused requesting={} requested={}", affectedUser.getUsername(), requesting.getUsername());
      datafeedListener.onConnectionRefused(affectedUser, requesting);
    } else {
      throw new IllegalArgumentException("Unknown connection request status type: " + status);
    }
  }

  private void notifyCreateIm(IMaestroMessage message) {
    // we only have user id in the JSON payload
    List<Long> members = message.getAffectedUsers().stream()
      .map(IUser::getId)
      .map(PodAndUserId::getUserId)
      .map(UserId::asLong)
      .collect(Collectors.toList());

    // apparently, there is no entity class for the maestroObject property -_-"
    IJsonObject<?> maestroObject = message.getJsonObject().getRequiredObject("maestroObject");
    String streamId = maestroObject.getRequiredString("streamId");
    boolean crosspod = maestroObject.getRequiredBoolean("crossPod");
    IUser initiator = message.getRequestingUser();

    // ICreateIMMaestroPayload payload = CreateIMMaestroPayload.FACTORY.newInstance(message.getPayload().getJsonObject(), modelRegistry);

    LOG.debug("onIMCreated stream={} members={} initiator={} crosspod={}", streamId, members, initiator.getUsername(), crosspod);
    datafeedListener.onIMCreated(streamId, members, initiator, crosspod);
  }

  private IEnvelope parseEnvelope(ISNSSQSWireObject sqsObject) throws IOException {
    // Payload is a base64 encoded json representation of an envelope within the notification, so it needs to be decoded and parsed again.
    String b64Payload = objectMapper.readTree(sqsObject.getJsonObject().get("Message").toString()).get("payload").asText();
    ImmutableByteArray payload = ImmutableByteArray.newInstance(Base64.decodeBase64(b64Payload));
    return Envelope.FACTORY.newInstance(payload, modelRegistry);
  }
}
