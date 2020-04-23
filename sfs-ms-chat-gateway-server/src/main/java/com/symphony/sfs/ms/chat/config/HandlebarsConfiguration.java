package com.symphony.sfs.ms.chat.config;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.cache.ConcurrentMapTemplateCache;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HandlebarsConfiguration {

  @Bean
  public Handlebars handlebars() {
    TemplateLoader loader = new ClassPathTemplateLoader();
    loader.setPrefix("/templates");
    loader.setSuffix(".hbs");


    // TODO better cache mechanism
    // Guava is used in the POC
    //    final Cache<TemplateSource, Template> templateCache = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(1000).build();
    //    return new Handlebars(loader).with((new GuavaTemplateCache(templateCache)));
    return new Handlebars(loader).with(new ConcurrentMapTemplateCache());
  }
}
