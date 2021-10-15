package com.symphony.sfs.ms.chat.datafeed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SBEEventUser {
  private Long id;
  private String username;
  private String prettyNameNormalized;
  private String emailAddress;
  private String firstName;
  private String surName;
  private String prettyName;
  private String company;
}
