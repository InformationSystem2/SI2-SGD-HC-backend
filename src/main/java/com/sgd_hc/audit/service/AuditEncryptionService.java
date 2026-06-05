package com.sgd_hc.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
public class AuditEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256;
    private static final int IV_SIZE = 12;
    private static final int TAG_SIZE = 128;

    @Value("${audit.encryption.key}")
    private String encryptionKey;

    private SecretKey key;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getDecoder().decode(encryptionKey);
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public byte[] encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = generateIV();
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_SIZE, iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(IV_SIZE + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return buffer.array();
        } catch (Exception e) {
            log.error("Error encrypting audit data", e);
            throw new RuntimeException("Error encrypting audit data", e);
        }
    }

    public String decrypt(byte[] encrypted) {
        if (encrypted == null) return null;
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encrypted);

            byte[] iv = new byte[IV_SIZE];
            buffer.get(iv);

            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_SIZE, iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Error decrypting audit data", e);
            throw new RuntimeException("Error decrypting audit data", e);
        }
    }

    public byte[] encryptMap(Map<String, Object> data) {
        if (data == null) return null;
        return encrypt(toJson(com.sgd_hc.audit.util.AuditoriaUtils.sanitizeMap(data)));
    }

    public Map<String, Object> decryptMap(byte[] encrypted) {
        if (encrypted == null) return null;
        try {
            String json = decrypt(encrypted);
            if (json == null) return null;
            return fromJson(json);
        } catch (RuntimeException e) {
            log.warn("Error decrypting audit field, returning null: {}", e.getMessage());
            return null;
        }
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing to JSON", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error deserializing JSON", e);
        }
    }

    private byte[] generateIV() {
        byte[] iv = new byte[IV_SIZE];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
}