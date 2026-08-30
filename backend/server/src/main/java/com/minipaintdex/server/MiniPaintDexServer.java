package com.minipaintdex.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.minipaintdex")
public class MiniPaintDexServer {
    public static void main(String[] args) {
        SpringApplication.run(MiniPaintDexServer.class, args);
    }
}
