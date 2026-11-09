package com.kitchenai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class KitchenAiApplication {
  public static void main(String[] args) {
    SpringApplication.run(KitchenAiApplication.class, args);
  }
}
