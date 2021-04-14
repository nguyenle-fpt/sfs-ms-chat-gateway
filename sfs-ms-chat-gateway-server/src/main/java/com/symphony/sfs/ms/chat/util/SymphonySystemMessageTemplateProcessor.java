package com.symphony.sfs.ms.chat.util;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class SymphonySystemMessageTemplateProcessor {

  private final Handlebars handlebars;

  /**
   * @param messageContent
   * @param templateName   basename of a file located in resources/templates folder
   * @return Processed template or unprocessed stringified input if template not found
   */
  public String process(String messageContent, String templateName) {
//    return process(SymphonySystemMessage.builder().content(messageContent).build(), templateName);
    return process(messageContent, null, Collections.emptyList(), templateName);
  }

  public String process(String messageContent, String title,  List<String> errors, String templateName) {
    return process(SymphonySystemMessage.builder().content(messageContent).title(title).errors(errors).build(), templateName);
  }

  /**
   * @param symphonySystemMessage
   * @param templateName          basename of a file located in resources/templates folder
   * @return Processed template or unprocessed stringified input if template not found
   */
  public String process(SymphonySystemMessage symphonySystemMessage, String templateName) {
    return getTemplate(templateName).map(template -> applyTemplate(template, Collections.singletonMap("message", symphonySystemMessage))).orElse(symphonySystemMessage.toString());
  }

  /**
   * @param context
   * @param templateName basename of a file located in resources/templates folder
   * @return Processed template or unprocessed stringified input if template not found
   */
  public String process(Map<String, Object> context, String templateName) {
    return getTemplate(templateName).map(template -> applyTemplate(template, context)).orElse(context.toString());
  }

  private Optional<Template> getTemplate(String templateName) {
    Template template = null;
    try {
      template = handlebars.compile(templateName);
    } catch (IOException e) {
      LOG.warn("Could not compile handlebars template {}", templateName, e);
    }

    return Optional.ofNullable(template);
  }

  private String applyTemplate(Template template, Object context) {
    try {
      return template.apply(context);
    } catch (IOException e) {
      LOG.warn("Could not apply template", e);
      return context.toString();
    }
  }
}
