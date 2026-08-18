package com.api.generator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.security.data-encryption")
public class SensitivePayloadProperties {

    private String currentKey = "";
    private List<String> previousKeys = new ArrayList<>();

    public String getCurrentKey() {
        return currentKey;
    }

    public void setCurrentKey(String currentKey) {
        this.currentKey = currentKey;
    }

    public List<String> getPreviousKeys() {
        return previousKeys;
    }

    public void setPreviousKeys(List<String> previousKeys) {
        this.previousKeys = previousKeys == null ? new ArrayList<>() : new ArrayList<>(previousKeys);
    }
}
