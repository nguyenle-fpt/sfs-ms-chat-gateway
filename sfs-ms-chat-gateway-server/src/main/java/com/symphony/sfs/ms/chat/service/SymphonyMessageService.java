package com.symphony.sfs.ms.chat.service;

import com.google.common.annotations.VisibleForTesting;
import com.symphony.oss.models.chat.canon.IAttachment;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.admin.generated.model.CanChatResponse;
import com.symphony.sfs.ms.chat.datafeed.DatafeedListener;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.datafeed.GatewaySocialMessage;
import com.symphony.sfs.ms.chat.datafeed.ParentRelationshipType;
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
import com.symphony.sfs.ms.chat.generated.model.SymphonyAttachment;
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
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.zalando.problem.violations.Violation;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseFromSymphony.UNSUPPORTED_MESSAGE_CONTENTS;
import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseToSymphony.ADVISOR_NO_LONGER_AVAILABLE;
import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseToSymphony.FEDERATED_ACCOUNT_NOT_FOUND;
import static com.symphony.sfs.ms.starter.util.ProblemUtils.newConstraintViolation;

@Service
@Slf4j
@RequiredArgsConstructor
public class SymphonyMessageService implements DatafeedListener {

  // The max size of attachments in a single message we accept is 25 MB ~ 34 MB when the file is encoded in base64
  private final long MAX_UPLOAD_SIZE = 34 * 1024 * 1024;
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
  public void onIMMessage(GatewaySocialMessage gatewaySocialMessage) {
    if (!arePartiesValid(gatewaySocialMessage)) {
      return;
    }
    // Here we know this is a (M)IM from Symphony to EMP(s)
    // Reminder: channel is created on the fly if needed in EMPs
    if (gatewaySocialMessage.getToUserIds().size() > 1) {
      LOG.info("More than one recipient --> We are in MIM | recipients={}", gatewaySocialMessage.getToUserIds());
    }
    handleFromSymphonyIMorMIM(gatewaySocialMessage);
  }

  private boolean arePartiesValid(GatewaySocialMessage gatewaySocialMessage) {
    if (gatewaySocialMessage.getMembers().size() < 2) {
      messageMetrics.onMessageBlockFromSymphony(NOT_ENOUGH_MEMBER, gatewaySocialMessage.getStreamId());
      LOG.warn("(M)IM with has less than 2 members  | streamId={} messageId={}", gatewaySocialMessage.getStreamId(), gatewaySocialMessage.getMessageId());
      return false;
    }
    if (federatedAccountRepository.findBySymphonyId(gatewaySocialMessage.getFromUserId()).isPresent()) {
      // Message is from a Federated User
      return false;
    }
    // Build recipient FederatedServiceAccount(s)
    gatewaySocialMessage.setToUserIds(gatewaySocialMessage.getMembers().stream().filter(id -> !id.equals(gatewaySocialMessage.getFromUserId())).collect(Collectors.toList()));
    return true;
  }

  private void handleFromSymphonyIMorMIM(GatewaySocialMessage gatewaySocialMessage) {
    String streamId = gatewaySocialMessage.getStreamId();
    MultiValueMap<String, FederatedAccount> federatedAccountsByEmp = new LinkedMultiValueMap<>();
    List<IUser> symphonyUsers = new ArrayList<>();

    for (String symphonyId : gatewaySocialMessage.getToUserIds()) {
      federatedAccountRepository.findBySymphonyId(symphonyId).ifPresentOrElse(federatedAccount -> federatedAccountsByEmp.add(federatedAccount.getEmp(), federatedAccount), () -> symphonyUsers.add(SymphonyUserUtils.newIUser(symphonyId)));
    }

    if (federatedAccountsByEmp.isEmpty()) {
      // No federated account found
      LOG.warn("Unexpected handleFromSymphonyIMorMIM towards non-federated accounts, in-memory session to be removed | toUsers={}", gatewaySocialMessage.getToUserIds());
      messageMetrics.onMessageBlockFromSymphony(NO_FEDERATED_ACCOUNT, streamId);
      gatewaySocialMessage.getToUserIds().forEach(datafeedSessionPool::removeSessionInMemory);
      return;
    }

    try {
      // Check if message contents can be sent
      List<SymphonySession> allUserSessions = new ArrayList<>(gatewaySocialMessage.getToUserIds().size());
      for (FederatedAccount toFederatedAccount : federatedAccountsByEmp.values().stream().flatMap(Collection::stream).collect(Collectors.toList())) {
        allUserSessions.add(datafeedSessionPool.refreshSession(toFederatedAccount.getSymphonyUserId()));
      }
      if (gatewaySocialMessage.isChime()) {
        allUserSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, "Chimes are not supported currently, your contact was not notified."));
        messageMetrics.onMessageBlockFromSymphony(UNSUPPORTED_MESSAGE_CONTENTS, streamId);
        return;
      } else if (gatewaySocialMessage.isTable()) {
        allUserSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, "Your message was not sent. Sending tables is not supported currently."));
        messageMetrics.onMessageBlockFromSymphony(UNSUPPORTED_MESSAGE_CONTENTS, streamId);
        return;
      } else if (gatewaySocialMessage.getParentRelationshipType() == ParentRelationshipType.REPLY) {
        allUserSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, "Your message was not sent to your contact. Inline replies are not supported currently."));
        messageMetrics.onMessageBlockFromSymphony(UNSUPPORTED_MESSAGE_CONTENTS, streamId);
        return;
      }

      for (Map.Entry<String, List<FederatedAccount>> entry : federatedAccountsByEmp.entrySet()) {
        List<FederatedAccount> toFederatedAccountsForEmp = entry.getValue();
        String emp = entry.getKey();
        List<SymphonySession> userSessions = new ArrayList<>(toFederatedAccountsForEmp.size());

        for (FederatedAccount toFederatedAccount : toFederatedAccountsForEmp) {
          userSessions.add(datafeedSessionPool.getSession(toFederatedAccount.getSymphonyUserId()));
        }
        Optional<CanChatResponse> canChat = adminClient.canChat(gatewaySocialMessage.getFromUserId(), toFederatedAccountsForEmp.get(0).getFederatedUserId(), emp);
        if (canChat.isEmpty() || canChat.get() == CanChatResponse.NO_ENTITLEMENT) {
          userSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, "You are not entitled to send messages to " + empSchemaService.getEmpDisplayName(emp) + " users."));
          messageMetrics.onMessageBlockFromSymphony(NO_ENTITLEMENT_ACCESS, streamId);
        } else if (canChat.get() == CanChatResponse.NO_CONTACT) {
          userSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, "This message will not be delivered. You no longer have the entitlement for this."));
          messageMetrics.onMessageBlockFromSymphony(NO_CONTACT, streamId);
        } else if (gatewaySocialMessage.getToUserIds().size() > 1) {// Check there are only 2 users
          userSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, "You are not allowed to send a message to a " + empSchemaService.getEmpDisplayName(emp) + " contact in a MIM."));
          messageMetrics.onMessageBlockFromSymphony(TOO_MUCH_MEMBERS, streamId);
        } else {
          List<Attachment> attachmentsContent = null;
          long totalsize = 0;
          if (!CollectionUtils.isEmpty(gatewaySocialMessage.getAttachments())) {
            // retrieve the attachment
            try {
              attachmentsContent = new ArrayList<>();
              for (IAttachment attachment : gatewaySocialMessage.getAttachments()) {
                Attachment result = new Attachment()
                  .contentType(attachment.getContentType())
                  .fileName(attachment.getName())
                  .data(symphonyService.getAttachment(streamId, gatewaySocialMessage.getMessageId(), attachment.getFileId(), userSessions.get(0)));

                attachmentsContent.add(result);

                totalsize += result.getData().length(); // Technically the byte size might not match the length but we assume this is ASCII
                if (totalsize > MAX_UPLOAD_SIZE) {
                  userSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, errorMessageAttachmentsNotSent(gatewaySocialMessage.getTextContent().isEmpty(), gatewaySocialMessage.getMessageId())));
                  return;
                }
              }
            } catch (DataBufferLimitException dbe) {
              userSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, errorMessageAttachmentsNotSent(gatewaySocialMessage.getTextContent().isEmpty(), gatewaySocialMessage.getMessageId())));
              return;
            }
          }

          // TODO Define the behavior in case of message not correctly sent to all EMPs
          //  need a recovery mechanism to re-send message in case of error only on some EMPs
          //
          // TODO Clarify the behavior when all MIM federated users have not joined the MIM
          //  Example: a WhatsApp user must join a WhatsGroup to discuss in the associated
          //  Proposition 1: block the chat (system message indicated that the chat is not possible until everyone has joined
          //  Proposition 2: allow chatting as soon as one federated has joined. In this case, what about the history of messages?
          Optional<Channel> channel = channelService.retrieveChannel(gatewaySocialMessage.getFromUserId(), toFederatedAccountsForEmp.get(0).getFederatedUserId(), entry.getKey());
          if (channel.isEmpty()) {
            channelService.createIMChannel(streamId, gatewaySocialMessage.getFromUser(), toFederatedAccountsForEmp.get(0));
          }
          messageMetrics.onSendMessageFromSymphony(gatewaySocialMessage.getFromUser(), toFederatedAccountsForEmp, streamId);
          Optional<String> empMessageId = empClient.sendMessage(emp,
            streamId,
            gatewaySocialMessage.getMessageId(),
            gatewaySocialMessage.getFromUser(),
            toFederatedAccountsForEmp,
            gatewaySocialMessage.getTimestamp(),
            gatewaySocialMessage.getMessageForEmp(),
            gatewaySocialMessage.getDisclaimerForEmp(),
            attachmentsContent);
          if (empMessageId.isEmpty()) {
            userSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, "This message was not delivered (messageId : " + gatewaySocialMessage.getMessageId() + ")."));
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
  public String sendMessage(String streamId, String fromSymphonyUserId, FormattingEnum formatting, String text, List<SymphonyAttachment> attachments) {
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
      symphonyMessageId = forwardIncomingMessageToSymphony(streamId, fromSymphonyUserId, advisorSymphonyUserId.get(), formatting, text, attachments).orElseThrow(SendMessageFailedProblem::new);
    }

    return symphonyMessageId;
  }

  private Optional<String> forwardIncomingMessageToSymphony(String streamId, String fromSymphonyUserId, String toSymphonyUserId, FormattingEnum formatting, String text, List<SymphonyAttachment> attachments) {
    LOG.info("incoming message");
    if (StringUtils.isEmpty(text)) {
      text = " "; // this is the minimum message for symphony
    }
    String messageContent = "<messageML>" + text + "</messageML>";
    if (attachments != null && attachments.size() > 0) {
      return symphonyMessageSender.sendRawMessageWithAttachments(streamId, fromSymphonyUserId, messageContent, toSymphonyUserId, attachments);
    } else {
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

  /**
   * Build the error message when there are more 25 MB attachments
   *
   * @param textIsEmpty True if there are only attachments, false if there is some text in the message
   * @param messageId   Id of the symphony message
   * @return The error message
   */
  private String errorMessageAttachmentsNotSent(boolean textIsEmpty, String messageId) {
    // Prepare the error message for the advisor
    StringBuilder errorMessage = new StringBuilder();
    errorMessage.append("The full contents of your message could not be delivered. ");
    if (!textIsEmpty) {
      errorMessage.append("The text that you entered could not be delivered. ");
    }
    errorMessage
      .append("Attachment was not delivered; it exceeds 25MB limit (messageId : ")
      .append(messageId)
      .append(")");

    return errorMessage.toString();
  }
}
