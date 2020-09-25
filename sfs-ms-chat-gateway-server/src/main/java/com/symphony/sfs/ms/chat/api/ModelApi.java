package com.symphony.sfs.ms.chat.api;

import com.symphony.sfs.ms.chat.generated.api.ModelGenerationApi;
import com.symphony.sfs.ms.chat.service.ModelService;
import com.symphony.sfs.ms.starter.modelGeneration.DynamoModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@Profile({"local", "dev"})
public class ModelApi implements ModelGenerationApi {

  private final ModelService modelService;

  @Override
  public ResponseEntity<Object> getModel() {
    Map<String, DynamoModel> results = modelService.generateModelDescription();

    return ResponseEntity.ok(results);
  }
}
