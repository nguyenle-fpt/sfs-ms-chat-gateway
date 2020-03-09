package com.symphony.sfs.ms.chat.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmpEntity {
  private String name;
  private String displayName;
  private String microserviceUrl;
  private String iconUrl;
}
