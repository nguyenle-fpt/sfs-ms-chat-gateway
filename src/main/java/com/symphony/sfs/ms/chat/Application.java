package com.symphony.sfs.ms.chat;

import com.symphony.sfs.ms.starter.SfsApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.Environment;

public class Application extends SfsApplication {

  public Application(Environment env) {
    super(env);
  }

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
