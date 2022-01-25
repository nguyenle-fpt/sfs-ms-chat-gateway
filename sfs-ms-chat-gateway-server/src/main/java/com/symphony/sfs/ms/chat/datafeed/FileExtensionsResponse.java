package com.symphony.sfs.ms.chat.datafeed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileExtensionsResponse {

  @JsonProperty("data")
  private List<FileExtension> data;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class FileExtension {

    @JsonProperty("extension")
    private String extension;

    @JsonProperty("scope_internal")
    private Boolean scopeInternal;

    @JsonProperty("scope_external")
    private Boolean scopeExternal;

    @JsonProperty("source")
    private SourceEnum source;

    public enum SourceEnum {
      SYSTEM,  CUSTOMER,
    };
  }
}
