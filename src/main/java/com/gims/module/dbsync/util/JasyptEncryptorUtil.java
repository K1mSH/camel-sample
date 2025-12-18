package com.gims.module.dbsync.util;

import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;

/**
 * Jasypt 암호화/복호화 유틸리티
 *
 * 사용법:
 *   java -cp "build/libs/*:build/classes/java/main" com.gims.module.dbsync.util.JasyptEncryptorUtil encrypt [암호화키] [평문]
 *   java -cp "build/libs/*:build/classes/java/main" com.gims.module.dbsync.util.JasyptEncryptorUtil decrypt [암호화키] [암호문]
 *
 * Windows:
 *   java -cp "build\libs\*;build\classes\java\main" com.gims.module.dbsync.util.JasyptEncryptorUtil encrypt [암호화키] [평문]
 */
public class JasyptEncryptorUtil {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("사용법:");
            System.out.println("  암호화: java JasyptEncryptorUtil encrypt [암호화키] [평문]");
            System.out.println("  복호화: java JasyptEncryptorUtil decrypt [암호화키] [암호문]");
            System.out.println();
            System.out.println("예시:");
            System.out.println("  java JasyptEncryptorUtil encrypt mySecretKey myPassword");
            return;
        }

        String operation = args[0];
        String encryptorPassword = args[1];
        String input = args[2];

        PooledPBEStringEncryptor encryptor = createEncryptor(encryptorPassword);

        if ("encrypt".equalsIgnoreCase(operation)) {
            String encrypted = encryptor.encrypt(input);
            System.out.println("===========================================");
            System.out.println("원본: " + input);
            System.out.println("암호화된 값: " + encrypted);
            System.out.println("properties 설정값: ENC(" + encrypted + ")");
            System.out.println("===========================================");
        } else if ("decrypt".equalsIgnoreCase(operation)) {
            String decrypted = encryptor.decrypt(input);
            System.out.println("===========================================");
            System.out.println("암호문: " + input);
            System.out.println("복호화된 값: " + decrypted);
            System.out.println("===========================================");
        } else {
            System.out.println("알 수 없는 명령어: " + operation);
            System.out.println("'encrypt' 또는 'decrypt'를 사용하세요.");
        }
    }

    public static PooledPBEStringEncryptor createEncryptor(String password) {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();

        config.setPassword(password);
        config.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
        config.setKeyObtentionIterations("1000");
        config.setPoolSize("1");
        config.setProviderName("SunJCE");
        config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
        config.setIvGeneratorClassName("org.jasypt.iv.RandomIvGenerator");
        config.setStringOutputType("base64");

        encryptor.setConfig(config);
        return encryptor;
    }

    /**
     * 프로그래밍 방식으로 암호화
     */
    public static String encrypt(String password, String plainText) {
        return createEncryptor(password).encrypt(plainText);
    }

    /**
     * 프로그래밍 방식으로 복호화
     */
    public static String decrypt(String password, String encryptedText) {
        return createEncryptor(password).decrypt(encryptedText);
    }
}
