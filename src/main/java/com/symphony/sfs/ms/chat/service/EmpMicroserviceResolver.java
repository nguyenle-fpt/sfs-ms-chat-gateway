package com.symphony.sfs.ms.chat.service;

import com.symphony.sfs.ms.starter.exception.MicroserviceNotFound;
import com.symphony.sfs.ms.starter.service.MicroserviceResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilderFactory;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EmpMicroserviceResolver {

  private final EmpSchemaService empSchemaService;
  private final MicroserviceResolver microserviceResolver;
  private final UriBuilderFactory uriBuilderFactory;

  public EmpMicroserviceResolver(EmpSchemaService empSchemaService, MicroserviceResolver microserviceResolver) {
    this.microserviceResolver = microserviceResolver;
    this.empSchemaService = empSchemaService;
    this.uriBuilderFactory = new DefaultUriBuilderFactory();
  }

  public String getEmpMicroserviceBaseUri(String emp) {
    try {
      return microserviceResolver.getMicroserviceBaseUri(emp);
    } catch (MicroserviceNotFound e) {
      // NOSONAR
    }

    return empSchemaService.getEmpDefinition(emp)
      .map(EmpEntity::getMicroserviceUrl)
      .orElseThrow(() -> new MicroserviceNotFound("Emp " + emp + " not configured"));
  }

  public Map<String, String> getAllEmpMicroserviceBaseUris() {
    Map<String, String> uris = microserviceResolver.getAllMicroserviceBaseUris();
    uris.putAll(empSchemaService.getEmpList()
      .stream()
      .collect(Collectors.toMap(EmpEntity::getName, EmpEntity::getMicroserviceUrl)));
    return uris;
  }

  public URI buildEmpMicroserviceUri(String emp, String path, Object... variables) {
    String base = getEmpMicroserviceBaseUri(emp);
    return uriBuilderFactory.expand(base + path, variables);
  }

}
