package com.symphony.sfs.ms.chat.model;

import com.symphony.sfs.ms.starter.dynamo.DynamoDocument;
import com.symphony.sfs.ms.starter.dynamo.util.AttributeMap;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import static com.symphony.sfs.ms.chat.config.DynamoConfiguration.GSI1_PK;
import static com.symphony.sfs.ms.chat.config.DynamoConfiguration.GSI1_SK;
import static com.symphony.sfs.ms.chat.config.DynamoConfiguration.TYPE_PREFIX;
import static com.symphony.sfs.ms.chat.model.ChannelMapper.channelGsi1Pk;
import static com.symphony.sfs.ms.chat.model.ChannelMapper.channelGsi1Sk;
import static com.symphony.sfs.ms.chat.model.ChannelMapper.channelPk;
import static com.symphony.sfs.ms.chat.model.ChannelMapper.channelSk;
import static com.symphony.sfs.ms.starter.dynamo.DynamoConstants.PARTITION_KEY;
import static com.symphony.sfs.ms.starter.dynamo.DynamoConstants.SORT_KEY;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
public class Channel extends DynamoDocument {

  public static final String TYPE = TYPE_PREFIX + "CHANNEL";

  private String streamId;
  private String advisorSymphonyId;
  private String federatedUserId;
  private String emp;

  public Channel(AttributeMap attributes) {
    super(attributes);
    this.federatedUserId = attributes.getString("federatedUserId");
    this.advisorSymphonyId = attributes.getString("advisorSymphonyId");
    this.emp = attributes.getString("emp");
    this.streamId = attributes.getString("streamId");
  }

  @Override
  public AttributeMap toAttributeMap() {
    AttributeMap attributes = super.toAttributeMap();

    attributes.putString("federatedUserId", getFederatedUserId());
    attributes.putString("advisorSymphonyId", getAdvisorSymphonyId());
    attributes.putString("emp", getEmp());
    attributes.putString("streamId", getStreamId());

    attributes.putString(PARTITION_KEY, channelPk(getFederatedUserId()));
    attributes.putString(SORT_KEY, channelSk(getAdvisorSymphonyId(), getEmp()));
    attributes.putString(GSI1_PK, channelGsi1Pk(getStreamId()));
    attributes.putString(GSI1_SK, channelGsi1Sk());
    return attributes;
  }
}
