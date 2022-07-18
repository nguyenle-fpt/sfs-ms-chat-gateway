package com.symphony.sfs.ms.chat.service;

import com.google.common.annotations.VisibleForTesting;
import com.symphony.oss.models.chat.canon.IAttachment;
import com.symphony.oss.models.chat.canon.IAttachmentEntity;
import com.symphony.sfs.ms.admin.generated.model.BlockedFileTypes;
import com.symphony.sfs.ms.admin.generated.model.CanChatResponse;
import com.symphony.sfs.ms.admin.generated.model.EmpEntity;
import com.symphony.sfs.ms.admin.generated.model.EmpSchema;
import com.symphony.sfs.ms.chat.config.EmpConfig;
import com.symphony.sfs.ms.chat.datafeed.DatafeedListener;
import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.datafeed.ForwarderQueueConsumer;
import com.symphony.sfs.ms.chat.datafeed.GatewaySocialMessage;
import com.symphony.sfs.ms.chat.datafeed.MessageDecryptor;
import com.symphony.sfs.ms.chat.exception.DecryptionException;
import com.symphony.sfs.ms.chat.exception.UnknownDatafeedUserException;
import com.symphony.sfs.ms.chat.generated.model.AttachmentBlocked;
import com.symphony.sfs.ms.chat.generated.model.AttachmentBlockedProblem;
import com.symphony.sfs.ms.chat.generated.model.CannotRetrieveStreamIdProblem;
import com.symphony.sfs.ms.chat.generated.model.EmpNotFoundProblem;
import com.symphony.sfs.ms.chat.generated.model.FormattingEnum;
import com.symphony.sfs.ms.chat.generated.model.MessageId;
import com.symphony.sfs.ms.chat.generated.model.MessageInfo;
import com.symphony.sfs.ms.chat.generated.model.MessageInfoWithCustomEntities;
import com.symphony.sfs.ms.chat.generated.model.MessageSenderNotFoundProblem;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessageFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.RetrieveMessagesResponse;
import com.symphony.sfs.ms.chat.generated.model.SendMessageFailedProblem;
import com.symphony.sfs.ms.chat.generated.model.SetMessagesAsReadRequest;
import com.symphony.sfs.ms.chat.generated.model.SymphonyAttachment;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.chat.service.external.AdminClient;
import com.symphony.sfs.ms.chat.service.external.EmpClient;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyService;
import com.symphony.sfs.ms.emp.generated.model.Attachment;
import com.symphony.sfs.ms.emp.generated.model.ChannelMember;
import com.symphony.sfs.ms.emp.generated.model.EmpError;
import com.symphony.sfs.ms.emp.generated.model.OperationIdBySymId;
import com.symphony.sfs.ms.emp.generated.model.SendMessageResponse;
import com.symphony.sfs.ms.emp.generated.model.SendSystemMessageRequest;
import com.symphony.sfs.ms.emp.generated.model.SendSystemMessageRequest.TypeEnum;
import com.symphony.sfs.ms.emp.generated.model.SendmessagerequestInlineMessage;
import com.symphony.sfs.ms.starter.config.properties.BotConfiguration;
import com.symphony.sfs.ms.starter.config.properties.PodConfiguration;
import com.symphony.sfs.ms.starter.security.SessionSupplier;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import com.symphony.sfs.ms.starter.symphony.message.MessageStatusService;
import com.symphony.sfs.ms.starter.symphony.message.SendMessageStatusRequest;
import com.symphony.sfs.ms.starter.symphony.stream.CustomEntity;
import com.symphony.sfs.ms.starter.symphony.stream.MessageEnvelope;
import com.symphony.sfs.ms.starter.symphony.stream.SBEEventMessage;
import com.symphony.sfs.ms.starter.symphony.stream.StreamInfo;
import com.symphony.sfs.ms.starter.symphony.stream.StreamService;
import com.symphony.sfs.ms.starter.symphony.stream.StreamTypes;
import com.symphony.sfs.ms.starter.symphony.stream.ThreadMessagesResponse;
import com.symphony.sfs.ms.starter.symphony.tds.TenantDetailEntity;
import com.symphony.sfs.ms.starter.symphony.tds.TenantDetailRepository;
import com.symphony.sfs.ms.starter.util.StreamUtil;
import com.symphony.sfs.ms.starter.util.UserIdUtils;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
  private static final int POD_BATCH_REQUEST_SIZE = 500;
  private static final String TEXT_TOO_LONG_WARNING = "The message was too long and was truncated. Only the first %,d characters were delivered";

  private final EmpConfig empConfig;
  private final EmpClient empClient;
  private final TenantDetailRepository tenantDetailRepository;
  private final FederatedAccountRepository federatedAccountRepository;
  private final ForwarderQueueConsumer forwarderQueueConsumer;
  private final DatafeedSessionPool datafeedSessionPool;
  private final SymphonyMessageSender symphonyMessageSender;
  private final AdminClient adminClient;
  private final EmpSchemaService empSchemaService;
  private final SymphonyService symphonyService;
  private final MessageStatusService messageStatusService;
  private final PodConfiguration podConfiguration;
  private final BotConfiguration botConfiguration;
  private final StreamService streamService;
  private final MessageIOMonitor messageMetrics;
  private final MessageSource messageSource;
  private final MessageDecryptor messageDecryptor;


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
    List<FederatedAccount> federatedAccounts = new ArrayList<>();

    for (String symphonyId : gatewaySocialMessage.getToUserIds()) {
      federatedAccountRepository.findBySymphonyId(symphonyId).ifPresent(
        federatedAccount -> {
          federatedAccountsByEmp.add(federatedAccount.getEmp(), federatedAccount);
          federatedAccounts.add(federatedAccount);
        });
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
      List<SessionSupplier<SymphonySession>> allUserSessions;
      if (gatewaySocialMessage.isRoom()) {
        allUserSessions = Collections.singletonList(datafeedSessionPool.getBotSessionSupplier());
      } else {
        allUserSessions = new ArrayList<>(gatewaySocialMessage.getToUserIds().size());
        for (FederatedAccount toFederatedAccount : federatedAccountsByEmp.values().stream().flatMap(Collection::stream).collect(Collectors.toList())) {
          allUserSessions.add(datafeedSessionPool.getSessionSupplier(toFederatedAccount));
        }
      }
      SendmessagerequestInlineMessage inlineMessageRequest = null;

      if (gatewaySocialMessage.containsCustomEntityType(CustomEntity.QUOTE_TYPE)) {
        inlineMessageRequest = getInlineQuote(gatewaySocialMessage, streamId, federatedAccounts, allUserSessions);
        if (inlineMessageRequest != null && botConfiguration.getSymphonyId().equals(inlineMessageRequest.getFromMember().getSymphonyId())) {
          allUserSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, messageSource.getMessage("cannot.reply.to.message.type", null, Locale.getDefault()), Collections.emptyList()));
          messageMetrics.onMessageBlockFromSymphony(UNSUPPORTED_MESSAGE_CONTENTS, streamId);
          return;
        }
        gatewaySocialMessage.getAttachments().clear(); // attachment cannot be sent as replies and we do not want to forward them
      }

      // Forward the message to all EMP users
      String tenantId = UserIdUtils.extractPodId(gatewaySocialMessage.getFromUserId());
      for (Map.Entry<String, List<FederatedAccount>> entry : federatedAccountsByEmp.entrySet()) {
        String emp = entry.getKey();
        // Check if message contents can be sent
        EmpEntity empEntity = empSchemaService.getEmpDefinition(emp).orElseThrow(EmpNotFoundProblem::new);
        EmpSchema empSchema = empEntity.getSchema();

        if (gatewaySocialMessage.isChime() && (empSchema.getSupportedFeatures() == null || empSchema.getSupportedFeatures().isChime() == null || !empSchema.getSupportedFeatures().isChime())) {
          allUserSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, messageSource.getMessage("chimes.not.supported", null, Locale.getDefault()), Collections.emptyList()));
          messageMetrics.onMessageBlockFromSymphony(UNSUPPORTED_MESSAGE_CONTENTS, streamId);
          continue;
        } else if (gatewaySocialMessage.isTable() && (empSchema.getSupportedFeatures() == null || empSchema.getSupportedFeatures().isTable() == null || !empSchema.getSupportedFeatures().isTable())) {
          allUserSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, messageSource.getMessage("tables.not.supported", null, Locale.getDefault()), Collections.emptyList()));
          messageMetrics.onMessageBlockFromSymphony(UNSUPPORTED_MESSAGE_CONTENTS, streamId);
          continue;
        }

        List<FederatedAccount> toFederatedAccountsForEmp = entry.getValue();
        List<SessionSupplier<SymphonySession>> userSessions = new ArrayList<>(toFederatedAccountsForEmp.size());

        for (FederatedAccount toFederatedAccount : toFederatedAccountsForEmp) {
          userSessions.add(datafeedSessionPool.getSessionSupplier(toFederatedAccount));
        }

        // CES-3203 Prevent messages to be sent in MIMs
        if (!gatewaySocialMessage.isRoom() && gatewaySocialMessage.getToUserIds().size() > 1) {
          String alertMessage = messageSource.getMessage("chat.mim.not.supported", new Object[]{empSchemaService.getEmpDisplayName(emp)}, Locale.getDefault());
          userSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, alertMessage, Collections.emptyList()));
          messageMetrics.onMessageBlockFromSymphony(TOO_MANY_MEMBERS, streamId);
          return;
        }

        Optional<CanChatResponse> canChat = gatewaySocialMessage.isRoom() ? Optional.of(CanChatResponse.CAN_CHAT) : adminClient.canChat(gatewaySocialMessage.getFromUserId(), toFederatedAccountsForEmp.get(0).getFederatedUserId(), emp);

        if (manageCanChat(gatewaySocialMessage, emp, streamId, userSessions, canChat)) {
          List<Attachment> attachmentsContent = null;
          List<IAttachment> blockedAttachments = new ArrayList<>();
          long totalsize = 0;
          if (!CollectionUtils.isEmpty(gatewaySocialMessage.getAttachments())) {
            Optional<BlockedFileTypes> blockedFileTypesOptional = adminClient.getBlockedFileTypes(streamId, tenantId, emp);
            Set<String> blockedAttachmentsType = blockedFileTypesOptional.isPresent() ? new HashSet<>(blockedFileTypesOptional.get()) : new HashSet<>();
            // retrieve the attachment
            try {
              attachmentsContent = new ArrayList<>();
              for (IAttachment attachment : gatewaySocialMessage.getAttachments()) {

                if(blockedAttachmentsType.contains(attachment.getContentType())) {
                  blockedAttachments.add(attachment);
                } else {
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
              }
            } catch (DataBufferLimitException dbe) {
              String alertMessage = messageSource.getMessage("message.partially.sent", new Object[]{gatewaySocialMessage.getMessageId()}, Locale.getDefault());
              allUserSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, alertMessage, Collections.emptyList()));
              return;
            }
          }

          if (!blockedAttachments.isEmpty()) {

            String types = blockedAttachments.stream().map(IAttachmentEntity::getContentType).collect(Collectors.joining(", "));
            String alertMessage = messageSource.getMessage("attachment.blocked", new Object[]{types, empEntity.getDisplayName()}, Locale.getDefault());
            allUserSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, alertMessage, Collections.emptyList()));
          }


          // TODO Define the behavior in case of message not correctly sent to all EMPs
          //  need a recovery mechanism to re-send message in case of error only on some EMPs
          //
          // TODO Clarify the behavior when all MIM federated users have not joined the MIM
          //  Example: a WhatsApp user must join a WhatsGroup to discuss in the associated
          //  Proposition 1: block the chat (system message indicated that the chat is not possible until everyone has joined
          //  Proposition 2: allow chatting as soon as one federated has joined. In this case, what about the history of messages?

          messageMetrics.onSendMessageFromSymphony(gatewaySocialMessage.getFromUser(), toFederatedAccountsForEmp, streamId);

          if(!StringUtils.isEmpty(gatewaySocialMessage.getTextContent()) || (attachmentsContent != null && !attachmentsContent.isEmpty())) {
            Optional<SendMessageResponse> sendMessageResponse = empClient.sendMessage(emp,
              streamId,
              gatewaySocialMessage.getMessageId(),
              gatewaySocialMessage.getFromUser(),
              toFederatedAccountsForEmp,
              gatewaySocialMessage.getTimestamp(),
              //we send raw message to whatsapp, so whatsapp api can format the message correctly
              gatewaySocialMessage.getTextContent(),
              gatewaySocialMessage.getDisclaimerForEmp(),
              attachmentsContent,
              inlineMessageRequest);
            if (sendMessageResponse.isEmpty()) {
              allUserSessions.forEach(session -> symphonyMessageSender.sendAlertMessage(session, streamId, messageSource.getMessage("message.not.delivered", new Object[]{gatewaySocialMessage.getMessageId()}, Locale.getDefault()), Collections.emptyList()));
            } else {
              this.dealWithMessageSentPartially(gatewaySocialMessage, sendMessageResponse.get(), allUserSessions);
            }
          }
        }
      }
    } catch (UnknownDatafeedUserException e) {
      // should never happen, as we have a FederatedAccount
      throw new IllegalStateException(e);
    }
  }

  private SendmessagerequestInlineMessage getInlineQuote(GatewaySocialMessage gatewaySocialMessage, String streamId, List<FederatedAccount> federatedAccounts, List<SessionSupplier<SymphonySession>> allUserSessions) throws UnknownDatafeedUserException {
    SendmessagerequestInlineMessage inlineMessageRequest = null;
    CustomEntity quote = gatewaySocialMessage.getCustomEntity(CustomEntity.QUOTE_TYPE).get();
    gatewaySocialMessage.setTextContent(gatewaySocialMessage.getTextContent().substring(quote.getEndIndex()));
    String id = StreamUtil.toUrlSafeStreamId(quote.getData().get("id").toString());
    Optional<SBEEventMessage> messageSearch = symphonyService.getEncryptedMessage(id, allUserSessions.get(0));
    if (messageSearch.isPresent()) {
      SBEEventMessage inlineMessage = messageSearch.get();
      try {
        // federatedAccounts is never empty
        messageDecryptor.decrypt(inlineMessage, federatedAccounts.get(0).getSymphonyUserId(), federatedAccounts.get(0).getSymphonyUsername());
      } catch (DecryptionException e) {
        LOG.error("Unable to decrypt social message: stream={} members={}", streamId, id, e);
        return null;
      }

      Optional<CustomEntity> inlineQuote = inlineMessage.getCustomEntity(CustomEntity.QUOTE_TYPE);

      if (inlineQuote.isPresent()) {
        inlineMessage.setText(inlineMessage.getText().substring(inlineQuote.get().getEndIndex()));
      }
      inlineMessageRequest = new SendmessagerequestInlineMessage()
        .messageId(StreamUtil.toUrlSafeStreamId(inlineMessage.getMessageId()))
        .text(inlineMessage.getText())
        .timestamp(inlineMessage.getIngestionDate())
        .fromMember(new ChannelMember()
          .symphonyId(String.valueOf(inlineMessage.getFrom().getId()))
          .displayName(inlineMessage.getFrom().getPrettyName())
          .firstName(inlineMessage.getFrom().getFirstName())
          .lastName(inlineMessage.getFrom().getSurName())
          .companyName(inlineMessage.getFrom().getCompany())
        );
      if (inlineMessage.getAttachments() != null) {
        List<com.symphony.sfs.ms.emp.generated.model.AttachmentInfo> attachmentInfos = inlineMessage
          .getAttachments()
          .stream()
          .map(a -> new com.symphony.sfs.ms.emp.generated.model.AttachmentInfo().fileName(a.getName()).contentType(a.getContentType()))
          .collect(Collectors.toList());
        inlineMessageRequest.setAttachments(attachmentInfos);
      }
    }
    return inlineMessageRequest;
  }

  private void dealWithMessageSentPartially(GatewaySocialMessage gatewaySocialMessage, SendMessageResponse sendMessageResponse, List<SessionSupplier<SymphonySession>> allUserSessions) {
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

  private boolean manageCanChat(GatewaySocialMessage gatewaySocialMessage, String emp, String streamId, List<SessionSupplier<SymphonySession>> userSessions, Optional<CanChatResponse> canChat) {
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
    return true;
  }

  @NewSpan
  public void markMessagesAsRead(SetMessagesAsReadRequest setMessagesAsReadRequest) {
    try {
      SessionSupplier<SymphonySession> userSession = datafeedSessionPool.getSessionSupplierOrFail(setMessagesAsReadRequest.getSymphonyUserId());
      SendMessageStatusRequest sendMessageStatusRequest = new SendMessageStatusRequest(setMessagesAsReadRequest.getTimestamp(), setMessagesAsReadRequest.getMessageIds(), true, setMessagesAsReadRequest.getStreamId());
      messageStatusService.markMessagesAsRead(podConfiguration.getUrl(), userSession, Collections.singletonList(sendMessageStatusRequest));
    } catch (UnknownDatafeedUserException e) {
      LOG.error("Mark Messages as read | Session not found from symphony user {}", setMessagesAsReadRequest.getSymphonyUserId());
    }
  }

  @NewSpan
  public RetrieveMessagesResponse retrieveMessages(String threadId, List<MessageId> messageIds, String symphonyUserId, OffsetDateTime startTime, OffsetDateTime endTime) {
    try {
      List<MessageInfo> messageInfos = new ArrayList<>();

      SessionSupplier<SymphonySession> userSession = datafeedSessionPool.getSessionSupplierOrFail(symphonyUserId);

      Map<String, SBEEventMessage> sbeEventMessages = new HashMap<>();
      ThreadMessagesResponse response;

      long from = startTime.toInstant().toEpochMilli();
      long to = endTime.toInstant().toEpochMilli();

      do {
        response = streamService.retrieveSocialMessagesList(podConfiguration.getUrl(), userSession, threadId, POD_BATCH_REQUEST_SIZE, from, to).orElseThrow(RetrieveMessageFailedProblem::new);

        for (MessageEnvelope messageEnvelope: response.getEnvelopes()) {
          sbeEventMessages.put(StreamUtil.toUrlSafeStreamId(messageEnvelope.getMessage().getMessageId()), messageEnvelope.getMessage());
        }

        if (!response.getEnvelopes().isEmpty()) {
          to = response.getEnvelopes().get(response.getEnvelopes().size() - 1).getMessage().getIngestionDate();
        }

      } while (response.getEnvelopes().size() == POD_BATCH_REQUEST_SIZE);

      for (MessageId id : messageIds) {
        SBEEventMessage sbeEventMessage = sbeEventMessages.get(id.getMessageId());
        if (sbeEventMessage == null) {
          sbeEventMessage = symphonyService.getEncryptedMessage(id.getMessageId(), userSession).orElseThrow(RetrieveMessageFailedProblem::new);
        }

        if (sbeEventMessage != null){
          MessageInfo messageInfo = symphonyMessageSender.decryptAndBuildMessageInfo(sbeEventMessage.toBuilder().build(), symphonyUserId, userSession, sbeEventMessages);
          messageInfos.add(messageInfo);
        }
      }

      return new RetrieveMessagesResponse().messages(messageInfos);
    } catch (UnknownDatafeedUserException e) {
      LOG.error("Session not found from symphony user {}", symphonyUserId);
      throw new MessageSenderNotFoundProblem();
    } catch (DecryptionException e) {
      LOG.error("Could not decrypt message for user {}", symphonyUserId);
      throw new RetrieveMessageFailedProblem();
    }
  }

  @NewSpan
    public MessageInfoWithCustomEntities sendMessage(String streamId, String fromSymphonyUserId, String tenantId, FormattingEnum formatting, String text, List<SymphonyAttachment> attachments, boolean forwarded, String parentMessageId, boolean attachmentReplySupported, Optional<List<String>> attachmentMessageIds) {
    MDC.put("streamId", streamId);
    FederatedAccount federatedAccount = federatedAccountRepository.findBySymphonyId(fromSymphonyUserId).orElseThrow(() -> {
      messageMetrics.onMessageBlockToSymphony(FEDERATED_ACCOUNT_NOT_FOUND, streamId);
      return new MessageSenderNotFoundProblem();
    });
    MDC.put("federatedUserId", federatedAccount.getFederatedUserId());
    MessageInfoWithCustomEntities symphonyMessage = null;
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
        int maxTextLength = empConfig.getMaxTextLength().getOrDefault(federatedAccount.getEmp(), MAX_TEXT_LENGTH);
        boolean textTooLong = (text.length() > maxTextLength);

        if (attachments != null && attachments.size() > 0 && tenantId != null) {
          Optional<BlockedFileTypes> blockedFileTypesOptional = adminClient.getBlockedFileTypes(streamId, tenantId, federatedAccount.getEmp());
          Set<String> empBlockedTypes = blockedFileTypesOptional.isPresent() ? new HashSet<>(blockedFileTypesOptional.get()) : new HashSet<>();

          if (!empBlockedTypes.isEmpty()) {
            List<SymphonyAttachment> attachmentsBlocked = attachments.stream().filter(a -> empBlockedTypes.contains(a.getContentType())).collect(Collectors.toList());
            if (!attachmentsBlocked.isEmpty()) {
              throw new AttachmentBlockedProblem(null, Map.of("attachmentsBlocked", attachmentsBlocked.stream().map(a -> new AttachmentBlocked().name(a.getFileName()).mimeType(a.getContentType())).collect(Collectors.toList())));
            }
          }
        }

        symphonyMessage = forwardIncomingMessageToSymphony(streamId, fromSymphonyUserId, formatting, text, attachments, parentMessageId, forwarded, textTooLong, maxTextLength, attachmentReplySupported, attachmentMessageIds)
          .orElseThrow(SendMessageFailedProblem::new);
        // In the case the message was sent truncated, send an alert to the Symphony and Federated users (CES-1912)
        if (textTooLong) {
          String alertMessage = String.format(TEXT_TOO_LONG_WARNING, maxTextLength);
          feedbackAboutIncomingMessage(federatedAccount.getEmp(), streamId, fromSymphonyUserId, alertMessage, TypeEnum.ALERT);

          boolean isRoom = streamInfo.getStreamType().getType() == StreamTypes.ROOM;

          SessionSupplier<SymphonySession> session;
          if (isRoom) {
            session = datafeedSessionPool.getBotSessionSupplier();
          } else {
            session = datafeedSessionPool.getSessionSupplierOrFail(fromSymphonyUserId);
          }
          symphonyMessageSender.sendAlertMessage(session, streamId, alertMessage, Collections.emptyList());
        }
      }
    } catch (UnknownDatafeedUserException e) {
      // should never happen, as we have a FederatedAccount
      throw new IllegalStateException(e);
    }

    return symphonyMessage;
  }

  @NewSpan
  public MessageInfoWithCustomEntities sendSystemMessage(String streamId, FormattingEnum formatting, String text, String title, String fromSymphonyUserId) {
    boolean isRoom = getStreamInfo(streamId).getStreamType().getType() == StreamTypes.ROOM;

    try {
      SessionSupplier<SymphonySession> session;
      if (isRoom) {
        session = datafeedSessionPool.getBotSessionSupplier();
      } else {
        session = datafeedSessionPool.getSessionSupplierOrFail(fromSymphonyUserId);
      }

      Optional<MessageInfoWithCustomEntities> symphonyMessage;
      if (formatting == null) {
        return symphonyMessageSender.sendRawMessage(session, streamId, "<messageML>" + text + "</messageML>").orElseThrow(SendMessageFailedProblem::new);
      }

      switch (formatting) {
        case SIMPLE:
          symphonyMessage = symphonyMessageSender.sendSimpleMessage(session, streamId, text);
          break;
        case NOTIFICATION:
          symphonyMessage = symphonyMessageSender.sendNotificationMessage(session, streamId, text);
          break;
        case INFO:
          symphonyMessage = symphonyMessageSender.sendInfoMessage(session, streamId, text);
          break;
        case ALERT:
          List<String> errors = Collections.emptyList();
          symphonyMessage = symphonyMessageSender.sendAlertMessage(session, streamId, text, title, errors);
          break;
        default:
          throw newConstraintViolation(new Violation("formatting", "invalid type, must be one of " + Arrays.toString(FormattingEnum.values())));
      }

      return symphonyMessage.orElseThrow(SendMessageFailedProblem::new);
    } catch (UnknownDatafeedUserException e) {
      // should never happen, as we have a FederatedAccount
      throw new IllegalStateException(e);
    }
  }


  private Optional<MessageInfoWithCustomEntities> forwardIncomingMessageToSymphony(String streamId, String fromSymphonyUserId, FormattingEnum formatting, String text, List<SymphonyAttachment> attachments, String parentMessageId, boolean forwarded,
                                                                                   boolean truncate, int maxTextLength, boolean attachmentReplySupported, Optional<List<String>> attachmentMessageIds) {
    // TODO: remove this parameter for all call using it
    String toSymphonyUserId = null;
    LOG.info("incoming message");
    if (StringUtils.isEmpty(text)) {
      text = " "; // this is the minimum message for symphony
    } else if (truncate) {
      text = text.substring(0, maxTextLength) + "...";
    }
    String messageML = "<messageML>" + text + "</messageML>";
    if (forwarded) {
      return symphonyMessageSender.sendForwardedMessage(streamId, fromSymphonyUserId, text, attachments);
    } else if (parentMessageId != null) {
      // we need pure text in case of relied message
      return symphonyMessageSender.sendReplyMessage(streamId, fromSymphonyUserId, text, parentMessageId, attachmentReplySupported, attachmentMessageIds);
    } else if (attachments != null && attachments.size() > 0) {
      return symphonyMessageSender.sendRawMessageWithAttachments(streamId, fromSymphonyUserId, messageML, toSymphonyUserId, attachments);
    } else {
      if (formatting == null) {
        return symphonyMessageSender.sendRawMessage(streamId, fromSymphonyUserId, messageML, toSymphonyUserId);
      }

      switch (formatting) {
        case SIMPLE:
          return symphonyMessageSender.sendSimpleMessage(streamId, fromSymphonyUserId, text, toSymphonyUserId);
        case NOTIFICATION:
          return symphonyMessageSender.sendNotificationMessage(streamId, fromSymphonyUserId, text, toSymphonyUserId);
        case INFO:
          return symphonyMessageSender.sendInfoMessage(streamId, fromSymphonyUserId, text, toSymphonyUserId);
        case ALERT:
          return symphonyMessageSender.sendAlertMessage(streamId, fromSymphonyUserId, text, toSymphonyUserId);
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


    return streamService.getStreamInfo(podConfiguration.getUrl(), datafeedSessionPool.getBotSessionSupplier(), streamId).orElseThrow(CannotRetrieveStreamIdProblem::new);
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
      canChatResponse.isPresent() && canChatResponse.get() == CanChatResponse.NO_CONTACT && formatting != null) {
      return Optional.empty();
    }

    String message = messageSource.getMessage("contact.not.available", null, Locale.getDefault());
    return Optional.of(message);
  }
}
