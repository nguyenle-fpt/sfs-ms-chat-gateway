package com.symphony.sfs.ms.chat.exception;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AdvisorNotFoundException extends Exception {

  private final String userId;

}
