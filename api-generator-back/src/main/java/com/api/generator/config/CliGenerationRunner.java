package com.api.generator.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Keeps the original CLI behaviour behind a dedicated Spring profile.
 * <p>
 * Run with: <code>mvn spring-boot:run -Dspring-boot.run.profiles=cli</code>
 */
@Configuration
@Profile("cli")
public class CliGenerationRunner {

    /**
     * Executes the generation process on application startup (CLI mode).
     */
    @Bean
    public CommandLineRunner commandLineRunner(GenerationOrchestrator orchestrator, GeneratorProperties props) {
        return args -> orchestrator.generate(props);
    }
}
