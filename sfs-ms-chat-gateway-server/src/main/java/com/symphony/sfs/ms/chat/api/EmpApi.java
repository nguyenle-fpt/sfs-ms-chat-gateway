package com.symphony.sfs.ms.chat.api;

import com.symphony.sfs.ms.chat.service.EmpSchemaService;
import org.springframework.cloud.sleuth.annotation.ContinueSpan;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class EmpApi implements com.symphony.sfs.ms.chat.generated.api.EmpApi {

  private final EmpSchemaService empSchemaService;

  @Override
  @ContinueSpan
  public ResponseEntity<Void> reloadEmps() {
    empSchemaService.loadEmpDefinitions();
    return ResponseEntity.noContent().build();
  }
}
