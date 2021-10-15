package com.symphony.sfs.ms.chat.datafeed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Builder
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SBEMessageAttachment {
  private String fileId;
  private String name;
  private boolean encrypted;
  private Long sizeInBytes;
  private Map<String, String> images;
  private String contentType;


}
