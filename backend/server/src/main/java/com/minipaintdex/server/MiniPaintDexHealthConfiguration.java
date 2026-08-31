package com.minipaintdex.server;

import com.minipaintdex.application.port.EventBus;
import com.minipaintdex.application.port.PersistenceLifecycle;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class MiniPaintDexHealthConfiguration {
    @Bean("persistence")
    HealthIndicator persistenceHealth(PersistenceLifecycle persistence) {
        return () -> {
            var status = persistence.status();
            var builder = "ready".equals(status.state()) ? Health.up() : Health.down();
            return builder
                    .withDetail("state", status.state())
                    .withDetail("generation", status.generation())
                    .withDetail("storage", status.storage())
                    .withDetail("detail", status.detail())
                    .build();
        };
    }

    @Bean("eventPipeline")
    HealthIndicator eventPipelineHealth(EventBus eventBus) {
        return () -> {
            var state = eventBus.state();
            var builder = state.running() && state.accepting() ? Health.up() : Health.outOfService();
            return builder
                    .withDetail("running", state.running())
                    .withDetail("accepting", state.accepting())
                    .withDetail("recoverablePublications", state.recoverablePublications())
                    .build();
        };
    }
}
