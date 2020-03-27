package com.symphony.sfs.ms.chat.exception;

import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

import java.net.URI;

import static com.symphony.sfs.ms.starter.util.ProblemUtils.getProblemType;

public class FederatedAccountAlreadyExistsProblem extends AbstractThrowableProblem {
  private static final String PROBLEM_MESSAGE = "Federated Account Already Exists.";
  private static final URI PROBLEM_URI = getProblemType("federated.account.already.exists");

  public FederatedAccountAlreadyExistsProblem() {
    super(PROBLEM_URI, PROBLEM_MESSAGE, Status.CONFLICT);
  }
}
