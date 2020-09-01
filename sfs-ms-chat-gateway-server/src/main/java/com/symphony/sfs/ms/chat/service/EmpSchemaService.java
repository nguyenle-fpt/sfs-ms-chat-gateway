package com.symphony.sfs.ms.chat.service;

import com.symphony.sfs.ms.admin.generated.model.EmpEntity;
import com.symphony.sfs.ms.admin.generated.model.EmpList;
import com.symphony.sfs.ms.chat.service.external.AdminClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class EmpSchemaService {

  private final AdminClient adminClient;
  private final Map<String, EmpEntity> empDefinitions;

  public EmpSchemaService(AdminClient adminClient) {
    this.adminClient = adminClient;
    this.empDefinitions = loadEmpDefinitions();
  }

  @NewSpan
  public List<EmpEntity> getEmpList() {
    return new ArrayList<>(empDefinitions.values());
  }

  @NewSpan
  public Optional<EmpEntity> getEmpDefinition(String emp) {
    return Optional.ofNullable(empDefinitions.get(emp));
  }

  public String getEmpDisplayName(String emp) {
    // Should not happen: Return emp key if emp definition not found
    return getEmpDefinition(emp).map(EmpEntity::getDisplayName).orElse(emp);
  }

  @NewSpan
  public EmpList loadEmpDefinitions() {
    EmpList emps = adminClient.getEmpList();

    LOG.info("Loaded {} EMP definitions: {}", emps.size(), emps.keySet());
    LOG.debug("{}", emps);

    return emps;
  }

}
