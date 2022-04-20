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
import com.symphony.oss.models.chat.canon.IAttachment;
import com.symphony.oss.models.chat.canon.IMaestroMessage;
import com.symphony.oss.models.chat.canon.ISNSSQSWireObject;
import com.symphony.oss.models.chat.canon.IUserEntity;
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
import com.symphony.sfs.ms.chat.exception.DecryptionException;
import com.symphony.sfs.ms.chat.service.MessageIOMonitor;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.health.MeterManager;
import com.symphony.sfs.ms.starter.security.SessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.crypto.exception.ContentKeyRetrievalException;
import com.symphony.sfs.ms.starter.symphony.crypto.exception.UnknownUserException;
import io.awspring.cloud.messaging.listener.SqsMessageDeletionPolicy;
import io.awspring.cloud.messaging.listener.annotation.SqsListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.MDC;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseFromSymphony.DECRYPTION_FAILED;
import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseFromSymphony.NO_GATEWAY_MANAGED_ACCOUNT;
import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseFromSymphony.SOCIAL_MESSAGE_MALFORMED;
import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseFromSymphony.UNMANAGED_ACCOUNT;

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
  private final MessageIOMonitor messageIOMonitor;

  private final BotConfiguration botConfiguration;


  public ForwarderQueueConsumer(ObjectMapper objectMapper, MessageDecryptor messageDecryptor, DatafeedSessionPool datafeedSessionPool, MessageIOMonitor messageIOMonitor, MeterManager meterManager, BotConfiguration botConfiguration) {
    this.objectMapper = objectMapper;
    this.messageDecryptor = messageDecryptor;
    this.datafeedSessionPool = datafeedSessionPool;
    this.messageIOMonitor = messageIOMonitor;
    this.botConfiguration = botConfiguration;
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
    // CES-1791
    // Before we handle the incoming message, we clear the MDC for the current thread
    MDC.clear();

    ISNSSQSWireObject sqsObject = SNSSQSWireObjectEntity.FACTORY.newInstance(JacksonAdaptor.adaptObject((ObjectNode) objectMapper.readTree(notification)).immutify(), modelRegistry);
    // String payloadType = sqsObject.getJsonObject().getObject("MessageAttributes").getObject("payloadType").get("Value").toString();

    IEnvelope envelope = parseEnvelope(sqsObject);
    String payloadType = envelope.getPayload().getCanonType();

    forwarderQueueMetrics.incomingMessages.increment();
    //LOG.debug("Message received | payloadType={} notification={}", payloadType, notification);

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
    LOG.debug("ENVELOPE | envelope={}", envelope);

    IJsonObject<?> attributes = envelope.getPayload().getJsonObject().getObject("attributes");
    String chatType = envelope.getPayload().getJsonObject().getString("chatType", "IM");
    Set<PodAndUserId> distList = envelope.getDistributionList();
    if (attributes == null && distList == null) {
      messageIOMonitor.onMessageBlockFromSymphony(SOCIAL_MESSAGE_MALFORMED, streamId);
      LOG.debug("No attributes in social message | envelope={}, payload={}", envelope, envelope.getPayload());
      return;
    }
    ParentRelationshipType parentRelationshipType = ParentRelationshipType.fromJsonObject(envelope.getPayload().getJsonObject());

    // Count number of retries
    if (Integer.parseInt(receiveCount) > 1) {
      forwarderQueueMetrics.onRetry(fromUser.getCompany());
    }
    List<String> members = Collections.emptyList();
    if(attributes != null && attributes.containsKey("dist")) {
      ImmutableJsonList memberJsonList = ((ImmutableJsonList) attributes.get("dist"));
      if (memberJsonList != null) {
        members = StreamSupport
          .stream(memberJsonList.spliterator(), false)
          .map(Object::toString)
          .collect(Collectors.toList());
      }
    } else {
      members = distList.stream().map(PodAndUserId::toString).collect(Collectors.toList());
    }

    LOG.debug("onIMMessage | streamId={} messageId={} fromUserId={}, members={}, timestamp={} message.getText()={}", streamId, messageId, fromUser.getId(), members, timestamp, socialMessage.getText());
    LOG.debug("onIMMessage | streamId={} messageId={} fromUserId={}, members={}, timestamp={} message.getPresentationML()={}", streamId, messageId, fromUser.getId(), members, timestamp, socialMessage.getPresentationML());
    LOG.debug("onIMMessage | streamId={} messageId={} fromUserId={}, members={}, timestamp={} message.getMessageML()={}", streamId, messageId, fromUser.getId(), members, timestamp, socialMessage.getMessageML());

    List<IAttachment> attachments = new ArrayList<>();

    if(socialMessage.getAttachments() != null) {
      attachments.addAll(socialMessage.getAttachments());
    }

    if(socialMessage.getFileKeyEncryptedAttachments() != null) {
      attachments.addAll(socialMessage.getFileKeyEncryptedAttachments());
    }

    GatewaySocialMessage gatewaySocialMessage = GatewaySocialMessage.builder()
      .streamId(streamId)
      .messageId(messageId)
      .fromUser(fromUser)
      .members(members)
      .timestamp(timestamp)
      .chime(socialMessage.getIsChime())
      .disclaimer(socialMessage.getDisclaimer())
      .attachments(attachments)
      .parentRelationshipType(parentRelationshipType)
      .chatType(chatType)
      .build();

    Optional<Pair<String, String>> managedSessionId = getManagedSessionId(gatewaySocialMessage);
    if (managedSessionId.isEmpty()) {
      messageIOMonitor.onMessageBlockFromSymphony(NO_GATEWAY_MANAGED_ACCOUNT, streamId);
      LOG.warn("IM message with no gateway-managed accounts | stream={} members={} initiator={}", streamId, members, fromUser.getId());
      return;
    }

    try {
      messageDecryptor.decrypt(socialMessage, managedSessionId.get().getLeft(), managedSessionId.get().getRight(), gatewaySocialMessage);
      //LOG.debug("onIMMessage | decryptedSocialMessage={}", gatewaySocialMessage); //To uncomment for local execution
      datafeedListener.onIMMessage(gatewaySocialMessage);

      // time in milliseconds between now (the message is sent to WhatsApp) and the ingestion date
      forwarderQueueMetrics.socialMessageProcessingTime.record(Duration.ofMillis(OffsetDateTime.now().toEpochSecond() * 1000 - timestamp));

    } catch (UnknownUserException e) {
      messageIOMonitor.onMessageBlockFromSymphony(UNMANAGED_ACCOUNT, streamId);
      LOG.debug("Unmanaged user | message={}", e.getMessage());
    } catch (ContentKeyRetrievalException | DecryptionException e) {
      LOG.debug("Unable to decrypt social message: stream={} members={} initiator={}", streamId, members, fromUser.getId(), e);
      messageIOMonitor.onMessageBlockFromSymphony(DECRYPTION_FAILED, streamId);
      throw new RuntimeException(e); // TODO better exception
    }
  }

  private Optional<Pair<String, String>> getManagedSessionId(GatewaySocialMessage gatewaySocialMessage) {
    // at least one of the members must be part of the IM
    String id = gatewaySocialMessage.getFromUserId();
    SessionSupplier<SymphonySession> managedSession = datafeedSessionPool.getSessionSupplier(id);
    if (managedSession == null) {
      for(String member: gatewaySocialMessage.getMembers()) {
        managedSession = datafeedSessionPool.getSessionSupplier(member);
        if (managedSession != null) {
          return Optional.of(Pair.of(member, managedSession.getPrincipal()));
        }
      }
    } else {
      return Optional.of(Pair.of(id, managedSession.getPrincipal()));
    }
    return Optional.empty();
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
      case LEAVE_ROOM:
        notifyLeaveRoom(maestroMessage);
        break;
      case CREATE_ROOM:
        // TODO implement these events
        LOG.info("Create room events not supported");
        LOG.debug("Room event | envelope={} maestroMessage={}", envelope, maestroMessage);
        break;
    }
  }

  private void notifyLeaveRoom(IMaestroMessage message) {
    IJsonObject<?> maestroObject = message.getJsonObject().getRequiredObject("maestroObject");
    String streamId = Base64.encodeBase64URLSafeString(Base64.decodeBase64(maestroObject.getRequiredString("threadId").getBytes(StandardCharsets.UTF_8)));
    MDC.put("streamId", streamId);

    List<String> members = message.getAffectedUsers().stream()
      .map(IUser::getId)
      .map(PodAndUserId::toString)
      .collect(Collectors.toList());

    IUser initiator = message.getRequestingUser();

    // Check if this is a room that we manage
    // Note: we cannot check if there are federated users in the room as in this event we only receive the concerned users
    // and not all members of the room
    if (!botConfiguration.getSymphonyId().equals(Long.toString(maestroObject.getRequiredLong("creator")))) {
      LOG.warn("Leave room not managed by bot | creator={} initiator={}",maestroObject.getRequiredLong("creator"), initiator.getId());
      return;
    }
    LOG.info("Users leave room | requestingUser={} affectedUsers={}",
      initiator.getId(),
      message.getAffectedUsers().stream().map(IUserEntity::getId).collect(Collectors.toList())
    );
    datafeedListener.onUserLeftRoom(streamId, initiator, message.getAffectedUsers());
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

    // Check if this is a room that we manage
    // Note: we cannot check if there are federated users in the room as in this event we only receive the concerned users
    // and not all members of the room
    if (!botConfiguration.getSymphonyId().equals(Long.toString(maestroObject.getRequiredLong("creator")))) {
      LOG.warn("Join room not managed by bot | creator={} initiator={}", maestroObject.getRequiredLong("creator"), initiator.getUsername());
      return;
    }

    // JOIN_ROOM events are sent with a duplicate for advisors
    // On the first on the payload.pending field is set to true on the second one it's false
    // For the federated users these field is set to false
    // In order to keep one and only one JOIN_ROOM event coming from advisors and federated users, we keep the ones with payload.pending: false
    if (message.getJsonObject().getRequiredObject("payload").getRequiredBoolean("pending")) {
      LOG.warn("Ignoring advisor's duplicate JOIN_ROOM event | members={} initiator={}", members, initiator.getUsername());
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

  private static class ForwarderQueueMetrics {
    private final Counter incomingMessages;

    private final Counter incomingSocialMessages;
    private final Counter incomingMaestroMessages;

    private final Timer socialMessageProcessingTime;

    private final MeterManager meterManager;


    public ForwarderQueueMetrics(MeterManager meterManager) {
      incomingMessages = meterManager.register(Counter.builder("sfs.forwarder.incoming").tag("type", "all"));
      incomingSocialMessages = meterManager.register(Counter.builder("sfs.forwarder.incoming").tag("type", "social"));
      incomingMaestroMessages = meterManager.register(Counter.builder("sfs.forwarder.incoming").tag("type", "maestro"));
      socialMessageProcessingTime = meterManager.register(Timer.builder("sfs.social.message.processing.time").publishPercentileHistogram());

      this.meterManager = meterManager;
    }

    public void onRetry(String companyName) {
      meterManager.register(Counter.builder("sfs.social.message.retries").tag("company", companyName)).increment();
    }
  }
}
