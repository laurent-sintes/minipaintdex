package com.minipaintdex.server.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import com.minipaintdex.bootstrap.MiniPaintDexProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {
    @Bean
    OpenAPI miniPaintDexOpenApi(MiniPaintDexProperties properties) {
        return new OpenAPI().info(new Info()
                .title("MiniPaintDex REST API")
                .description("Local market-catalog, workshop and asynchronous publication services.")
                .contact(new Contact().name(properties.application().author()))
                .version("v1"));
    }
}
