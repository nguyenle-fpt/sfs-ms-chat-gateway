package com.symphony.sfs.ms.chat.service;

import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

import java.net.URI;

import static com.symphony.sfs.ms.starter.util.ProblemUtils.getProblemType;

/**
 *
 */
public class CreateUserProblem extends AbstractThrowableProblem {
  private static final String PROBLEM_MESSAGE = "Could not create user.";
  private static final URI PROBLEM_URI = getProblemType("create.user.error");

  public CreateUserProblem() {
    super(PROBLEM_URI, PROBLEM_MESSAGE, Status.BAD_GATEWAY);
  }

}
