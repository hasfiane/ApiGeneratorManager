package com.api.generator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication(scanBasePackages = {
    "com.api.generator.account",
    "com.api.generator.admin",
    "com.api.generator.api",
    "com.api.generator.auth",
    "com.api.generator.common",
    "com.api.generator.config",
    "com.api.generator.reader",
    "com.api.generator.security",
    "com.api.generator.util"
})
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
public class ApiGeneratorManagerApplication {
    public static void main(String[] args) { SpringApplication.run(ApiGeneratorManagerApplication.class, args); }
}
