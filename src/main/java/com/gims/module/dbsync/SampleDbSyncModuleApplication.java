package com.gims.module.dbsync;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DB Sync Sample Module Application
 *
 * 독립 실행 가능한 DB 동기화 모듈
 * 관리 시스템과 API 통신하여 실행 상태를 보고합니다
 */
@Slf4j
@SpringBootApplication
public class SampleDbSyncModuleApplication {

    public static void main(String[] args) {
        try {
            SpringApplication.run(SampleDbSyncModuleApplication.class, args);

            log.info("\n" +
                    "========================================\n" +
                    "  DB Sync Sample Module 시작 완료\n" +
                    "========================================\n" +
                    "  모듈이 관리 시스템의 요청을 대기합니다.\n" +
                    "========================================");

        } catch (Exception e) {
            log.error("모듈 시작 실패", e);
            System.exit(1);
        }
    }
}
