package com.symphony.sfs.ms.chat.datafeed;

import com.fasterxml.jackson.databind.JsonNode;
import com.symphony.sfs.ms.starter.symphony.stream.MessageAttachment;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MessageEntityData {
  /**
   * Text contained in the enricher - ex: a reply or forward message text
   */
  private String text;

  /**
   * Date string when message was ingested
   */
  private long ingestionDate;

  /**
   * Additional information that accompanies the custom entity data - ex: in replies and forwards this contains a
   * formatted string with the user and time forward/reply
   */
  private String metadata;

  /**
   * Forward and reply attachment data
   */
  private List<MessageAttachment> attachments;

  /**
   * Stream id the custom entity is based on - ex: in forward and reply, this is the stream id of the original message
   */
  private String streamId;

  /**
   * Id or message id the custom entity is based on - ex: in forward and reply, this is the id of the original message
   */
  private String id;


  /**
   * Message presentation ML the custom entity is based on - ex: in forward and reply, this is the presentationML of the original message
   */
  private String presentationML;

  /**
   * Entities of the original message in forward and reply
   */
  private JsonNode entities;

  /**
   * customEntities of the original message in forward and reply
   */
  private List<JsonNode> customEntities;

  /**
   * entityJSON of the original message in forward and reply
   */
  private JsonNode entityJSON;

  private List<JsonNode> jsonMedia;
}

