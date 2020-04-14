package com.symphony.sfs.ms.chat.service.external;

import com.symphony.sfs.ms.starter.service.MicroServiceClient;
import com.symphony.sfs.ms.starter.service.MicroserviceResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.UserInfo;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * @author enrico.molino (09/04/2020)
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class DefaultAdminClient implements AdminClient {

  public static final String ADMIN_MICRO_SERVICE_NAME = "sfs-ms-admin";

  private final MicroServiceClient microServiceClient;
  private final MicroserviceResolver microserviceResolver;

  @Override
  public Optional<UserInfo> getAdvisor(String userId) {
    return microServiceClient.get(microserviceResolver.buildMicroserviceUri(ADMIN_MICRO_SERVICE_NAME, GET_ADVISOR_ENDPOINT, userId), UserInfo.class);
  }
}
