package com.symphony.sfs.ms.chat.exception;

import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

import java.net.URI;

import static com.symphony.sfs.ms.starter.util.ProblemUtils.getProblemType;

public class InlineReplyMessageException extends AbstractThrowableProblem {
  private static final String PROBLEM_MESSAGE = "Could not reply to attachment";
  private static final URI PROBLEM_URI = getProblemType("quote.attachment.error");
  public InlineReplyMessageException() {
    super(PROBLEM_URI, PROBLEM_MESSAGE, Status.BAD_REQUEST);
  }
}
