package com.symphony.sfs.ms.chat.exception;

import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

import java.net.URI;

import static com.symphony.sfs.ms.starter.util.ProblemUtils.getProblemType;

public class BlastAttachmentUploadException extends AbstractThrowableProblem {
  private static final String PROBLEM_MESSAGE = "Forward message with attachment failed";
  private static final URI PROBLEM_URI = getProblemType("forward.attachment.error");
  public BlastAttachmentUploadException() {
    super(PROBLEM_URI, PROBLEM_MESSAGE, Status.BAD_REQUEST);
  }
}
