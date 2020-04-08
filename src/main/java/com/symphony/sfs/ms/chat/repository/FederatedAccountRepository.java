package com.symphony.sfs.ms.chat.repository;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.symphony.sfs.ms.chat.model.FederatedAccount;
import com.symphony.sfs.ms.starter.dynamo.AbstractRawDynamoRepository;
import com.symphony.sfs.ms.starter.dynamo.schema.DynamoSchema;
import com.symphony.sfs.ms.starter.dynamo.util.AttributeMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.symphony.sfs.ms.chat.config.DynamoConfiguration.GSI1_IDX;
import static com.symphony.sfs.ms.chat.model.FederatedAccountMapper.federatedAccountGsi1Pk;
import static com.symphony.sfs.ms.chat.model.FederatedAccountMapper.federatedAccountGsi1Sk;
import static com.symphony.sfs.ms.chat.model.FederatedAccountMapper.federatedAccountPk;
import static com.symphony.sfs.ms.chat.model.FederatedAccountMapper.federatedAccountSk;

@Repository
@Slf4j
public class FederatedAccountRepository extends AbstractRawDynamoRepository {

  public FederatedAccountRepository(AmazonDynamoDB amazonDynamoDB, DynamoSchema schema) {
    super(amazonDynamoDB, schema);
  }

  public FederatedAccount save(FederatedAccount entity) {
    return super.save(entity);
  }

  public FederatedAccount saveIfNotExists(FederatedAccount entity) {
    String pkName = schema.getPrimaryKey().getPartitionKeyName();
    String skName = schema.getPrimaryKey().getSortKeyName();

    AttributeMap map = entity.toAttributeMap();
    amazonDynamoDB.putItem(new PutItemRequest()
      .withTableName(schema.getTableName())
      .withItem(map)
      .withConditionExpression("attribute_not_exists(" + pkName + ") AND attribute_not_exists(" + skName + ")")
    );

    LOG.debug("Document {}={}, {}={} successfully inserted",
      pkName, map.getString(pkName),
      skName, map.getString(skName)
    );

    return entity; // should it be a copy?
  }

  public List<FederatedAccount> findByFederatedUserId(String federatedUserId) {
    return findByPartitionKey(federatedAccountPk(federatedUserId), FederatedAccount::new);
  }

  public Optional<FederatedAccount> findByFederatedUserIdAndEmp(String federatedUserId, String emp) {
    return findByPrimaryKey(federatedAccountPk(federatedUserId), federatedAccountSk(emp), FederatedAccount::new);
  }

  public Optional<FederatedAccount> findBySymphonyId(String symphonyId) {
    return findBySecondaryKey(GSI1_IDX, federatedAccountGsi1Pk(symphonyId), federatedAccountGsi1Sk(), FederatedAccount::new);
  }

}
