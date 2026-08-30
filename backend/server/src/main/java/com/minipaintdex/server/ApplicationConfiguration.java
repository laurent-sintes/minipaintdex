package com.minipaintdex.server;

import com.minipaintdex.adapter.file.FileMiniPaintDexRepository;
import com.minipaintdex.application.MiniPaintDexService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
class ApplicationConfiguration {
    @Bean
    FileMiniPaintDexRepository fileMiniPaintDexRepository(@Value("${minipaintdex.root:.}") String root) {
        return new FileMiniPaintDexRepository(Path.of(root));
    }

    @Bean
    MiniPaintDexService miniPaintDexService(FileMiniPaintDexRepository repository) {
        return new MiniPaintDexService(repository, repository);
    }
}
