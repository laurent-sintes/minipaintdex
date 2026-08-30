package com.minipaintdex.server;

import com.minipaintdex.bootstrap.MiniPaintDexSpringConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = "com.minipaintdex.server")
@Import(MiniPaintDexSpringConfiguration.class)
public class MiniPaintDexServer {
    public static void main(String[] args) {
        SpringApplication.run(MiniPaintDexServer.class, args);
    }
}
