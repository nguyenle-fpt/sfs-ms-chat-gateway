package com.symphony.sfs.ms.chat.model;

import static com.symphony.sfs.ms.starter.dynamo.util.AttributeUtils.buildStandardHierarchicalKey;

public class ChannelMapper {
  public static String channelPk(String federatedUserId) {
    return buildStandardHierarchicalKey(Channel.TYPE, federatedUserId);
  }

  public static String channelSk(String advisorSymphonyId, String emp) {
    return buildStandardHierarchicalKey(Channel.TYPE, advisorSymphonyId, emp);
  }

  public static String channelGsi1Pk(String streamId) {
    return buildStandardHierarchicalKey(Channel.TYPE, streamId);
  }

  public static String channelGsi1Sk() {
    return Channel.TYPE;
  }
}
