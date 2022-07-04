package com.symphony.sfs.ms.chat;

import com.symphony.sfs.ms.starter.SfsApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;

@ComponentScan(basePackageClasses = {Application.class})
public class Application extends SfsApplication {

  public Application(Environment env) {
    super(env);
  }

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
