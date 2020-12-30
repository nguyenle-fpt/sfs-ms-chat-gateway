package com.symphony.sfs.ms.chat.datafeed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Arrays;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CustomEntity {

  public static final String QUOTE_TYPE = "com.symphony.sharing.quote";

  private String type;

  public static List<CustomEntity> fromJSONString(String text, ObjectMapper objectMapper) throws JsonProcessingException {
    return Arrays.asList(objectMapper.readValue(text, CustomEntity[].class));
  }
}
