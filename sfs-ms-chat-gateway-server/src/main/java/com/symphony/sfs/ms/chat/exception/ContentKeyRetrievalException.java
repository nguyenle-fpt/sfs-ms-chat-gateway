package com.symphony.sfs.ms.chat.exception;

import com.symphony.oss.models.core.canon.facade.ThreadId;

import java.io.IOException;

public class ContentKeyRetrievalException extends IOException {

  public ContentKeyRetrievalException(ThreadId threadId, String username, Long rotationId, Throwable cause) {
    super(String.format("Unable to retrieve key: threadId=%s username=%s rotation=%s", threadId, username, rotationId), cause);
  }

  public ContentKeyRetrievalException(ThreadId threadId, String username, Long rotationId) {
    super(String.format("Unable to retrieve key: threadId=%s username=%s rotation=%s, {}", threadId, username, rotationId));
  }
}
