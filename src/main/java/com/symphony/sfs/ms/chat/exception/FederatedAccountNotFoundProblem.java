package com.symphony.sfs.ms.chat.exception;

import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

import java.net.URI;

import static com.symphony.sfs.ms.starter.util.ProblemUtils.getProblemType;

public class FederatedAccountNotFoundProblem extends AbstractThrowableProblem {
  private static final String PROBLEM_MESSAGE = "Federated Account Not Found.";
  private static final URI PROBLEM_URI = getProblemType("federated.account.not.found");

  public FederatedAccountNotFoundProblem() {
    super(PROBLEM_URI, PROBLEM_MESSAGE, Status.BAD_REQUEST);
  }
}
