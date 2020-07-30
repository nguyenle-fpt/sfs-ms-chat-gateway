package com.symphony.sfs.ms.chat.service;

import com.google.common.annotations.VisibleForTesting;
import com.symphony.oss.models.chat.canon.IAttachment;
import com.symphony.oss.models.chat.canon.UserEntity;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.oss.models.chat.canon.facade.User;
import com.symphony.sfs.ms.admin.generated.model.EmpEntity;
import com.symphony.sfs.ms.chat.datafeed.DatafeedListener;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.exception.UnknownDatafeedUserException;
import com.symphony.sfs.ms.chat.generated.model.MessageId;
import com.symphony.sfs.ms.chat.generated.model.MessageInfo;
import com.symphony.sfs.ms.chat.generated.model.MessageSenderNotFoundProblem;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessageFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessagesResponse;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.external.AdminClient;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyService;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.health.MeterManager;
import com.symphony.sfs.ms.starter.monitoring.CounterUtils;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import io.micrometer.core.instrument.Counter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.jsoup.nodes.Entities.escape;

@Service
@Slf4j
public class SymphonyMessageService implements DatafeedListener {

  private final EmpClient empClient;
  private final FederatedAccountRepository federatedAccountRepository;
  private final ForwarderQueueConsumer forwarderQueueConsumer;
  private final DatafeedSessionPool datafeedSessionPool;
  private final SymphonyMessageSender symphonyMessageSender;
  private final AdminClient adminClient;
  private final EmpSchemaService empSchemaService;
  private final SymphonyService symphonyService;
  private final PodConfiguration podConfiguration;
  private final MessageMetrics messageMetrics;

  public SymphonyMessageService(EmpClient empClient, FederatedAccountRepository federatedAccountRepository, ForwarderQueueConsumer forwarderQueueConsumer, DatafeedSessionPool datafeedSessionPool, SymphonyMessageSender symphonyMessageSender, AdminClient adminClient, EmpSchemaService empSchemaService, SymphonyService symphonyService, PodConfiguration podConfiguration, MeterManager meterManager) {
    this.empClient = empClient;
    this.federatedAccountRepository = federatedAccountRepository;
    this.forwarderQueueConsumer = forwarderQueueConsumer;
    this.datafeedSessionPool = datafeedSessionPool;
    this.symphonyMessageSender = symphonyMessageSender;
    this.adminClient = adminClient;
    this.empSchemaService = empSchemaService;
    this.symphonyService = symphonyService;
    this.podConfiguration = podConfiguration;
    this.messageMetrics = new MessageMetrics(meterManager);
  }

  @PostConstruct
  @VisibleForTesting
  public void registerAsDatafeedListener() {
    forwarderQueueConsumer.registerDatafeedListener(this);
  }

  @Override
  @NewSpan
  public void onIMMessage(String streamId, String messageId, IUser fromSymphonyUser, List<String> members, Long timestamp, String message, String disclaimer, List<IAttachment> attachments) {

    if (members.size() < 2) {
      messageMetrics.onMessageBlock(BlockingCause.NOT_ENOUGH_MEMBER, fromSymphonyUser.getCompany());
      LOG.warn("(M)IM with has less than 2 members  | streamId={} messageId={}", streamId, messageId);
      return;
    }

    // Recipients correspond to all userIds which are not fromUserId
    String fromSymphonyUserId = fromSymphonyUser.getId().toString();
    List<String> toUserIds = members.stream().filter(id -> !id.equals(fromSymphonyUserId)).collect(Collectors.toList());

    Optional<FederatedAccount> fromFederatedAccount = federatedAccountRepository.findBySymphonyId(fromSymphonyUserId);
    boolean isMessageFromFederatedUser = fromFederatedAccount.isPresent();

    if (!isMessageFromFederatedUser) {
      // (M)IM from Symphony to EMP(s)
      // Reminder: channel is created on the fly if needed in EMPs

      // Get recipient FederatedServiceAccount(s)
      if (toUserIds.size() > 1) {
        LOG.info("More than one recipient --> We are in MIM | recipients={}", toUserIds);
      }
      handleFromSymphonyIMorMIM(streamId, messageId, fromSymphonyUser, toUserIds, timestamp, message, disclaimer, attachments);
    }
  }

  private void handleFromSymphonyIMorMIM(String streamId, String messageId, IUser fromSymphonyUser, List<String> toUserIds, Long timestamp, String message, String disclaimer, List<IAttachment> attachments) {
    MultiValueMap<String, FederatedAccount> federatedAccountsByEmp = new LinkedMultiValueMap<>();
    List<IUser> symphonyUsers = new ArrayList<>();

    for (String symphonyId : toUserIds) {
      federatedAccountRepository.findBySymphonyId(symphonyId).ifPresentOrElse(federatedAccount -> federatedAccountsByEmp.add(federatedAccount.getEmp(), federatedAccount), () -> symphonyUsers.add(newIUser(symphonyId)));
    }

    if (federatedAccountsByEmp.isEmpty()) {
      // No federated account found
      LOG.warn("Unexpected handleFromSymphonyIMorMIM towards non-federated accounts, in-memory session to be removed | toUsers={}", toUserIds);
      messageMetrics.onMessageBlock(BlockingCause.NO_FEDERATED_ACCOUNT, fromSymphonyUser.getCompany());
      toUserIds.forEach(datafeedSessionPool::removeSessionInMemory);
      return;
    }

    try {
      for (Map.Entry<String, List<FederatedAccount>> entry : federatedAccountsByEmp.entrySet()) {
        List<FederatedAccount> toFederatedAccountsForEmp = entry.getValue();
        List<SymphonySession> userSessions = new ArrayList<>(toFederatedAccountsForEmp.size());

        for (FederatedAccount toFederatedAccount : toFederatedAccountsForEmp) {
          // TODO The process stops at first UnknownDatafeedUserException
          //  Some EMPs may have been called, others may have not
          //  Do we check first all sessions are ok?
          userSessions.add(datafeedSessionPool.refreshSession(toFederatedAccount.getSymphonyUserId()));
        }
        if (adminClient.getEntitlementAccess(fromSymphonyUser.getId().toString(), entry.getKey()).isEmpty()) {
          userSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, "You are not entitled to send messages to " + getEmp(entry.getKey()).getDisplayName() + " users."));
          messageMetrics.onMessageBlock(BlockingCause.NO_ENTITLEMENT_ACCESS, fromSymphonyUser.getCompany());
        } else if (toUserIds.size() > 1) {// Check there are only 2 users
          userSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, "You are not allowed to send a message to a " + getEmp(entry.getKey()).getDisplayName() + " contact in a MIM."));
          messageMetrics.onMessageBlock(BlockingCause.TOO_MUCH_MEMBERS, fromSymphonyUser.getCompany());
        } else if (attachments != null && !attachments.isEmpty()) {
          // If there are some attachments, warn the advisor and block the message
          userSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, "This message was not delivered. Attachments are not supported (messageId : " + messageId + ")."));
          messageMetrics.onMessageBlock(BlockingCause.ATTACHMENTS, fromSymphonyUser.getCompany());
        } else {

          // TODO Define the behavior in case of message not correctly sent to all EMPs
          //  need a recovery mechanism to re-send message in case of error only on some EMPs
          //
          // TODO Clarify the behavior when all MIM federated users have not joined the MIM
          //  Example: a WhatsApp user must join a WhatsGroup to discuss in the associated
          //  Proposition 1: block the chat (system message indicated that the chat is not possible until everyone has joined
          //  Proposition 2: allow chatting as soon as one federated has joined. In this case, what about the history of messages?
          messageMetrics.onSendMessage(fromSymphonyUser.getCompany(), streamId);
          empClient.sendMessage(entry.getKey(), streamId, messageId, fromSymphonyUser, entry.getValue(), timestamp, escape(message), escape(disclaimer));
        }
      }
    } catch (UnknownDatafeedUserException e) {
      // should never happen, as we have a FederatedAccount
      throw new IllegalStateException(e);
    }
  }

  private IUser newIUser(String symphonyId) {
    // TODO resolve firstName and lastName
    UserEntity.Builder builder = new UserEntity.Builder()
      .withId(Long.valueOf(symphonyId))
      .withFirstName(symphonyId + "_firstName")
      .withSurname(symphonyId + "_lastName");
    return new User(builder);
  }

  @NewSpan
  public RetrieveMessagesResponse retrieveMessages(List<MessageId> messageIds, String symphonyUserId) {
    try {
      List<MessageInfo> messageInfos = new ArrayList<>();

      SymphonySession userSession = datafeedSessionPool.refreshSession(symphonyUserId);
      for (MessageId id : messageIds) {
        MessageInfo message = symphonyService.getMessage(id.getMessageId(), userSession, podConfiguration.getUrl()).orElseThrow(RetrieveMessageFailedProblem::new);
        messageInfos.add(message);
      }

      return new RetrieveMessagesResponse().messages(messageInfos);
    } catch (UnknownDatafeedUserException e) {
      LOG.error("Session not found from symphony user {}", symphonyUserId);
      throw new MessageSenderNotFoundProblem();
    }
  }

  private EmpEntity getEmp(String emp) {
    // Should not happen: Return emp key if emp definition not found
    return empSchemaService.getEmpDefinition(emp).orElse(new EmpEntity().displayName(emp));
  }

  private enum BlockingCause {
    NOT_ENOUGH_MEMBER("not enough member"),
    NO_FEDERATED_ACCOUNT("no federated account"),
    NO_ENTITLEMENT_ACCESS("no entitlement access"),
    TOO_MUCH_MEMBERS("too much members"),
    ATTACHMENTS("attachments");

    private String blockingCause;

    BlockingCause(String blockingCause) {
      this.blockingCause = blockingCause;
    }
  }

  private class MessageMetrics {
    private final MeterManager meterManager;

    // to count number of conversations for which messages were sent in the last minute
    private Map<String, OffsetDateTime> lastSentMessagesDatesByStreamId;
    private Counter conversationsSendingMessages;

    public MessageMetrics(MeterManager meterManager) {
      this.meterManager = meterManager;

      this.conversationsSendingMessages = meterManager.register(Counter.builder("conversations.sending.messages"));
      this.lastSentMessagesDatesByStreamId = new ConcurrentHashMap<>();
    }

    public void onSendMessage(String companyName, String streamId) {
      meterManager.register(Counter.builder("messages.send.from.symphony").tag("company", companyName)).increment();

      // increment the counter and update the last received message date for the conversation
      CounterUtils.incrementOncePerMinute(lastSentMessagesDatesByStreamId, streamId, conversationsSendingMessages);
    }

    public void onMessageBlock(BlockingCause blockingCause, String companyName) {
      meterManager.register(Counter.builder("blocked.message.from.symphony").tag("cause", blockingCause.blockingCause).tag("company", companyName)).increment();
    }
  }
}
