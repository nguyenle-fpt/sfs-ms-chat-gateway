package com.symphony.sfs.ms.chat.service;

import com.google.common.annotations.VisibleForTesting;
import com.symphony.oss.models.chat.canon.IAttachment;
import com.symphony.oss.models.chat.canon.facade.IUser;
import com.symphony.sfs.ms.admin.generated.model.CanChatResponse;
import com.symphony.sfs.ms.chat.datafeed.CustomEntity;
import com.symphony.sfs.ms.chat.datafeed.DatafeedListener;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.datafeed.GatewaySocialMessage;
import com.symphony.sfs.ms.chat.datafeed.ParentRelationshipType;
import com.symphony.sfs.ms.chat.exception.UnknownDatafeedUserException;
import com.symphony.sfs.ms.chat.generated.model.CannotRetrieveStreamIdProblem;
import com.symphony.sfs.ms.chat.generated.model.FormattingEnum;
import com.symphony.sfs.ms.chat.generated.model.MessageId;
import com.symphony.sfs.ms.chat.generated.model.MessageInfo;
import com.symphony.sfs.ms.chat.generated.model.MessageSenderNotFoundProblem;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessageFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessagesResponse;
import com.symphony.sfs.ms.chat.generated.model.SendMessageFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.SymphonyAttachment;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.external.AdminClient;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyService;
import com.symphony.sfs.ms.chat.util.SpecialCharactersUtils;
import com.symphony.sfs.ms.chat.util.SymphonyUserUtils;
import com.symphony.sfs.ms.emp.generated.model.Attachment;
import com.symphony.sfs.ms.emp.generated.model.EmpError;
import com.symphony.sfs.ms.emp.generated.model.OperationIdBySymId;
import com.symphony.sfs.ms.emp.generated.model.SendMessageResponse;
import com.symphony.sfs.ms.emp.generated.model.SendSystemMessageRequest;
import com.symphony.sfs.ms.emp.generated.model.SendSystemMessageRequest.TypeEnum;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.security.StaticSessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.AuthenticationService;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.stream.StreamInfo;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import com.symphony.sfs.ms.starter.symphony.stream.StreamTypes;
import com.symphony.sfs.ms.starter.symphony.user.UsersInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.log4j.MDC;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.context.MessageSource;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseFromSymphony.NOT_ENOUGH_MEMBER;
import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseFromSymphony.NO_CONTACT;
import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseFromSymphony.NO_ENTITLEMENT_ACCESS;
import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseFromSymphony.NO_FEDERATED_ACCOUNT;
import static com.symphony.sfs.ms.chat.service.MessageIOMonitor.BlockingCauseFromSymphony.TOO_MANY_MEMBERS;
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
  private static final int MAX_TEXT_LENGTH = 30000;
  private static final String TEXT_TOO_LONG_WARNING = "The message was too long and was truncated. Only the first %,d characters were delivered";

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
  private final MessageSource messageSource;


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
    if (botConfiguration.getSymphonyId().equals(gatewaySocialMessage.getFromUserId())) {
      return false;
    }
    if (federatedAccountRepository.findBySymphonyId(gatewaySocialMessage.getFromUserId()).isPresent() && !gatewaySocialMessage.isRoom()) {
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
      //TODO in case of a room allUserSessions will have only the bot session, in case if IM it has only one federated account's session.
      //TODO maybe the code should be simplified accordingly
      List<SymphonySession> allUserSessions;
      if (gatewaySocialMessage.isRoom()) {
        //TODO caching for botSession ?
        SymphonySession botSession = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());
        allUserSessions = Collections.singletonList(botSession);
      } else {
        allUserSessions = new ArrayList<>(gatewaySocialMessage.getToUserIds().size());
        for (FederatedAccount toFederatedAccount : federatedAccountsByEmp.values().stream().flatMap(Collection::stream).collect(Collectors.toList())) {
          allUserSessions.add(datafeedSessionPool.refreshSession(toFederatedAccount.getSymphonyUserId()));
        }
      }

      // Check if message contents can be sent
      if (gatewaySocialMessage.isChime()) {
        allUserSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, messageSource.getMessage("chimes.not.supported", null, Locale.getDefault()), Collections.emptyList()));
        messageMetrics.onMessageBlockFromSymphony(UNSUPPORTED_MESSAGE_CONTENTS, streamId);
        return;
      } else if (gatewaySocialMessage.isTable()) {
        allUserSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, messageSource.getMessage("tables.not.supported", null, Locale.getDefault()), Collections.emptyList()));
        messageMetrics.onMessageBlockFromSymphony(UNSUPPORTED_MESSAGE_CONTENTS, streamId);
        return;
      } else if (gatewaySocialMessage.getParentRelationshipType() == ParentRelationshipType.REPLY ||
        gatewaySocialMessage.containsCustomEntityType(CustomEntity.QUOTE_TYPE)) {
        allUserSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, messageSource.getMessage("inline.replies.not.supported", null, Locale.getDefault()), Collections.emptyList()));
        messageMetrics.onMessageBlockFromSymphony(UNSUPPORTED_MESSAGE_CONTENTS, streamId);
        return;
      }

      // Forward the message to all EMP users
      for (Map.Entry<String, List<FederatedAccount>> entry : federatedAccountsByEmp.entrySet()) {
        List<FederatedAccount> toFederatedAccountsForEmp = entry.getValue();
        String emp = entry.getKey();
        List<SymphonySession> userSessions = new ArrayList<>(toFederatedAccountsForEmp.size());

        for (FederatedAccount toFederatedAccount : toFederatedAccountsForEmp) {
          userSessions.add(datafeedSessionPool.getSession(toFederatedAccount.getSymphonyUserId()));
        }

        // CES-3203 Prevent messages to be sent in MIMs
        if (!gatewaySocialMessage.isRoom() && gatewaySocialMessage.getToUserIds().size() > 1) {
          String alertMessage = messageSource.getMessage("chat.mim.not.supported", new Object[]{empSchemaService.getEmpDisplayName(emp)}, Locale.getDefault());
          userSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, alertMessage, Collections.emptyList()));
          messageMetrics.onMessageBlockFromSymphony(TOO_MANY_MEMBERS, streamId);
          return;
        }

        // not optimal, we call the canChat everytime here.
        Optional<CanChatResponse> canChat = adminClient.canChat(gatewaySocialMessage.getFromUserId(), toFederatedAccountsForEmp.get(0).getFederatedUserId(), emp);

        if (manageCanChat(gatewaySocialMessage, emp, streamId, userSessions, canChat)) {
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
                  .data(symphonyService.getAttachment(streamId, gatewaySocialMessage.getMessageId(), attachment.getFileId(), allUserSessions.get(0)));

                attachmentsContent.add(result);

                totalsize += result.getData().length(); // Technically the byte size might not match the length but we assume this is ASCII
                if (totalsize > MAX_UPLOAD_SIZE) {
                  String alertMessage = messageSource.getMessage("message.partially.sent", new Object[]{gatewaySocialMessage.getMessageId()}, Locale.getDefault());
                  allUserSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, alertMessage, Collections.emptyList()));
                  return;
                }
              }
            } catch (DataBufferLimitException dbe) {
              String alertMessage = messageSource.getMessage("message.partially.sent", new Object[]{gatewaySocialMessage.getMessageId()}, Locale.getDefault());
              allUserSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, alertMessage, Collections.emptyList()));
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

          // We will not create new channels if you have the permission CAN_CHAT_NO_CREATE_IM
          // this means that old conversations should still work but the user won't be able to create a new one
          // we don't know if the conversation already exists so we cannot send a specific message.
          // we let the normal mechanism take care of that.
          if (!gatewaySocialMessage.isRoom() && canChat.isPresent() && canChat.get() != CanChatResponse.CAN_CHAT_NO_CREATE_IM) {
            channelService.createIMChannel(streamId, gatewaySocialMessage.getFromUser(), toFederatedAccountsForEmp.get(0));
          }
          messageMetrics.onSendMessageFromSymphony(gatewaySocialMessage.getFromUser(), toFederatedAccountsForEmp, streamId);

          Optional<SendMessageResponse> sendMessageResponse = empClient.sendMessage(emp,
            streamId,
            gatewaySocialMessage.getMessageId(),
            gatewaySocialMessage.getFromUser(),
            toFederatedAccountsForEmp,
            gatewaySocialMessage.getTimestamp(),
            gatewaySocialMessage.getMessageForEmp(),
            gatewaySocialMessage.getDisclaimerForEmp(),
            attachmentsContent);
          if (sendMessageResponse.isEmpty()) {
            allUserSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, messageSource.getMessage("message.not.delivered", new Object[]{gatewaySocialMessage.getMessageId()}, Locale.getDefault()), Collections.emptyList()));
          } else {
            this.dealWithMessageSentPartially(gatewaySocialMessage, sendMessageResponse.get(), allUserSessions);
          }
        }
      }
    } catch (UnknownDatafeedUserException e) {
      // should never happen, as we have a FederatedAccount
      throw new IllegalStateException(e);
    }
  }

  private void dealWithMessageSentPartially(GatewaySocialMessage gatewaySocialMessage, SendMessageResponse sendMessageResponse, List<SymphonySession> allUserSessions) {
    List<OperationIdBySymId> empMessageIds = sendMessageResponse.getOperationIds();
    List<OperationIdBySymId> notDeliveredFor = empMessageIds
      .stream()
      .filter(op -> op.getOperationId() == null)
      .collect(Collectors.toList());

    if (notDeliveredFor.isEmpty()) {
      return;
    }
    String errorMessage;
    if (!gatewaySocialMessage.isRoom()) {
      errorMessage = messageSource.getMessage("message.not.delivered", new Object[]{gatewaySocialMessage.getMessageId()}, Locale.getDefault());
    } else {
      String users = notDeliveredFor.stream()
        .map(op -> this.federatedAccountRepository.findBySymphonyId(op.getSymphonyId()))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(this::federatedAccountDisplayName)
        .collect(Collectors.joining(", "));
      errorMessage = messageSource.getMessage("message.not.received", new Object[]{gatewaySocialMessage.getMessageId(), users}, Locale.getDefault());
    }

    // CES-3955
    // Build a list of stringified errors
    List<String> formattedErrors = new ArrayList<>();
    List<EmpError> errors = sendMessageResponse.getErrors();
    if (org.apache.commons.collections.CollectionUtils.isNotEmpty(errors)) {

      formattedErrors.addAll(errors.stream().map(EmpError::getDetail).collect(Collectors.toList()));
      LOG.info("SymphonyMessageService.dealWithMessageSentPartially | formattedErrors={}", formattedErrors);
    }

    allUserSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, gatewaySocialMessage.getStreamId(), errorMessage, formattedErrors.isEmpty() ? null : formattedErrors));
  }

  private String federatedAccountDisplayName(FederatedAccount federatedAccount) {
    return federatedAccount.getFirstName() + " " + federatedAccount.getLastName();
  }

  private boolean manageCanChat(GatewaySocialMessage gatewaySocialMessage, String emp, String streamId, List<SymphonySession> userSessions, Optional<CanChatResponse> canChat) {
    if (gatewaySocialMessage.isRoom()) {
      return true;
    }

    if (canChat.isEmpty() || canChat.get() == CanChatResponse.NO_ENTITLEMENT) {
      userSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, messageSource.getMessage("cannot.chat.not.entitled", new Object[]{empSchemaService.getEmpDisplayName(emp)}, Locale.getDefault()), Collections.emptyList()));
      messageMetrics.onMessageBlockFromSymphony(NO_ENTITLEMENT_ACCESS, streamId);
      return false;
    } else if (canChat.get() == CanChatResponse.NO_CONTACT) {
      userSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, messageSource.getMessage("cannot.chat.no.contact", null, Locale.getDefault()), Collections.emptyList()));
      messageMetrics.onMessageBlockFromSymphony(NO_CONTACT, streamId);
      return false;
    }
    // CAN_CHAT_NO_CREATE_IM is seen as a canchat for this function
    return true;
  }

  @NewSpan
  public RetrieveMessagesResponse retrieveMessages(List<MessageId> messageIds, String symphonyUserId) {
    try {
      List<MessageInfo> messageInfos = new ArrayList<>();

      SymphonySession userSession = datafeedSessionPool.refreshSession(symphonyUserId);
      for (MessageId id : messageIds) {
        MessageInfo message = symphonyService.getMessage(id.getMessageId(), userSession).orElseThrow(RetrieveMessageFailedProblem::new);
        messageInfos.add(message.message(SpecialCharactersUtils.unescapeSpecialCharacters(message.getMessage())));
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
      return new MessageSenderNotFoundProblem();
    });
    MDC.put("federatedUserId", federatedAccount.getFederatedUserId());
    String symphonyMessageId = null;
    StreamInfo streamInfo = getStreamInfo(streamId);
    Optional<String> notEntitled = Optional.empty();
    if (streamInfo.getStreamType().getType() != StreamTypes.ROOM) {
      Optional<String> advisorSymphonyUserId = findAdvisor(streamInfo, streamId, fromSymphonyUserId);
      notEntitled = notEntitledMessage(advisorSymphonyUserId, federatedAccount.getFederatedUserId(), federatedAccount.getEmp(), formatting);
      advisorSymphonyUserId.ifPresent(s -> MDC.put("advisor", s));
    }

    try {
      if (notEntitled.isPresent()) {
        messageMetrics.onMessageBlockToSymphony(ADVISOR_NO_LONGER_AVAILABLE, streamId);
        feedbackAboutIncomingMessage(federatedAccount.getEmp(), streamId, fromSymphonyUserId, notEntitled.get(), TypeEnum.ALERT);
      } else {
        // TODO fix this bad management of optional
        messageMetrics.onSendMessageToSymphony(fromSymphonyUserId, streamId);
        boolean textTooLong = (text.length() > MAX_TEXT_LENGTH);
        symphonyMessageId = forwardIncomingMessageToSymphony(streamId, fromSymphonyUserId, null, formatting, text, attachments, textTooLong).orElseThrow(SendMessageFailedProblem::new);
        // In the case the message was sent truncated, send an alert to the Symphony and Federated users (CES-1912)
        if (textTooLong) {
          String alertMessage = String.format(TEXT_TOO_LONG_WARNING, MAX_TEXT_LENGTH);
          feedbackAboutIncomingMessage(federatedAccount.getEmp(), streamId, fromSymphonyUserId, alertMessage, TypeEnum.ALERT);

          boolean isRoom = streamInfo.getStreamType().getType() == StreamTypes.ROOM;

          SymphonySession session;
          if (isRoom) {
            session = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());
          } else {
            session = datafeedSessionPool.refreshSession(fromSymphonyUserId);
          }
          symphonyMessageSender.sendAlertMessage(session, streamId, alertMessage, Collections.emptyList());
        }
      }
    } catch (UnknownDatafeedUserException e) {
      // should never happen, as we have a FederatedAccount
      throw new IllegalStateException(e);
    }

    return symphonyMessageId;
  }

  @NewSpan
  public String sendSystemMessage(String streamId, FormattingEnum formatting, String text, String title, String fromSymphonyUserId) {
    boolean isRoom = getStreamInfo(streamId).getStreamType().getType() == StreamTypes.ROOM;

    try {
      SymphonySession session;
      if (isRoom) {
        session = authenticationService.authenticate(podConfiguration.getSessionAuth(), podConfiguration.getKeyAuth(), botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());
      } else {
        session = datafeedSessionPool.refreshSession(fromSymphonyUserId);
      }

      Optional<String> symphonyMessageId;
      if (formatting == null) {
        return symphonyMessageSender.sendRawMessage(session, streamId, "<messageML>" + text + "</messageML>").orElseThrow(SendMessageFailedProblem::new);
      }

      switch (formatting) {
        case SIMPLE:
          symphonyMessageId = symphonyMessageSender.sendSimpleMessage(session, streamId, text);
          break;
        case NOTIFICATION:
          symphonyMessageId = symphonyMessageSender.sendNotificationMessage(session, streamId, text);
          break;
        case INFO:
          symphonyMessageId = symphonyMessageSender.sendInfoMessage(session, streamId, text);
          break;
        case ALERT:
          List<String> errors = Collections.emptyList();
          symphonyMessageId = symphonyMessageSender.sendAlertMessage(session, streamId, text, title, errors);
          break;
        default:
          throw newConstraintViolation(new Violation("formatting", "invalid type, must be one of " + Arrays.toString(FormattingEnum.values())));
      }

      return symphonyMessageId.orElseThrow(SendMessageFailedProblem::new);
    } catch (UnknownDatafeedUserException e) {
      // should never happen, as we have a FederatedAccount
      throw new IllegalStateException(e);
    }
  }

  private Optional<String> forwardIncomingMessageToSymphony(String streamId, String fromSymphonyUserId, String toSymphonyUserId, FormattingEnum formatting, String text, List<SymphonyAttachment> attachments, boolean truncate) {
    LOG.info("incoming message");
    if (StringUtils.isEmpty(text)) {
      text = " "; // this is the minimum message for symphony
    } else if (truncate) {
      text = text.substring(0, MAX_TEXT_LENGTH);
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

  private void feedbackAboutIncomingMessage(String emp, String streamId, String symphonyId, String reasonText, SendSystemMessageRequest.TypeEnum feedbackType) {
    LOG.info("FeedbackAboutIncomingMessage | emp={}, streamId={}, symphonyId={}, reason={}, type={}", emp, streamId, symphonyId, reasonText, feedbackType);
    empClient.sendSystemMessage(emp, streamId, symphonyId, new Date().getTime(), reasonText, feedbackType);
  }

  private StreamInfo getStreamInfo(String streamId) {

    SymphonySession botSession = authenticationService.authenticate(
      podConfiguration.getSessionAuth(),
      podConfiguration.getKeyAuth(),
      botConfiguration.getUsername(), botConfiguration.getPrivateKey().getData());

    return streamService.getStreamInfo(podConfiguration.getUrl(), new StaticSessionSupplier<>(botSession), streamId).orElseThrow(CannotRetrieveStreamIdProblem::new);
  }

  /**
   * Pre-requisite: the stream is an IM between an advisor and an emp user
   *
   * @param streamId
   * @param fromFederatedAccountSymphonyUserId
   * @return Optional with found advisor symphonyUserId. Empty if no advisor found
   */
  private Optional<String> findAdvisor(StreamInfo streamInfo, String streamId, String fromFederatedAccountSymphonyUserId) {
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
    // No advisor at all
    if (advisorSymphonyUserId.isEmpty()) {
      return Optional.of(messageSource.getMessage("contact.not.available", null, Locale.getDefault()));
    }

    // Advisor entitled
    Optional<CanChatResponse> canChatResponse = adminClient.canChat(advisorSymphonyUserId.get(), federatedUserId, emp);
    // We can still send notifications to advisors without contacts. Useful for onboarding when we need to send a notification before the contact is created
    // TODO REFACTOR, adding a message type and checking on that might make more sense than checking the formatting to change behaviour
    if (canChatResponse.isPresent() && canChatResponse.get() == CanChatResponse.CAN_CHAT ||
      canChatResponse.isPresent() && canChatResponse.get() == CanChatResponse.CAN_CHAT_NO_CREATE_IM ||
      canChatResponse.isPresent() && canChatResponse.get() == CanChatResponse.NO_CONTACT && formatting != null) {
      return Optional.empty();
    }

    String message = messageSource.getMessage("contact.not.available", null, Locale.getDefault());
    return Optional.of(message);
  }
}
