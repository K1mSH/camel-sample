package com.gims.module.dbsync.client;

import com.gims.module.dbsync.dto.ManagerCallbackDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;

/**
 * 관리 시스템 API 클라이언트
 *
 * 실행 상태를 관리 시스템에 보고합니다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManagerApiClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${manager.callback.base-url}")
    private String managerBaseUrl;

    private String dynamicCallbackUrl;

    @Value("${module.id}")
    private String moduleId;

    @Value("${module.version}")
    private String moduleVersion;

    /**
     * 동적 콜백 URL 설정
     */
    public void setDynamicCallbackUrl(String url) {
        this.dynamicCallbackUrl = url;
    }

    /**
     * 동적 콜백 URL 초기화
     */
    public void clearDynamicCallbackUrl() {
        this.dynamicCallbackUrl = null;
    }

    /**
     * 현재 사용할 콜백 URL 반환
     */
    private String getCallbackUrl() {
        return (dynamicCallbackUrl != null && !dynamicCallbackUrl.isEmpty())
            ? dynamicCallbackUrl
            : managerBaseUrl;
    }

    /**
     * 실행 시작 보고
     */
    public Long reportExecutionStart(String execType, String execUser) {
        String url = null;
        try {
            url = getCallbackUrl() + "/execution/start";

            ManagerCallbackDto.ExecutionStartRequest request = ManagerCallbackDto.ExecutionStartRequest.builder()
                    .moduleId(moduleId)
                    .execType(execType)
                    .execUser(execUser)
                    .moduleVersion(moduleVersion)
                    .hostInfo(getHostInfo())
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<ManagerCallbackDto.ExecutionStartRequest> entity = new HttpEntity<>(request, headers);

            log.debug("매니저에 실행 시작 보고: {}", url);

            ResponseEntity<ManagerCallbackDto.ApiResponse<Long>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<ManagerCallbackDto.ApiResponse<Long>>() {}
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Long execId = response.getBody().getData();
                log.info("실행 시작 보고 성공: execId={}", execId);
                return execId;
            } else {
                log.error("실행 시작 보고 실패: HTTP {}", response.getStatusCode());
                return null;
            }

        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("매니저 연결 실패: 매니저가 실행 중이지 않거나 URL이 잘못되었습니다 ({})", url, e);
            return null;
        } catch (Exception e) {
            log.error("실행 시작 보고 중 예기치 않은 오류 발생", e);
            return null;
        }
    }

    /**
     * 진행 상황 보고
     */
    public void reportProgress(Long execId, String currentStep, Integer progressPercent,
                                Long processedCount, Long totalCount, String message, String logLevel) {
        String url = null;
        try {
            url = getCallbackUrl() + "/progress";

            ManagerCallbackDto.ExecutionProgressRequest request = ManagerCallbackDto.ExecutionProgressRequest.builder()
                    .execId(execId)
                    .moduleId(moduleId)
                    .currentStep(currentStep)
                    .progressPercent(progressPercent)
                    .processedCount(processedCount)
                    .totalCount(totalCount)
                    .message(message)
                    .logLevel(logLevel != null ? logLevel : "INFO")
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<ManagerCallbackDto.ExecutionProgressRequest> entity = new HttpEntity<>(request, headers);

            restTemplate.postForEntity(url, entity, ManagerCallbackDto.ApiResponse.class);

            log.debug("진행 상황 보고: step={}, progress={}%", currentStep, progressPercent);

        } catch (org.springframework.web.client.ResourceAccessException e) {
            // 매니저 연결 실패는 경고만 (작업은 계속 진행)
            log.warn("매니저에 진행 상황 보고 실패 - 연결 오류 (작업 계속): {}", url);
        } catch (Exception e) {
            // 기타 예외도 경고만 (작업은 계속 진행)
            log.warn("진행 상황 보고 실패 (작업 계속): {}", e.getMessage());
        }
    }

    /**
     * 실행 완료 보고
     */
    public void reportExecutionComplete(Long execId, boolean success, long processedCount,
                                         long errorCount, String resultMessage, String errorMessage,
                                         long executionTimeMs) {
        String url = null;
        try {
            url = getCallbackUrl() + "/execution/complete";

            ManagerCallbackDto.ExecutionCompleteRequest request = ManagerCallbackDto.ExecutionCompleteRequest.builder()
                    .execId(execId)
                    .moduleId(moduleId)
                    .success(success)
                    .processedCount(processedCount)
                    .errorCount(errorCount)
                    .resultMessage(resultMessage)
                    .errorMessage(errorMessage)
                    .executionTimeMs(executionTimeMs)
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<ManagerCallbackDto.ExecutionCompleteRequest> entity = new HttpEntity<>(request, headers);

            log.info("매니저에 실행 완료 보고: success={}, processed={}, errors={}", success, processedCount, errorCount);

            ResponseEntity<ManagerCallbackDto.ApiResponse> response = restTemplate.postForEntity(
                    url, entity, ManagerCallbackDto.ApiResponse.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("실행 완료 보고 성공");
            } else {
                log.warn("실행 완료 보고 응답 비정상: HTTP {}", response.getStatusCode());
            }

        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("매니저 연결 실패: 실행 완료를 보고할 수 없습니다 ({})", url, e);
        } catch (Exception e) {
            log.error("실행 완료 보고 중 예기치 않은 오류 발생", e);
        }
    }

    /**
     * 호스트 정보 조회
     */
    private String getHostInfo() {
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            return localhost.getHostName() + " (" + localhost.getHostAddress() + ")";
        } catch (Exception e) {
            return "Unknown";
        }
    }
}
