package com.api.generator.api.service;

import com.api.generator.auth.JwtProperties;
import com.api.generator.config.SensitivePayloadProperties;
import com.api.generator.config.GeneratorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class GenerationPayloadCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PAYLOAD_PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey currentKey;
    private final List<SecretKey> decodeKeys;
    private final SecureRandom secureRandom = new SecureRandom();

    public GenerationPayloadCodec(SensitivePayloadProperties properties, JwtProperties jwtProperties) {
        List<String> configuredKeys = new ArrayList<>();
        if (properties.getCurrentKey() != null && !properties.getCurrentKey().isBlank()) {
            configuredKeys.add(properties.getCurrentKey().trim());
        }
        configuredKeys.add(jwtProperties.secret());
        if (properties.getPreviousKeys() != null) {
            configuredKeys.addAll(properties.getPreviousKeys());
        }

        LinkedHashSet<String> uniqueKeys = new LinkedHashSet<>();
        configuredKeys.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .forEach(uniqueKeys::add);

        if (uniqueKeys.isEmpty()) {
            throw new IllegalStateException("No encryption key is configured for persisted generation payloads");
        }

        List<SecretKey> materializedKeys = uniqueKeys.stream()
                .map(this::deriveKey)
                .toList();
        this.currentKey = materializedKeys.get(0);
        this.decodeKeys = materializedKeys;
    }

    public String encode(GeneratorProperties props) {
        try {
            byte[] plain = OBJECT_MAPPER.writeValueAsBytes(props);
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, currentKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain);

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return PAYLOAD_PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to persist generation payload", e);
        }
    }

    public GeneratorProperties decode(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        String trimmed = payload.trim();
        if (trimmed.startsWith("{")) {
            try {
                return OBJECT_MAPPER.readValue(trimmed, GeneratorProperties.class);
            } catch (Exception e) {
                throw new IllegalStateException("Unable to restore generation payload", e);
            }
        }
        if (!trimmed.startsWith(PAYLOAD_PREFIX)) {
            throw new IllegalStateException("Unable to restore generation payload");
        }

        byte[] combined = Base64.getDecoder().decode(trimmed.substring(PAYLOAD_PREFIX.length()));
        for (SecretKey key : decodeKeys) {
            try {
                ByteBuffer buffer = ByteBuffer.wrap(combined);
                byte[] iv = new byte[IV_LENGTH];
                buffer.get(iv);
                byte[] encrypted = new byte[buffer.remaining()];
                buffer.get(encrypted);

                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
                byte[] plain = cipher.doFinal(encrypted);
                return OBJECT_MAPPER.readValue(plain, GeneratorProperties.class);
            } catch (Exception ignored) {
                // Try the next configured key to support explicit rotation windows.
            }
        }
        throw new IllegalStateException("Unable to restore generation payload");
    }

    private SecretKey deriveKey(String secret) {
        try {
            return new SecretKeySpec(
                    MessageDigest.getInstance("SHA-256")
                            .digest(secret.getBytes(StandardCharsets.UTF_8)),
                    "AES"
            );
        } catch (Exception e) {
            throw new IllegalStateException("Unable to derive generation payload encryption key", e);
        }
    }
}
