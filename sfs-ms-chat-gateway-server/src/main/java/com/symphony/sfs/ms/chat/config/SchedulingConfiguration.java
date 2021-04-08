package com.symphony.sfs.ms.chat.config;

import com.symphony.sfs.ms.starter.scheduling.SfsEventChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.config.TriggerTask;

import java.util.Arrays;
import java.util.List;

/**
 * Additional configuration taken into account in {@link com.symphony.sfs.ms.starter.scheduling.DefaultSchedulingConfiguration}
 */
@Configuration
@RequiredArgsConstructor
public class SchedulingConfiguration {

  private final SfsEventChecker sfsEventChecker;

  @Bean
  List<TriggerTask> triggerTasks() {
    return Arrays.asList(
      new TriggerTask(() -> sfsEventChecker.run(), sfsEventChecker::nextExecutionTime)
    );
  }
}
