package com.symphony.sfs.ms.chat.util;

import com.github.jknack.handlebars.Handlebars;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SymphonySystemMessageTemplateProcessorTest {

  private Handlebars handlebars;
  private SymphonySystemMessageTemplateProcessor templateProcessor;

  @BeforeEach
  public void setUp() {
    handlebars = new Handlebars();

    templateProcessor = new SymphonySystemMessageTemplateProcessor(handlebars);
  }

  @Test
  void testProcessMessageContent() {
    String message = templateProcessor.process("Message Content", "symphony_message_test_template");
    assertEquals("<p>Message Content</p>\n", message);
  }

  @Test
  void testProcessMessageContent_NoExistingTemplate() {
    String message = templateProcessor.process("Message Content", "symphony_message_test_no_existing_template");
    assertEquals("SymphonySystemMessage(content=Message Content, title=null, description=null)", message);

  }

  @Test
  void testProcessSymphonySystemMessage() {
    String message = templateProcessor.process(
      SymphonySystemMessage.builder().content("content").title("title").description("description").build(),
      "symphony_message_test_template");
    assertEquals("<p>title</p>\n<p>content</p>\n", message);
  }

  @Test
  void testProcessSymphonySystemMessage_NoExistingTemplate() {
    String message = templateProcessor.process(
      SymphonySystemMessage.builder().content("content").title("title").description("description").build(),
      "symphony_message_test_no_existing_template");
    assertEquals("SymphonySystemMessage(content=content, title=title, description=description)", message);
  }

  @Test
  void testProcess() {
    String message = templateProcessor.process(
      Collections.singletonMap("message", Map.of("content", "content", "title", "title")),
      "symphony_message_test_template");
    assertEquals("<p>title</p>\n<p>content</p>\n", message);
  }

  @Test
  void testProcess_NoExistingTemplate() {
    String message = templateProcessor.process(
      Map.of("content", "content", "title", "title"),
      "symphony_message_test_no_existing_template");

    // content and title entries in any order
    assertThat(message, matchesPattern("\\{(?=.*title=title)(?=.*content=content).*\\}"));
  }
}
