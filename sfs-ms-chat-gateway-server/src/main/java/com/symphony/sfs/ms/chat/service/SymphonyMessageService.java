package com.symphony.sfs.ms.chat.service;

import com.google.common.annotations.VisibleForTesting;
import com.symphony.oss.models.chat.canon.IAttachment;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.admin.generated.model.CanChatResponse;
import com.symphony.sfs.ms.chat.datafeed.DatafeedListener;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.exception.UnknownDatafeedUserException;
import com.symphony.sfs.ms.chat.generated.model.CannotRetrieveStreamIdProblem;
import com.symphony.sfs.ms.chat.generated.model.FederatedAccountNotFoundProblem;
import com.symphony.sfs.ms.chat.generated.model.MessageId;
import com.symphony.sfs.ms.chat.generated.model.MessageInfo;
import com.symphony.sfs.ms.chat.generated.model.MessageSenderNotFoundProblem;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessageFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessagesResponse;
import com.symphony.sfs.ms.chat.generated.model.SendMessageFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.SendMessageRequest.FormattingEnum;
import com.symphony.sfs.ms.chat.model.Channel;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.external.AdminClient;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyService;
import com.symphony.sfs.ms.chat.util.SymphonyUserUtils;
import com.symphony.sfs.ms.emp.generated.model.Attachment;
import com.symphony.sfs.ms.emp.generated.model.SendSystemMessageRequest;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.security.StaticSessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.StreamInfo;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import com.symphony.sfs.ms.starter.symphony.user.UsersInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.UserInfo;
import org.apache.log4j.MDC;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.zalando.problem.violations.Violation;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseFromSymphony.NOT_ENOUGH_MEMBER;
import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseFromSymphony.NO_CONTACT;
import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseFromSymphony.NO_ENTITLEMENT_ACCESS;
import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseFromSymphony.NO_FEDERATED_ACCOUNT;
import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseFromSymphony.TOO_MUCH_MEMBERS;
import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseToSymphony.ADVISOR_NO_LONGER_AVAILABLE;
import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseToSymphony.FEDERATED_ACCOUNT_NOT_FOUND;
import static com.symphony.sfs.ms.starter.util.ProblemUtils.newConstraintViolation;
import static org.jsoup.nodes.Entities.escape;

@Service
@Slf4j
@RequiredArgsConstructor
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
  private final BotConfiguration botConfiguration;
  private final AuthenticationService authenticationService;
  private final UsersInfoService usersInfoService;
  private final StreamService streamService;
  private final MessageIOMonitor messageMetrics;
  private final ChannelService channelService;

  @PostConstruct
  @VisibleForTesting
  public void registerAsDatafeedListener() {
    forwarderQueueConsumer.registerDatafeedListener(this);
  }

  @Override
  @NewSpan
  public void onIMMessage(String streamId, String messageId, IUser fromSymphonyUser, List<String> members, Long timestamp, String message, String disclaimer, List<IAttachment> attachments) {

    if (members.size() < 2) {
      messageMetrics.onMessageBlockFromSymphony(NOT_ENOUGH_MEMBER, streamId);
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
      federatedAccountRepository.findBySymphonyId(symphonyId).ifPresentOrElse(federatedAccount -> federatedAccountsByEmp.add(federatedAccount.getEmp(), federatedAccount), () -> symphonyUsers.add(SymphonyUserUtils.newIUser(symphonyId)));
    }

    if (federatedAccountsByEmp.isEmpty()) {
      // No federated account found
      LOG.warn("Unexpected handleFromSymphonyIMorMIM towards non-federated accounts, in-memory session to be removed | toUsers={}", toUserIds);
      messageMetrics.onMessageBlockFromSymphony(NO_FEDERATED_ACCOUNT, streamId);
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
        Optional<CanChatResponse> canChat = adminClient.canChat(fromSymphonyUser.getId().toString(), toFederatedAccountsForEmp.get(0).getFederatedUserId(), entry.getKey());
        if (canChat.isEmpty() || canChat.get() == CanChatResponse.NO_ENTITLEMENT) {
          userSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, "You are not entitled to send messages to " + empSchemaService.getEmpDisplayName(entry.getKey()) + " users."));
          messageMetrics.onMessageBlockFromSymphony(NO_ENTITLEMENT_ACCESS, streamId);
        } else if (canChat.get() == CanChatResponse.NO_CONTACT) {
          userSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, "This message will not be delivered. You no longer have the entitlement for this."));
          messageMetrics.onMessageBlockFromSymphony(NO_CONTACT, streamId);
        } else if (toUserIds.size() > 1) {// Check there are only 2 users
          userSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, "You are not allowed to send a message to a " + empSchemaService.getEmpDisplayName(entry.getKey()) + " contact in a MIM."));
          messageMetrics.onMessageBlockFromSymphony(TOO_MUCH_MEMBERS, streamId);
        } else {
          List<Attachment> attachmentsContent = null;
          if (attachments != null && !attachments.isEmpty()) {
            // retrieve the attachment
            attachmentsContent = attachments.stream().map(a -> new Attachment()
              .contentType(a.getContentType())
              .fileName(a.getName())
              .data(symphonyService.getAttachment(streamId, messageId, a.getFileId(), userSessions.get(0)))
            ).collect(Collectors.toList());
          }

          // TODO Define the behavior in case of message not correctly sent to all EMPs
          //  need a recovery mechanism to re-send message in case of error only on some EMPs
          //
          // TODO Clarify the behavior when all MIM federated users have not joined the MIM
          //  Example: a WhatsApp user must join a WhatsGroup to discuss in the associated
          //  Proposition 1: block the chat (system message indicated that the chat is not possible until everyone has joined
          //  Proposition 2: allow chatting as soon as one federated has joined. In this case, what about the history of messages?
          Optional<Channel> channel = channelService.retrieveChannel(fromSymphonyUser.getId().toString(), toFederatedAccountsForEmp.get(0).getFederatedUserId(), entry.getKey());
          if (channel.isEmpty()) {
            channelService.createIMChannel(streamId, fromSymphonyUser, toFederatedAccountsForEmp.get(0));
          }
          messageMetrics.onSendMessageFromSymphony(fromSymphonyUser, entry.getValue(), streamId);
          Optional<String> empMessageId = empClient.sendMessage(entry.getKey(), streamId, messageId, fromSymphonyUser, entry.getValue(), timestamp, escape(message), escape(disclaimer), attachmentsContent);
          if (empMessageId.isEmpty()) {
            userSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, "This message was not delivered (messageId : " + messageId + ")."));
          }
        }
      }
    } catch (UnknownDatafeedUserException e) {
      // should never happen, as we have a FederatedAccount
      throw new IllegalStateException(e);
    }
  }

  @NewSpan
  public RetrieveMessagesResponse retrieveMessages(List<MessageId> messageIds, String symphonyUserId) {
    try {
      List<MessageInfo> messageInfos = new ArrayList<>();

      SymphonySession userSession = datafeedSessionPool.refreshSession(symphonyUserId);
      for (MessageId id : messageIds) {
        MessageInfo message = symphonyService.getMessage(id.getMessageId(), userSession).orElseThrow(RetrieveMessageFailedProblem::new);
        messageInfos.add(message);
      }

      return new RetrieveMessagesResponse().messages(messageInfos);
    } catch (UnknownDatafeedUserException e) {
      LOG.error("Session not found from symphony user {}", symphonyUserId);
      throw new MessageSenderNotFoundProblem();
    }
  }

  @NewSpan
  public String sendMessage(String streamId, String fromSymphonyUserId, FormattingEnum formatting, String text) {
    MDC.put("streamId", streamId);
    FederatedAccount federatedAccount = federatedAccountRepository.findBySymphonyId(fromSymphonyUserId).orElseThrow(() -> {
      messageMetrics.onMessageBlockToSymphony(FEDERATED_ACCOUNT_NOT_FOUND, streamId);
      return new FederatedAccountNotFoundProblem();
    });
    MDC.put("federatedUserId", federatedAccount.getFederatedUserId());

    Optional<String> advisorSymphonyUserId = findAdvisor(streamId, fromSymphonyUserId);
    Optional<String> notEntitled = notEntitledMessage(advisorSymphonyUserId, federatedAccount.getFederatedUserId(), federatedAccount.getEmp(), formatting);
    advisorSymphonyUserId.ifPresent(s -> MDC.put("advisor", s));

    String symphonyMessageId = null;
    if (notEntitled.isPresent()) {
      messageMetrics.onMessageBlockToSymphony(ADVISOR_NO_LONGER_AVAILABLE, streamId);
      blockIncomingMessage(federatedAccount.getEmp(), streamId, notEntitled.get());
    } else {
      // TODO fix this bad management of optional
      messageMetrics.onSendMessageToSymphony(fromSymphonyUserId, advisorSymphonyUserId.get(), streamId);
      symphonyMessageId = forwardIncomingMessageToSymphony(streamId, fromSymphonyUserId, advisorSymphonyUserId.get(), formatting, text).orElseThrow(SendMessageFailedProblem::new);
    }

    return symphonyMessageId;
  }

  private Optional<String> forwardIncomingMessageToSymphony(String streamId, String fromSymphonyUserId, String toSymphonyUserId, FormattingEnum formatting, String text) {
    LOG.info("incoming message");
    String messageContent = "<messageML>" + text + "</messageML>";
    if (formatting == null) {
      return symphonyMessageSender.sendRawMessage(streamId, fromSymphonyUserId, messageContent, toSymphonyUserId);
    }

    messageContent = text;
    switch (formatting) {
      case SIMPLE:
        return symphonyMessageSender.sendSimpleMessage(streamId, fromSymphonyUserId, messageContent, toSymphonyUserId);
      case NOTIFICATION:
        return symphonyMessageSender.sendNotificationMessage(streamId, fromSymphonyUserId, messageContent, toSymphonyUserId);
      case INFO:
        return symphonyMessageSender.sendInfoMessage(streamId, fromSymphonyUserId, messageContent, toSymphonyUserId);
      case ALERT:
        return symphonyMessageSender.sendAlertMessage(streamId, fromSymphonyUserId, messageContent, toSymphonyUserId);
      default:
        throw newConstraintViolation(new Violation("formatting", "invalid type, must be one of " + Arrays.toString(FormattingEnum.values())));
    }
  }

  private void blockIncomingMessage(String emp, String streamId, String reasonText) {
    LOG.info("block incoming message");
    empClient.sendSystemMessage(emp, streamId, new Date().getTime(), reasonText, SendSystemMessageRequest.TypeEnum.ALERT);
  }

  /**
   * Pre-requisite: the stream is an IM between an advisor and an emp user
   *
   * @param streamId
   * @param fromFederatedAccountSymphonyUserId
   * @return Optional with found advisor symphonyUserId. Empty if no advisor found
   */
  private Optional<String> findAdvisor(String streamId, String fromFederatedAccountSymphonyUserId) {

    SymphonySession botSession = authenticationService.authenticate(
      podConfiguration.getSessionAuth(),
      podConfiguration.getKeyAuth(),
      botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());

    StreamInfo streamInfo = streamService.getStreamInfo(podConfiguration.getUrl(), new StaticSessionSupplier<>(botSession), streamId).orElseThrow(CannotRetrieveStreamIdProblem::new);

    List<Long> streamMemberIds = streamInfo.getStreamAttributes().getMembers();
    if (streamMemberIds.size() != 2) {
      LOG.warn("Unsupported stream {} with {} members (symphonyUserIds = {})", streamId, streamMemberIds.size(), streamMemberIds);
      return Optional.empty();
    } else {

      // Nominal case of IM: We have a stream with exactly 2 members
      // - 1 user must an advisor
      // - 1 user must be a emp user
      return streamInfo.getStreamAttributes().getMembers()
        .parallelStream()
        .map(String::valueOf)
        .filter(symphonyId -> !symphonyId.equals(fromFederatedAccountSymphonyUserId)) // Remove federatedAccountUser
        .findFirst();
    }
  }

  /**
   * @param advisorSymphonyUserId
   * @param emp
   * @return If present, message indicating that advisor is NOT entitled.
   */
  private Optional<String> notEntitledMessage(Optional<String> advisorSymphonyUserId, String federatedUserId, String emp, FormattingEnum formatting) {
    final String CONTACT_NOT_AVAILABLE = "Sorry, your contact is no longer available";
    final String CONTACT_WITH_DETAILS_NOT_AVAILABLE = "Sorry, your contact %s is no longer available";

    // No advisor at all
    if (advisorSymphonyUserId.isEmpty()) {
      return Optional.of(CONTACT_NOT_AVAILABLE);
    }

    // Advisor entitled
    Optional<CanChatResponse> canChatResponse = adminClient.canChat(advisorSymphonyUserId.get(), federatedUserId, emp);
    // We can still send notifications to advisors without contacts. Useful for onboarding when we need to send a notification before the contact is created
    // TODO REFACTOR, adding a message type and checking on that might make more sense than checking the formatting to change behaviour
    if (canChatResponse.isPresent() && canChatResponse.get() == CanChatResponse.CAN_CHAT ||
      canChatResponse.isPresent() && canChatResponse.get() == CanChatResponse.NO_CONTACT && formatting != null) {
      return Optional.empty();
    }

    // From here advisor is not entitled
    // Get advisor details
    SymphonySession botSession = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());
    final List<UserInfo> usersFromIds = usersInfoService.getUsersFromIds(podConfiguration.getUrl(), new StaticSessionSupplier<>(botSession), Collections.singletonList(advisorSymphonyUserId.get()));

    String message;
    if (usersFromIds.isEmpty()) {
      LOG.warn("Advisor with symphonyUserId {} not found on Symphony side", advisorSymphonyUserId.get());
      message = CONTACT_NOT_AVAILABLE;
    } else {
      // Message with advisor details
      message = String.format(CONTACT_WITH_DETAILS_NOT_AVAILABLE, usersFromIds.get(0).getDisplayName());
    }

    return Optional.ofNullable(message);
  }
}
