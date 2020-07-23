package com.symphony.sfs.ms.chat.service;

import com.symphony.sfs.ms.chat.datafeed.DatafeedListener;
import com.symphony.sfs.ms.chat.exception.UnknownDatafeedUserException;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.chat.repository.FederatedAccountRepository;
import com.symphony.sfs.ms.starter.symphony.auth.SymphonySession;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FederatedAccountSessionService implements DatafeedListener {

  private final FederatedAccountRepository federatedAccountRepository;

  @NewSpan
  public Optional<FederatedAccount> findBySymphonyId(String symphonyId) {
    return federatedAccountRepository.findBySymphonyId(symphonyId);
  }

  @NewSpan
  public FederatedAccount findBySymphonyIdOrFail(String symphonyId) throws UnknownDatafeedUserException {
    return findBySymphonyId(symphonyId)
      .orElseThrow(() -> new UnknownDatafeedUserException("Unmanaged datafeed")); // TODO better exception;
  }

  @NewSpan
  public FederatedAccount updateSession(String symphonyId, SymphonySession session) throws UnknownDatafeedUserException {
    FederatedAccount federatedAccount = findBySymphonyIdOrFail(symphonyId);
    return updateSession(federatedAccount, session);
  }

  @NewSpan
  public FederatedAccount updateSession(FederatedAccount federatedAccount, SymphonySession session) {
    federatedAccount.setSessionToken(session.getSessionToken());
    federatedAccount.setKmToken(session.getKmToken());
    return federatedAccountRepository.save(federatedAccount);
  }
}
