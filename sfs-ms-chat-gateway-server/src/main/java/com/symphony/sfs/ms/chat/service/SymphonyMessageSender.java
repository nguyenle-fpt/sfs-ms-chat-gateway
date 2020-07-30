package com.symphony.sfs.ms.chat.service;

import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.chat.generated.model.SendMessageFailedProblem;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.util.SymphonySystemMessageTemplateProcessor;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.health.MeterManager;
import com.symphony.sfs.ms.starter.monitoring.CounterUtils;
import com.symphony.sfs.ms.starter.security.StaticSessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import io.micrometer.core.instrument.Counter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SymphonyMessageSender {

  public static final String USER_WAITING_CONFIRMATION = "user.waiting.confirmation";
  public static final String USER_JOINED_GROUP = "user.joined.group";
  public static final String MESSAGE_NOT_DELIVERED = "message.not.delivered";
  public static final String USER_MUST_REENGAGE = "user.must.reengage";
  public static final String USER_BLACKLISTED = "user.blacklisted";
  public static final String USER_NOT_IN_STREAM = "user.not.in.stream";
  public static final String USER_HAS_NOT_JOINED = "user.has.not.joined";
  public static final String MIM_NOT_ALLOWED = "mim.not.allowed";
  public static final String ROOM_NOT_ALLOWED = "room.not.allowed";
  public static final String ONBOARDING_SUCCESS = "onboarding.success";
  public static final String USER_INVITE_EXPIRED = "user.invite.expired";
  public static final String SYSTEM_MESSAGE_ALERT_HANDLEBARS_TEMPLATE = "system_message_alert";
  public static final String SYSTEM_MESSAGE_INFORMATION_HANDLEBARS_TEMPLATE = "system_message_information";
  public static final String SYSTEM_MESSAGE_NOTIFICATION_HANDLEBARS_TEMPLATE = "system_message_notification";
  public static final String SYSTEM_MESSAGE_SIMPLE_HANDLEBARS_TEMPLATE = "system_message_simple";
  private final PodConfiguration podConfiguration;
  private final ChatConfiguration chatConfiguration;
  private final AuthenticationService authenticationService;
  private final FederatedAccountRepository federatedAccountRepository;
  private final StreamService streamService;
  private final SymphonySystemMessageTemplateProcessor templateProcessor;
  private final MessagesMetrics messagesMetrics;

  public SymphonyMessageSender(PodConfiguration podConfiguration, ChatConfiguration chatConfiguration, AuthenticationService authenticationService, FederatedAccountRepository federatedAccountRepository, StreamService streamService, SymphonySystemMessageTemplateProcessor templateProcessor, MeterManager meterManager) {
    this.podConfiguration = podConfiguration;
    this.chatConfiguration = chatConfiguration;
    this.authenticationService = authenticationService;
    this.federatedAccountRepository = federatedAccountRepository;
    this.streamService = streamService;
    this.templateProcessor = templateProcessor;
    this.messagesMetrics = new MessagesMetrics(meterManager);
  }

  public Optional<String> sendRawMessage(SymphonySession session, String streamId, String messageContent) {
    LOG.debug("Send message to symphony");
    return streamService.sendMessage(podConfiguration.getUrl(), new StaticSessionSupplier<>(session), streamId, messageContent);
  }

  public Optional<String> sendRawMessage(String streamId, String fromSymphonyUserId, String messageContent, String toSymphonyUserId) {
    FederatedAccount federatedAccount = federatedAccountRepository.findBySymphonyId(fromSymphonyUserId).orElseThrow(() -> {
      LOG.error("fromSymphonyUser {} not found", fromSymphonyUserId);
      messagesMetrics.onBlockMessage();
      return new SendMessageFailedProblem();
    });

    messagesMetrics.onMessageSent(toSymphonyUserId, streamId);

    SymphonySession userSession = authenticationService.authenticate(
      podConfiguration.getSessionAuth(),
      podConfiguration.getKeyAuth(),
      federatedAccount.getSymphonyUsername(),
      chatConfiguration.getSharedPrivateKey().getData());

    return sendRawMessage(userSession, streamId, messageContent);
  }

  public Optional<String> sendSimpleMessage(String streamId, String fromSymphonyUserId, String messageContent, String toSymphonyUserId) {
    String detemplatized = templateProcessor.process(messageContent, SYSTEM_MESSAGE_SIMPLE_HANDLEBARS_TEMPLATE);
    return sendRawMessage(streamId, fromSymphonyUserId, detemplatized, toSymphonyUserId);
  }

  public Optional<String> sendSimpleMessage(SymphonySession userSession, String streamId, String messageContent) {
    String detemplatized = templateProcessor.process(messageContent, SYSTEM_MESSAGE_SIMPLE_HANDLEBARS_TEMPLATE);
    return sendRawMessage(userSession, streamId, detemplatized);
  }

  public Optional<String> sendNotificationMessage(String streamId, String fromSymphonyUserId, String messageContent, String toSymphonyUserId) {
    return sendRawMessage(streamId, fromSymphonyUserId, templateProcessor.process(messageContent, SYSTEM_MESSAGE_NOTIFICATION_HANDLEBARS_TEMPLATE), toSymphonyUserId);
  }

  public Optional<String> sendNotificationMessage(SymphonySession userSession, String streamId, String messageContent) {
    return sendRawMessage(userSession, streamId, templateProcessor.process(messageContent, SYSTEM_MESSAGE_NOTIFICATION_HANDLEBARS_TEMPLATE));
  }

  public Optional<String> sendInfoMessage(String streamId, String fromSymphonyUserId, String messageContent, String toSymphonyUserId) {
    return sendRawMessage(streamId, fromSymphonyUserId, templateProcessor.process(messageContent, SYSTEM_MESSAGE_INFORMATION_HANDLEBARS_TEMPLATE), toSymphonyUserId);
  }

  public Optional<String> sendInfoMessage(SymphonySession userSession, String streamId, String messageContent) {
    return sendRawMessage(userSession, streamId, templateProcessor.process(messageContent, SYSTEM_MESSAGE_INFORMATION_HANDLEBARS_TEMPLATE));
  }

  public Optional<String> sendAlertMessage(SymphonySession userSession, String streamId, String messageContent) {
    return sendRawMessage(userSession, streamId, templateProcessor.process(messageContent, SYSTEM_MESSAGE_ALERT_HANDLEBARS_TEMPLATE));
  }

  public Optional<String> sendAlertMessage(String streamId, String fromSymphonyUserId, String messageContent, String toSymphonyUserId) {
    return sendRawMessage(streamId, fromSymphonyUserId, templateProcessor.process(messageContent, SYSTEM_MESSAGE_ALERT_HANDLEBARS_TEMPLATE), toSymphonyUserId);
  }

  // TODO i18n?
//  public void sendUntemplatedMessage(String whatsAppId, String streamId, String messageI18nKey, Object... args) {
//    String content = RendererUtils.resolveI18n(messageSource, messageI18nKey, args, messageI18nKey);
//    sendRawMessage(whatsAppId, streamId, content);
//  }

//  public void sendAlertMessage(String whatsAppId, String streamId, String messageI18nKey, Object... args) {
//    String content = RendererUtils.getRenderedMessage(messageSource, new RendererMessage(SmsRenderer.SmsTypes.ALERT, messageI18nKey, args));
//    sendRawMessage(whatsAppId, streamId, content);
//  }
//
//  public void sendUntemplatedMessage(ISymClient client, String streamId, String messageI18nKey, Object... args) {
//    String content = RendererUtils.resolveI18n(messageSource, messageI18nKey, args, messageI18nKey);
//    sendRawMessage(client, streamId, content);
//  }
//
//  public void sendInfoMessage(ISymClient client, String streamId, String messageI18nKey, Object... args) {
//    String content = RendererUtils.getRenderedMessage(messageSource, new RendererMessage(SmsRenderer.SmsTypes.INFORMATION, messageI18nKey, args));
//    sendRawMessage(client, streamId, content);
//  }
//
//  public void sendAlertMessage(ISymClient client, String streamId, String messageI18nKey, Object... args) {
//    String content = RendererUtils.getRenderedMessage(messageSource, new RendererMessage(SmsRenderer.SmsTypes.ALERT, messageI18nKey, args));
//    sendRawMessage(client, streamId, content);
//  }

  private static class MessagesMetrics {
    private final MeterManager meterManager;
    private final Counter blockedMessages;
    private final Counter sentMessages;

    // to count number of symphony users receiving messages in the last minute
    private Map<String, OffsetDateTime> lastReceivedMessagesDatesByUsersId;
    private Counter symphonyUsersReceivingMessage;

    // to count number of conversations for which messages were sent in the last minute
    private Map<String, OffsetDateTime> lastReceivedMessagesDatesByStreamId;
    private Counter conversationsReceivingMessages;

    public MessagesMetrics(MeterManager meterManager) {
      this.meterManager = meterManager;
      this.blockedMessages = this.meterManager.register(Counter.builder("blocked.messages.to.symphony").tag("cause", "unknown sender"));
      this.sentMessages = this.meterManager.register(Counter.builder("sent.messages.to.symphony"));

      this.symphonyUsersReceivingMessage = meterManager.register(Counter.builder("symphony.users.receiving.messages"));
      this.lastReceivedMessagesDatesByUsersId = new ConcurrentHashMap<>();

      this.conversationsReceivingMessages = meterManager.register(Counter.builder("conversations.receiving.messages"));
      this.lastReceivedMessagesDatesByStreamId = new ConcurrentHashMap<>();
    }

    public void onMessageSent(String symphonyId, String streamId) {
      sentMessages.increment();

      // increment the counter and update the last received message date for the Symphony user
      CounterUtils.incrementOncePerMinute(lastReceivedMessagesDatesByUsersId, symphonyId, symphonyUsersReceivingMessage);

      // increment the counter and update the last received message date for the conversation
      CounterUtils.incrementOncePerMinute(lastReceivedMessagesDatesByStreamId, streamId, conversationsReceivingMessages);
    }

    public void onBlockMessage() {
      this.blockedMessages.increment();
    }
  }

}
