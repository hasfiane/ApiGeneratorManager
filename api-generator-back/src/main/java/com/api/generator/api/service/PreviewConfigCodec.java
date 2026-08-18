package com.api.generator.api.service;

import com.api.generator.auth.JwtProperties;
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
import java.util.Base64;

@Service
public class PreviewConfigCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();

    public PreviewConfigCodec(JwtProperties jwtProperties) {
        this.key = new SecretKeySpec(deriveKey(jwtProperties.secret()), "AES");
    }

    public String encode(PreviewRuntimeService.PreviewLaunchConfig config) {
        try {
            byte[] plain = OBJECT_MAPPER.writeValueAsBytes(config);
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain);

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to encrypt preview configuration", e);
        }
    }

    public PreviewRuntimeService.PreviewLaunchConfig decode(String payload) {
        try {
            if (payload == null || payload.isBlank()) {
                throw new IllegalStateException("Preview configuration payload is empty");
            }
            String trimmed = payload.trim();
            if (trimmed.startsWith("{")) {
                return OBJECT_MAPPER.readValue(trimmed, PreviewRuntimeService.PreviewLaunchConfig.class);
            }
            byte[] combined = Base64.getDecoder().decode(payload);
            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(encrypted);
            return OBJECT_MAPPER.readValue(plain, PreviewRuntimeService.PreviewLaunchConfig.class);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to decrypt preview configuration", e);
        }
    }

    private byte[] deriveKey(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest((secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to derive preview encryption key", e);
        }
    }
}
