package com.symphony.sfs.ms.chat.datafeed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.symphony.oss.models.chat.canon.ChatModel;
import com.symphony.oss.models.chat.canon.IMaestroMessage;
import com.symphony.oss.models.chat.canon.ISNSSQSWireObject;
import com.symphony.oss.models.chat.canon.MaestroEventType;
import com.symphony.oss.models.chat.canon.MaestroMessage;
import com.symphony.oss.models.chat.canon.SNSSQSWireObjectEntity;
import com.symphony.oss.models.chat.canon.facade.ISocialMessage;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.oss.models.chat.canon.facade.SocialMessage;
import com.symphony.oss.models.core.canon.CoreModel;
import com.symphony.oss.models.core.canon.facade.Envelope;
import com.symphony.oss.models.core.canon.facade.IEnvelope;
import com.symphony.oss.models.core.canon.facade.PodAndUserId;
import com.symphony.oss.models.crypto.canon.CryptoModel;
import com.symphony.oss.models.fundamental.FundamentalModelRegistry;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool.DatafeedSession;
import com.symphony.sfs.ms.chat.exception.UnknownDatafeedUserException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.cloud.aws.messaging.listener.SqsMessageDeletionPolicy;
import org.springframework.cloud.aws.messaging.listener.annotation.SqsListener;
import org.springframework.stereotype.Service;
import org.symphonyoss.s2.canon.runtime.IEntityFactory;
import org.symphonyoss.s2.canon.runtime.ModelRegistry;
import org.symphonyoss.s2.common.dom.json.IJsonObject;
import org.symphonyoss.s2.common.dom.json.ImmutableJsonList;
import org.symphonyoss.s2.common.dom.json.JsonString;
import org.symphonyoss.s2.common.dom.json.jackson.JacksonAdaptor;
import org.symphonyoss.s2.common.immutable.ImmutableByteArray;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Service
@Slf4j
public class ForwarderQueueConsumer {

  private final ObjectMapper objectMapper;
  private final MessageDecryptor messageDecryptor;
  private final DatafeedSessionPool datafeedSessionPool;
  private final ModelRegistry modelRegistry;

  @Getter
  private final MultiListener<String> rawListeners = new MultiListener<>();

  private final MultiDatafeedListener datafeedListener = new MultiDatafeedListener();

  public ForwarderQueueConsumer(ObjectMapper objectMapper, MessageDecryptor messageDecryptor, DatafeedSessionPool datafeedSessionPool) {
    this.objectMapper = objectMapper;
    this.messageDecryptor = messageDecryptor;
    this.datafeedSessionPool = datafeedSessionPool;

    // Hell...
    IEntityFactory<?, ?, ?>[] factories = Stream.of(
      Stream.of(CoreModel.FACTORIES),
      // Stream.of(FundamentalModel.FACTORIES),
      // Stream.of(AllegroModel.FACTORIES),
      Stream.of(ChatModel.FACTORIES),
      Stream.of(CryptoModel.FACTORIES)
      // Stream.of(SystemModel.FACTORIES)
    )
      .reduce(Stream::concat)
      .orElseGet(Stream::empty)
      .toArray(IEntityFactory<?, ?, ?>[]::new);

    modelRegistry = new FundamentalModelRegistry().withFactories(factories);
  }

  public void registerDatafeedListener(DatafeedListener listener) {
    datafeedListener.register(listener);
  }

  public void unregisterDatafeedListener(DatafeedListener listener) {
    datafeedListener.unregister(listener);
  }

  @SqsListener(value = {"${aws.sqs.ingestion}"}, deletionPolicy = SqsMessageDeletionPolicy.ON_SUCCESS)
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

    // getting the SocialMessage from the envelope
    ISocialMessage socialMessage = SocialMessage.FACTORY.newInstance(envelope.getPayload().getJsonObject(), modelRegistry);

    String messageId = socialMessage.getMessageId().toBase64UrlSafeString();
    String streamId = socialMessage.getThreadId().toBase64UrlSafeString();
    Long timestamp = socialMessage.getIngestionDate().asLong(); // or getActualIngestionDate()?
    IUser fromUser = socialMessage.getFrom(); // or getActualFromUser()?

    IJsonObject<?> attributes = envelope.getPayload().getJsonObject().getObject("attributes");
    if (attributes == null) {
      LOG.debug("No attributes in social message: object={}, envelope={}, payload={}", sqsObject, envelope, envelope.getPayload());
      return;
    }
    ImmutableJsonList memberJsonList = ((ImmutableJsonList) attributes.get("dist"));
    List<String> members = Collections.emptyList();
    if (memberJsonList != null) {
      members = StreamSupport
        .stream(memberJsonList.spliterator(), false)
        .map(Object::toString)
        .collect(Collectors.toList());
    }

    // at least one of the members must be part of the IM
    DatafeedSession managedSession = datafeedSessionPool.getSession(fromUser.getId().toString());
    if (managedSession == null) {
      managedSession = members.stream()
        .map(datafeedSessionPool::getSession)
        .filter(Objects::nonNull)
        .findAny()
        .orElse(null);
    }
    if (managedSession == null) {
      LOG.warn("IM message with no gateway-managed accounts: stream={} members={} initiator={}", streamId, members, fromUser.getId());
      return;
    }

    try {
      LOG.debug("onIMMessage streamId={} messageId={} fromUserId={}, members={}, timestamp={} message={}", streamId, messageId, fromUser.getId(), members, timestamp, socialMessage.getText());
      String text = messageDecryptor.decrypt(socialMessage, managedSession.getUserId());
      datafeedListener.onIMMessage(streamId, messageId, fromUser, members, timestamp, text);
    } catch (UnknownDatafeedUserException e) {
      LOG.debug("Unmanaged user {}", e.getMessage());
    }
  }

  private void notifyMaestroMessage(ISNSSQSWireObject sqsObject) throws IOException {
    IEnvelope envelope = parseEnvelope(sqsObject);

    // getting the MaestroMessage from the envelope
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

    // if there is no session for both members of the connection request, it means that this user is not managed by our gateway
    if (!datafeedSessionPool.sessionExists(requesting.getId().toString()) && !datafeedSessionPool.sessionExists(affectedUser.getId().toString())) {
      LOG.warn("Connection request with no gateway-managed accounts: {}, {}", requesting.getUsername(), affectedUser.getUsername());
      return;
    }

    if ("pending_incoming".equalsIgnoreCase(status)) {
      LOG.debug("onConnectionRequested requesting={} requested={}", requesting.getUsername(), affectedUser.getUsername());
      datafeedListener.onConnectionRequested(requesting, affectedUser);
    } else if ("accepted".equalsIgnoreCase(status)) {
      LOG.debug("onConnectionAccepted requesting={} requested={}", affectedUser.getUsername(), requesting.getUsername());
      datafeedListener.onConnectionAccepted(affectedUser, requesting);
    } else if ("refused".equalsIgnoreCase(status)) {
      LOG.debug("onConnectionRefused requesting={} requested={}", affectedUser.getUsername(), requesting.getUsername());
      datafeedListener.onConnectionRefused(affectedUser, requesting);
    } else if ("deleted".equalsIgnoreCase(status)) {
      LOG.debug("onConnectionDeleted requesting={} requested={}", requesting.getUsername(), affectedUser.getUsername());
      datafeedListener.onConnectionDeleted(requesting, affectedUser);
    } else {
      throw new IllegalArgumentException("Unknown connection request status type: " + status);
    }
  }

  private void notifyCreateIm(IMaestroMessage message) {
    // we only have user id in the JSON payload
    List<String> members = message.getAffectedUsers().stream()
      .map(IUser::getId)
      .map(PodAndUserId::toString)
      .collect(Collectors.toList());

    // apparently, there is no entity class for the maestroObject property -_-"
    IJsonObject<?> maestroObject = message.getJsonObject().getRequiredObject("maestroObject");
    String streamId = Base64.encodeBase64URLSafeString(Base64.decodeBase64(maestroObject.getRequiredString("streamId").getBytes(StandardCharsets.UTF_8)));
    boolean crosspod = maestroObject.getRequiredBoolean("crossPod");
    IUser initiator = message.getRequestingUser();

    // at least one of the members must be part of the IM
    boolean atLeastOneHasSession = datafeedSessionPool.sessionExists(initiator.getId().toString());
    if (!atLeastOneHasSession) {
      atLeastOneHasSession = members.stream().anyMatch(datafeedSessionPool::sessionExists);
    }
    if (!atLeastOneHasSession) {
      LOG.warn("IM with no gateway-managed accounts: stream={} members={} initiator={}", streamId, members, initiator.getUsername());
      return;
    }

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
