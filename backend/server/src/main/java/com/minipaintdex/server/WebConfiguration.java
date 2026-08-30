package com.minipaintdex.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
class WebConfiguration implements WebMvcConfigurer {
    private final Path media;

    WebConfiguration(@Value("${minipaintdex.root:.}") String root) {
        this.media = Path.of(root).toAbsolutePath().normalize().resolve("media");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins("http://127.0.0.1:5173", "http://localhost:5173").allowedMethods("GET", "POST", "OPTIONS").allowedHeaders("Content-Type", "Idempotency-Key", "X-Correlation-Id");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/media/**").addResourceLocations(media.toUri().toString());
        registry.addResourceHandler("/**").addResourceLocations("classpath:/static/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        for (var route : new String[]{"/paints/**", "/projects/**", "/market/**", "/workshop/**"}) {
            registry.addViewController(route).setViewName("forward:/index.html");
        }
    }
}
