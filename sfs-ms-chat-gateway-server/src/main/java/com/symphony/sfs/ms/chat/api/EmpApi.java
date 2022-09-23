package com.symphony.sfs.ms.chat.api;

import com.symphony.sfs.ms.chat.generated.model.FileExtensionList;
import com.symphony.sfs.ms.chat.service.EmpSchemaService;
import com.symphony.sfs.ms.chat.service.PodConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.sleuth.annotation.ContinueSpan;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class EmpApi implements com.symphony.sfs.ms.chat.generated.api.EmpApi {

  private final EmpSchemaService empSchemaService;
  private final PodConfigService podConfigService;

  @Override
  @ContinueSpan
  public ResponseEntity<Void> reloadEmps() {
    LOG.info("Reload EMPs");
    empSchemaService.loadEmpDefinitions();
    return ResponseEntity.noContent().build();
  }

  @Override
  @ContinueSpan
  public ResponseEntity<FileExtensionList> getAllowedFileExtensions() {
    LOG.info("Get allowed file extensions");
    return ResponseEntity.ok(podConfigService.getEmpAllowedFileTypes());
  }
}
