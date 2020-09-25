package com.symphony.sfs.ms.chat.service;

import com.symphony.sfs.ms.starter.modelGeneration.DynamoModel;
import com.symphony.sfs.ms.starter.modelGeneration.ModelGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelService {

  public static final String MODEL_CLASSES_PACKAGE = "com.symphony.sfs.ms.chat.model";
  public static final String MODEL_CLASSES_PATH = "../sfs-ms-chat-gateway-dal/target/classes/com/symphony/sfs/ms/chat/model";

  private final ModelGenerationService modelGenerationService;

  public Map<String, DynamoModel> generateModelDescription() {
    return modelGenerationService.generateModel(MODEL_CLASSES_PACKAGE, MODEL_CLASSES_PATH);
  }
}
