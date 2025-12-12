package com.gims.module.dbsync.controller;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gims.module.dbsync.client.ManagerApiClient;
import com.gims.module.dbsync.dto.ManagerCallbackDto;
import com.gims.module.dbsync.dto.MappingConfigDto;
import com.gims.module.dbsync.service.DbSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 모듈 실행 컨트롤러
 *
 * 관리 시스템으로부터 실행 요청을 받습니다
 */
@Slf4j
@RestController
@RequestMapping("/api/module")
@RequiredArgsConstructor
public class ModuleExecutionController {

    private final ManagerApiClient managerApiClient;
    private final DbSyncService dbSyncService;

    // 비동기 실행을 위한 스레드 풀
    private final ExecutorService executorService = Executors.newFixedThreadPool(3);

    private final ObjectMapper objectMapper = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * 동기화 실행 요청 (관리 시스템에서 호출)
     */
    @PostMapping("/execute")
    public ResponseEntity<ManagerCallbackDto.ApiResponse<String>> execute(@RequestBody Map<String, Object> request) {

        log.info("=== 동기화 실행 요청 수신 ===");
        log.info("요청 데이터: {}", request);

        try {
            // 요청 파라미터 추출
            Long execId = request.get("execId") != null ? Long.valueOf(request.get("execId").toString()) : null;
            String moduleId = (String) request.get("moduleId");
            String configJson = (String) request.get("configJson");
            String callbackUrl = (String) request.get("callbackUrl");

            // 매핑 설정 추출
            MappingConfigDto mappingConfig = null;
            Object mappingConfigRaw = request.get("mappingConfig");
            if (mappingConfigRaw != null) {
                try {
                    log.info("매핑 설정 원본 타입: {}", mappingConfigRaw.getClass().getName());
                    log.info("매핑 설정 원본 데이터: {}", objectMapper.writeValueAsString(mappingConfigRaw));

                    mappingConfig = objectMapper.convertValue(mappingConfigRaw, MappingConfigDto.class);

                    log.info("매핑 설정 수신 성공: moduleId={}, 테이블 매핑 수={}",
                            mappingConfig.getModuleId(),
                            mappingConfig.getTableMappings() != null ? mappingConfig.getTableMappings().size() : 0);

                    // 상세 매핑 로깅
                    if (mappingConfig.getTableMappings() != null) {
                        for (MappingConfigDto.TableMappingDto tm : mappingConfig.getTableMappings()) {
                            log.info("  테이블: {} -> {}, 컬럼 수={}",
                                    tm.getSourceTable(), tm.getTargetTable(),
                                    tm.getColumnMappings() != null ? tm.getColumnMappings().size() : 0);
                            if (tm.getColumnMappings() != null) {
                                for (MappingConfigDto.ColumnMappingDto cm : tm.getColumnMappings()) {
                                    log.info("    컬럼: {} -> {}", cm.getSourceColumn(), cm.getTargetColumn());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("매핑 설정 파싱 실패, 기본 변환 사용", e);
                }
            } else {
                log.info("매핑 설정 없음 (null), 기본 변환 사용");
            }

            // callbackUrl이 제공되면 동적 URL 사용, 없으면 기본 URL 사용
            if (callbackUrl != null && !callbackUrl.isEmpty()) {
                managerApiClient.setDynamicCallbackUrl(callbackUrl);
                log.info("동적 콜백 URL 설정: {}", callbackUrl);
            }

            // execId가 없으면 관리 시스템에 실행 시작 보고
            if (execId == null) {
                execId = managerApiClient.reportExecutionStart("AUTO", null);
                if (execId == null) {
                    return ResponseEntity.status(500).body(
                            ManagerCallbackDto.ApiResponse.<String>builder()
                                    .success(false)
                                    .message("실행 시작 보고 실패")
                                    .build()
                    );
                }
            }

            // 비동기로 동기화 작업 실행
            Long finalExecId = execId;
            MappingConfigDto finalMappingConfig = mappingConfig;
            executorService.submit(() -> {
                try {
                    log.info("동기화 작업 시작: execId={}", finalExecId);
                    dbSyncService.executeSync(finalExecId, configJson, finalMappingConfig);
                } catch (Exception e) {
                    log.error("동기화 작업 중 예외 발생", e);
                } finally {
                    // 동적 URL 초기화
                    managerApiClient.clearDynamicCallbackUrl();
                }
            });

            return ResponseEntity.ok(
                    ManagerCallbackDto.ApiResponse.<String>builder()
                            .success(true)
                            .message("동기화 작업이 시작되었습니다")
                            .data("execId: " + finalExecId)
                            .build()
            );

        } catch (Exception e) {
            log.error("실행 요청 처리 중 오류 발생", e);
            return ResponseEntity.status(500).body(
                    ManagerCallbackDto.ApiResponse.<String>builder()
                            .success(false)
                            .message("실행 요청 처리 실패: " + e.getMessage())
                            .build()
            );
        }
    }

    /**
     * 모듈 상태 조회
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new java.util.HashMap<>();
        status.put("status", "RUNNING");
        status.put("activeThreads", ((java.util.concurrent.ThreadPoolExecutor) executorService).getActiveCount());
        status.put("queueSize", ((java.util.concurrent.ThreadPoolExecutor) executorService).getQueue().size());

        return ResponseEntity.ok(status);
    }
}
