package com.gims.module.dbsync.config;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Jasypt 암호화 설정
 * 환경변수 JASYPT_ENCRYPTOR_PASSWORD로 암호화 키를 주입받음
 *
 * 로컬 개발 시: --spring.profiles.active=dev 로 실행하면 암호화 없이 사용 가능
 * 운영 환경: JASYPT_ENCRYPTOR_PASSWORD 환경변수 필수
 */
@Configuration
@Profile("!dev")
public class JasyptConfig {

    private static final Logger log = LoggerFactory.getLogger(JasyptConfig.class);

    @Bean("jasyptStringEncryptor")
    public StringEncryptor stringEncryptor() {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();

        // 환경변수에서 암호화 키를 가져옴
        String password = System.getenv("JASYPT_ENCRYPTOR_PASSWORD");
        if (password == null || password.isEmpty()) {
            password = System.getProperty("jasypt.encryptor.password");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalStateException(
                "JASYPT_ENCRYPTOR_PASSWORD 환경변수 또는 jasypt.encryptor.password 시스템 프로퍼티가 설정되지 않았습니다."
            );
        }

        log.info("Jasypt encryptor initialized with PBEWITHHMACSHA512ANDAES_256 algorithm");

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
}
