package com.symphony.sfs.ms.chat.repository;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.symphony.sfs.ms.chat.model.Channel;
import com.symphony.sfs.ms.starter.dynamo.AbstractRawDynamoRepository;
import com.symphony.sfs.ms.starter.dynamo.pagination.DocumentPage;
import com.symphony.sfs.ms.starter.dynamo.pagination.Pageable;
import com.symphony.sfs.ms.starter.dynamo.schema.DynamoSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.symphony.sfs.ms.chat.model.ChannelMapper.channelGsi1Pk;
import static com.symphony.sfs.ms.chat.model.ChannelMapper.channelGsi1Sk;
import static com.symphony.sfs.ms.chat.model.ChannelMapper.channelPk;
import static com.symphony.sfs.ms.chat.model.ChannelMapper.channelSk;
import static com.symphony.sfs.ms.starter.util.PaginationUtils.followPages;

@Repository
@Slf4j
public class ChannelRepository extends AbstractRawDynamoRepository {
  public ChannelRepository(AmazonDynamoDB amazonDynamoDB, DynamoSchema schema) {
    super(amazonDynamoDB, schema);
  }
  @NewSpan
  public Channel save(Channel channel) {
    return super.save(channel);
  }

  @NewSpan
  public Optional<Channel> findByAdvisorSymphonyIdAndFederatedUserIdAndEmp(String advisorSymphonyId, String federatedUserId, String emp) {
    return findByPrimaryKey(channelPk(federatedUserId), channelSk(advisorSymphonyId, emp), Channel::new);
  }
  @NewSpan
  public DocumentPage<Channel> findAllByFederatedUserId(String federatedUserId, Pageable pageable) {
    return findByPartitionKey(channelPk(federatedUserId), pageable, Channel::new);
  }
  @NewSpan
  public List<Channel> findAllByFederatedUserId(String federatedUserId) {
    return followPages(pageable -> findAllByFederatedUserId(federatedUserId, pageable));
  }

  @NewSpan
  public Optional<Channel> findByStreamId(String streamId) {
    return findByPrimaryKey(channelGsi1Pk(streamId), channelGsi1Sk(), Channel::new);
  }

  @NewSpan
  public void delete(Channel channel) {
    super.delete(channel);
  }
}
