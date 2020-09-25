package com.symphony.sfs.ms.chat.datafeed;

import com.google.common.base.Enums;
import com.symphony.oss.commons.dom.json.ImmutableJsonObject;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

public enum ParentRelationshipType {
  REPLY, FORWARD, OTHER, NONE;

  private static final String KEY = "parentRelationshipType";

  public static ParentRelationshipType fromJsonObject(ImmutableJsonObject jsonObject) {
    String value = Optional.ofNullable(jsonObject.getString(KEY, ParentRelationshipType.NONE.name())).orElse(StringUtils.EMPTY);
    return Enums.getIfPresent(ParentRelationshipType.class, value).or(ParentRelationshipType.OTHER);
  }
}
