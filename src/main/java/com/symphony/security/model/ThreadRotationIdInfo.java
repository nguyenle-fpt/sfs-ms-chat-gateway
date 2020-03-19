package com.symphony.security.model;

/**
 * Created by aaron@symphony.com on 7/11/16.
 */
public class ThreadRotationIdInfo {
  private String streamId;
  private int acceptedRotationId;
  private int acceptedRotationRange;
  private int retiredRotationId;

  public ThreadRotationIdInfo(String streamId, int acceptedRotationId, int acceptedRotationRange,
      int retiredRotationId) {
    this.streamId = streamId;
    this.acceptedRotationId = acceptedRotationId;
    this.acceptedRotationRange = acceptedRotationRange;
    this.retiredRotationId = retiredRotationId;
  }

  public String getStreamId() {
    return streamId;
  }

  public int getAcceptedRotationId() {
    return acceptedRotationId;
  }

  public int getAcceptedRotationRange() {
    return acceptedRotationRange;
  }

  public int getRetiredRotationId() {
    return retiredRotationId;
  }
}
