package com.symphony.sfs.ms.chat.exception;

import com.symphony.oss.models.core.canon.facade.ThreadId;

import java.io.IOException;

public class ContentKeyRetrievalException extends IOException {

  public ContentKeyRetrievalException(String threadId, String userId, Long rotationId, Throwable cause) {
    super(String.format("Unable to retrieve key: threadId=%s userId=%s rotation=%s", threadId, userId, rotationId), cause);
  }

  public ContentKeyRetrievalException(String threadId, String userId, Long rotationId) {
    super(String.format("Unable to retrieve key: threadId=%s userId=%s rotation=%s, {}", threadId, userId, rotationId));
  }
}
