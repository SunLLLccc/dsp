package com.sunlc.dsp.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 数据源密码加解密工具
 * 使用 AES/ECB/PKCS5Padding 对称加密
 */
@Slf4j
@Component
public class PasswordEncryptor {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    @Value("${dsp.security.encrypt-key:DSP2026SecretKey4}")
    private String encryptKey;

    private SecretKeySpec keySpec;

    @PostConstruct
    public void init() {
        // AES 密钥必须为 16 字节
        byte[] keyBytes = new byte[16];
        byte[] srcBytes = encryptKey.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(srcBytes, 0, keyBytes, 0, Math.min(srcBytes.length, 16));
        this.keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * 加密密码，返回 Base64 编码的密文
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        // 如果已经是加密格式（以 ENC( 开头，以 ) 结尾），直接返回
        if (plainText.startsWith("ENC(") && plainText.endsWith(")")) {
            return plainText;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return "ENC(" + Base64.getEncoder().encodeToString(encrypted) + ")";
        } catch (Exception e) {
            log.error("密码加密失败", e);
            throw new RuntimeException("密码加密失败", e);
        }
    }

    /**
     * 解密密码，支持 ENC(base64) 格式和明文
     * 明文直接返回（兼容历史数据）
     */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }
        // 不是加密格式，当作明文直接返回（兼容历史数据）
        if (!cipherText.startsWith("ENC(") || !cipherText.endsWith(")")) {
            return cipherText;
        }
        String base64Str = cipherText.substring(4, cipherText.length() - 1);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(base64Str));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("密码解密失败", e);
            throw new RuntimeException("密码解密失败", e);
        }
    }
}
