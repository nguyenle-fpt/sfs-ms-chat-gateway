package com.symphony.sfs.ms.chat.datafeed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.symphony.oss.canon.runtime.IEntityFactory;
import com.symphony.oss.canon.runtime.ModelRegistry;
import com.symphony.oss.commons.dom.json.IJsonObject;
import com.symphony.oss.commons.dom.json.ImmutableJsonList;
import com.symphony.oss.commons.dom.json.jackson.JacksonAdaptor;
import com.symphony.oss.commons.immutable.ImmutableByteArray;
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
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool.DatafeedSession;
import com.symphony.sfs.ms.chat.exception.ContentKeyRetrievalException;
import com.symphony.sfs.ms.chat.exception.DecryptionException;
import com.symphony.sfs.ms.chat.exception.UnknownDatafeedUserException;
import com.symphony.sfs.ms.starter.health.MeterManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.MDC;
import org.springframework.cloud.aws.messaging.listener.SqsMessageDeletionPolicy;
import org.springframework.cloud.aws.messaging.listener.annotation.SqsListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
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
  private final MultiListener<String> rawListener = new MultiListener<>();
  private final MultiDatafeedListener datafeedListener = new MultiDatafeedListener();

  private final ForwarderQueueMetrics forwarderQueueMetrics;

  public ForwarderQueueConsumer(ObjectMapper objectMapper, MessageDecryptor messageDecryptor, DatafeedSessionPool datafeedSessionPool, MeterManager meterManager) {
    this.objectMapper = objectMapper;
    this.messageDecryptor = messageDecryptor;
    this.datafeedSessionPool = datafeedSessionPool;
    this.forwarderQueueMetrics = new ForwarderQueueMetrics(meterManager);

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

    modelRegistry = new ModelRegistry().withFactories(factories);
  }

  public void registerDatafeedListener(DatafeedListener listener) {
    datafeedListener.register(listener);
  }

  public void unregisterDatafeedListener(DatafeedListener listener) {
    datafeedListener.unregister(listener);
  }

  @SqsListener(value = {"${aws.sqs.ingestion}"}, deletionPolicy = SqsMessageDeletionPolicy.ON_SUCCESS)
  public void consume(String notification, @Header("ApproximateReceiveCount") String receiveCount) throws IOException {

    ISNSSQSWireObject sqsObject = SNSSQSWireObjectEntity.FACTORY.newInstance(JacksonAdaptor.adaptObject((ObjectNode) objectMapper.readTree(notification)).immutify(), modelRegistry);
    // String payloadType = sqsObject.getJsonObject().getObject("MessageAttributes").getObject("payloadType").get("Value").toString();

    IEnvelope envelope = parseEnvelope(sqsObject);
    String payloadType = envelope.getPayload().getCanonType();

    forwarderQueueMetrics.incomingMessages.increment();
    LOG.debug("Message received | payloadType={} notification={}", payloadType, notification);

    System.out.println(payloadType);
    switch (payloadType) {
      case SocialMessage.TYPE_ID:
        notifySocialMessage(envelope, receiveCount);
        break;
      case MaestroMessage.TYPE_ID:
        notifyMaestroMessage(envelope);
        break;
      default:
        LOG.debug("Unsupported payload type | type={}", payloadType);
    }

    rawListener.accept(notification);
  }

  private void notifySocialMessage(IEnvelope envelope, String receiveCount) {
    // getting the SocialMessage from the envelope
    ISocialMessage socialMessage = SocialMessage.FACTORY.newInstance(envelope.getPayload().getJsonObject(), modelRegistry);
    forwarderQueueMetrics.incomingSocialMessages.increment();

    String messageId = socialMessage.getMessageId().toBase64UrlSafeString();
    String streamId = socialMessage.getThreadId().toBase64UrlSafeString();
    Long timestamp = socialMessage.getIngestionDate().asLong(); // or getActualIngestionDate()?
    IUser fromUser = socialMessage.getFrom(); // or getActualFromUser()?
    MDC.put("streamId", streamId);
    MDC.put("messageId", messageId);
    IJsonObject<?> attributes = envelope.getPayload().getJsonObject().getObject("attributes");
    if (attributes == null) {
      forwarderQueueMetrics.onBlockMessage(BlockingCause.SOCIAL_MESSAGE_MALFORMED, fromUser.getCompany());
      LOG.debug("No attributes in social message | envelope={}, payload={}", envelope, envelope.getPayload());
      return;
    }

    // Count number of retries
    if (Integer.parseInt(receiveCount) > 1) {
      forwarderQueueMetrics.onRetry(fromUser.getCompany());
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
      forwarderQueueMetrics.onBlockMessage(BlockingCause.NO_GATEWAY_MANAGED_ACCOUNT, fromUser.getCompany());
      LOG.warn("IM message with no gateway-managed accounts | stream={} members={} initiator={}", streamId, members, fromUser.getId());
      return;
    }

    try {
      LOG.debug("onIMMessage | streamId={} messageId={} fromUserId={}, members={}, timestamp={} message.getText()={}", streamId, messageId, fromUser.getId(), members, timestamp, socialMessage.getText());
      LOG.debug("onIMMessage | streamId={} messageId={} fromUserId={}, members={}, timestamp={} message.getPresentationML()={}", streamId, messageId, fromUser.getId(), members, timestamp, socialMessage.getPresentationML());
      LOG.debug("onIMMessage | streamId={} messageId={} fromUserId={}, members={}, timestamp={} message.getMessageML()={}", streamId, messageId, fromUser.getId(), members, timestamp, socialMessage.getMessageML());

      String text = unescapeSepcialsCharacters(messageDecryptor.decrypt(socialMessage, managedSession.getUserId()));
      String disclaimer = socialMessage.getDisclaimer();


      // LOG.debug("onIMMessage streamId={} messageId={} fromUserId={}, members={}, timestamp={} decrypted={}", streamId, messageId, fromUser.getId(), members, timestamp, text);

      datafeedListener.onIMMessage(streamId, messageId, fromUser, members, timestamp, text, disclaimer, socialMessage.getAttachments());

      // time in milliseconds between now (the message is sent to WhatsApp) and the ingestion date
      forwarderQueueMetrics.socialMessageProcessingTime.record(Duration.ofMillis(OffsetDateTime.now().toEpochSecond() * 1000 - timestamp));

    } catch (UnknownDatafeedUserException e) {
      forwarderQueueMetrics.onBlockMessage(BlockingCause.UNMANAGED_ACCOUNT, fromUser.getCompany());
      LOG.debug("Unmanaged user | message={}", e.getMessage());
    } catch (ContentKeyRetrievalException | DecryptionException e) {
      LOG.debug("Unable to decrypt social message: stream={} members={} initiator={}", streamId, members, fromUser.getId(), e);
      forwarderQueueMetrics.onBlockMessage(BlockingCause.DECRYPTION_FAILED, fromUser.getCompany());
      throw new RuntimeException(e); // TODO better exception
    }
  }

  private void notifyMaestroMessage(IEnvelope envelope) {
    // getting the MaestroMessage from the envelope
    IMaestroMessage maestroMessage = MaestroMessage.FACTORY.newInstance(envelope.getPayload().getJsonObject(), modelRegistry);
    forwarderQueueMetrics.incomingMaestroMessages.increment();

    MaestroEventType eventType = maestroMessage.getEvent();
    System.out.println(eventType);
    switch (eventType) {
      case CREATE_IM:
        notifyCreateIm(maestroMessage);
        break;
      case CONNECTION_REQUEST_ALERT:
        notifyConnectionRequest(maestroMessage);
        break;
      case JOIN_ROOM:
        notifyJoinRoom(maestroMessage);
        break;

      case CREATE_ROOM:
      case LEAVE_ROOM:
        // TODO implement these events
        LOG.info("Create and Leave room events not supported");
        LOG.debug("Room event | envelope={} maestroMessage={}", envelope, maestroMessage);
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
      LOG.warn("Connection request with no gateway-managed accounts | requesting={} requested={}", requesting.getUsername(), affectedUser.getUsername());
      return;
    }

    if ("pending_incoming".equalsIgnoreCase(status)) {
      LOG.debug("onConnectionRequested | requesting={} requested={}", requesting.getUsername(), affectedUser.getUsername());
      datafeedListener.onConnectionRequested(requesting, affectedUser);
    } else if ("accepted".equalsIgnoreCase(status)) {
      LOG.debug("onConnectionAccepted | requesting={} requested={}", affectedUser.getUsername(), requesting.getUsername());
      datafeedListener.onConnectionAccepted(affectedUser, requesting);
    } else if ("refused".equalsIgnoreCase(status)) {
      LOG.debug("onConnectionRefused | requesting={} requested={}", affectedUser.getUsername(), requesting.getUsername());
      datafeedListener.onConnectionRefused(affectedUser, requesting);
    } else if ("deleted".equalsIgnoreCase(status)) {
      LOG.debug("onConnectionDeleted | requesting={} requested={}", requesting.getUsername(), affectedUser.getUsername());
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
    MDC.put("streamId", streamId);
    boolean crosspod = maestroObject.getRequiredBoolean("crossPod");
    IUser initiator = message.getRequestingUser();

    // at least one of the members must be part of the IM
    boolean atLeastOneHasSession = datafeedSessionPool.sessionExists(initiator.getId().toString());
    if (!atLeastOneHasSession) {
      atLeastOneHasSession = members.stream().anyMatch(datafeedSessionPool::sessionExists);
    }
    if (!atLeastOneHasSession) {
      LOG.warn("IM with no gateway-managed accounts | members={} initiator={}", members, initiator.getUsername());
      return;
    }

    LOG.debug("onIMCreated | members={} initiator={} crosspod={}", members, initiator.getUsername(), crosspod);
    datafeedListener.onIMCreated(streamId, members, initiator, crosspod);
  }

  private void notifyJoinRoom(IMaestroMessage message) {
    IJsonObject<?> maestroObject = message.getJsonObject().getRequiredObject("maestroObject");
    String streamId = Base64.encodeBase64URLSafeString(Base64.decodeBase64(maestroObject.getRequiredString("threadId").getBytes(StandardCharsets.UTF_8)));
    MDC.put("streamId", streamId);

    List<String> members = message.getAffectedUsers().stream()
      .map(IUser::getId)
      .map(PodAndUserId::toString)
      .collect(Collectors.toList());

    IUser initiator = message.getRequestingUser();

    // at least one of the members must be a federated user
    boolean atLeastOneHasSession = members.stream().anyMatch(datafeedSessionPool::sessionExists);
    if (!atLeastOneHasSession) {
      LOG.warn("Room with no gateway-managed accounts | members={} initiator={}", members, initiator.getUsername());
      return;
    }

    datafeedListener.onUserJoinedRoom(streamId, members, initiator);
  }

  private IEnvelope parseEnvelope(ISNSSQSWireObject sqsObject) throws IOException {
    // Payload is a base64 encoded json representation of an envelope within the notification, so it needs to be decoded and parsed again.
    String b64Payload = objectMapper.readTree(sqsObject.getJsonObject().get("Message").toString()).get("payload").asText();
    ImmutableByteArray payload = ImmutableByteArray.newInstance(Base64.decodeBase64(b64Payload));
    return Envelope.FACTORY.newInstance(payload, modelRegistry);
  }

  private String unescapeSepcialsCharacters(String text) {
    List<String> specialsCharacters = Arrays.asList("+", "-", "_", "*", "`");
    for (String specialCharacter : specialsCharacters) {
      text = text.replace("\\" + specialCharacter, specialCharacter);
    }

    return text;
  }

  private enum BlockingCause {
    SOCIAL_MESSAGE_MALFORMED("social message malformed"),
    NO_GATEWAY_MANAGED_ACCOUNT("no gateway managed account"),
    UNMANAGED_ACCOUNT("unmanaged account"),
    DECRYPTION_FAILED("decryption failed");

    private String blockingCause;

    BlockingCause(String blockingCause) {
      this.blockingCause = blockingCause;
    }
  }

  private static class ForwarderQueueMetrics {
    private final Counter incomingMessages;

    private final Counter incomingSocialMessages;
    private final Counter incomingMaestroMessages;

    private final Timer socialMessageProcessingTime;

    private final MeterManager meterManager;


    public ForwarderQueueMetrics(MeterManager meterManager) {
      incomingMessages = meterManager.register(Counter.builder("forwarder.incoming").tag("type", "all"));
      incomingSocialMessages = meterManager.register(Counter.builder("forwarder.incoming").tag("type", "social"));
      incomingMaestroMessages = meterManager.register(Counter.builder("forwarder.incoming").tag("type", "maestro"));
      socialMessageProcessingTime = meterManager.register(Timer.builder("social.message.processing.time").publishPercentileHistogram());

      this.meterManager = meterManager;
    }

    public void onBlockMessage(BlockingCause blockingCause, String companyName) {
      meterManager.register(Counter.builder("blocked.message.from.symphony").tag("cause", blockingCause.blockingCause).tag("company", companyName)).increment();
    }

    public void onRetry(String companyName) {
      meterManager.register(Counter.builder("social.message.retries").tag("company", companyName)).increment();
    }
  }
}
