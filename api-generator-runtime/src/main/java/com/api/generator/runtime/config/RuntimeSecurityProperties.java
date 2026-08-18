package com.api.generator.runtime.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "security")
public class RuntimeSecurityProperties {

    private boolean enabled = true;
    private final Bootstrap bootstrap = new Bootstrap();
    private final Jwt jwt = new Jwt();

    @Getter
    @Setter
    public static class Bootstrap {
        private String username = "admin";
        private String password = "";
    }

    @Getter
    @Setter
    public static class Jwt {
        private String secretBase64 = "";
        private String secret = "";
        private String issuer = "generated-api";
        private String audience = "generated-api";
        private long expirationSeconds = 3600;
    }
}
