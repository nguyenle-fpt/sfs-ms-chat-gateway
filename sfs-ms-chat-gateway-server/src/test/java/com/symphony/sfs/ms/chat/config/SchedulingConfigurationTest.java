package com.symphony.sfs.ms.chat.config;

import com.symphony.sfs.ms.starter.scheduling.SfsEventChecker;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.TriggerTask;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SchedulingConfigurationTest {

  @Test
  void triggerTasks() {

    SfsEventChecker sfsEventChecker = mock(SfsEventChecker.class);

    assertEquals(1, new SchedulingConfiguration(sfsEventChecker).triggerTasks().size());
  }
}
