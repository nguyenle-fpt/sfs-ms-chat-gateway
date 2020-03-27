package com.symphony.sfs.ms.chat.service;

import com.symphony.sfs.ms.chat.config.properties.ChatConfiguration;
import com.symphony.sfs.ms.chat.service.emp.EmpEntity;
import com.symphony.sfs.ms.chat.service.emp.EmpList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.symphony.sfs.ms.starter.util.WebClientUtils.logWebClientError;

@Service
@Slf4j
public class EmpSchemaService {

  public static final String EMPS_ENDPOINT = "/api/v1/emps";

  private final WebClient webClient;
  private final ChatConfiguration chatConfiguration;
  private final Map<String, EmpEntity> empDefinitions;

  public EmpSchemaService(WebClient webClient, ChatConfiguration chatConfiguration) {
    this.webClient = webClient;
    this.chatConfiguration = chatConfiguration;
    this.empDefinitions = loadEmpDefinitions();
  }

  public List<EmpEntity> getEmpList() {
    return new ArrayList<>(empDefinitions.values());
  }

  public Optional<EmpEntity> getEmpDefinition(String emp) {
    return Optional.ofNullable(empDefinitions.get(emp));
  }

  private EmpList loadEmpDefinitions() {
    try {
      EmpList emps = webClient.get()
        .uri(chatConfiguration.getMsAdminUrl() + EMPS_ENDPOINT)
        .retrieve()
        .bodyToMono(EmpList.class)
        .block();
      return emps;
    } catch (Exception e) {
      logWebClientError(LOG, EMPS_ENDPOINT, e);
    }
    return new EmpList();
  }

}
