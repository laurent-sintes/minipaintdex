package com.minipaintdex.server;

import com.minipaintdex.adapter.file.FileRepositoryLayout;
import com.minipaintdex.bootstrap.MiniPaintDexProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
class WebConfiguration implements WebMvcConfigurer {
    private final Path media;

    private final String[] allowedOrigins;

    WebConfiguration(FileRepositoryLayout layout, MiniPaintDexProperties properties) {
        this.media = layout.mediaDirectory();
        this.allowedOrigins = properties.web().allowedOrigins().toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(allowedOrigins).allowedMethods("GET", "POST", "OPTIONS").allowedHeaders("Content-Type", "Idempotency-Key", "X-Correlation-Id");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/media/**").addResourceLocations(media.toUri().toString());
        registry.addResourceHandler("/**").addResourceLocations("classpath:/static/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        for (var route : new String[]{"/market/**", "/workshop/**", "/shopping"}) {
            registry.addViewController(route).setViewName("forward:/index.html");
        }
    }
}
