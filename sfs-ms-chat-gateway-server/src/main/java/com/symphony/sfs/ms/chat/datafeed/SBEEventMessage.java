package com.symphony.sfs.ms.chat.datafeed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SBEEventMessage {
  private String version;
  private String id;
  private String messageId;
  private SBEEventUser from;
  private long ingestionDate;
  private String chatType;
  private String event;
  private String streamId;
  private String threadId;
  private String text;
  private String entityJSON;
  private String presentationML;
  private String disclaimer;
  private String customEntities;
  private Object entities;
  private String encryptedMedia;
  private String encryptedEntities;
  private String jsonMedia;
  private String format;
  private String parentMessageId;
  private String parentRelationshipType;
  private String encryptedFileKey;
  private int msgFeatures;
  @JsonProperty("isBlast")
  private boolean isBlast;
  private boolean isFormReply;
  private String metadata;
  private String fromPod;
  private Attributes attributes;

  @JsonProperty("isChime")
  private boolean isChime;
  private List<SBEMessageAttachment> attachments;
  private List<SBEMessageAttachment> fileKeyEncryptedAttachments;



  @Setter
  @Builder.Default
  private List<CustomEntity> parsedCustomEntities = new ArrayList<>();


  public Optional<CustomEntity> getCustomEntity(String entityType) {
    return parsedCustomEntities.stream().filter(customEntity -> entityType.equals(customEntity.getType())).findFirst();
  }

  public enum Versions {
    SOCIALMESSAGE
  }
}
