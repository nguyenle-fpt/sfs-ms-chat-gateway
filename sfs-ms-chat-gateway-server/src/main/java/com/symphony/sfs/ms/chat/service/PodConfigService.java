package com.symphony.sfs.ms.chat.service;

import com.symphony.sfs.ms.chat.datafeed.DatafeedSessionPool;
import com.symphony.sfs.ms.chat.generated.model.FileExtensionList;
import com.symphony.sfs.ms.chat.service.symphony.SymphonyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PodConfigService {
  private final DatafeedSessionPool datafeedSessionPool;
  private final SymphonyService symphonyService;

  @NewSpan
  public FileExtensionList getEmpAllowedFileTypes() {
    FileExtensionList extList = new FileExtensionList();
    symphonyService.getAllowedFileTypes(datafeedSessionPool.getBotSessionSupplier()).forEach(type -> extList.add(type));
    return extList;
  }
}
