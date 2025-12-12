package com.gims.module.dbsync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 관리 시스템 콜백 DTO
 */
public class ManagerCallbackDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionStartRequest {
        private String moduleId;
        private String execType;
        private String execUser;
        private String moduleVersion;
        private String hostInfo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionProgressRequest {
        private Long execId;
        private String moduleId;
        private String currentStep;
        private Integer progressPercent;
        private Long processedCount;
        private Long totalCount;
        private String message;
        private String logLevel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionCompleteRequest {
        private Long execId;
        private String moduleId;
        private Boolean success;
        private Long processedCount;
        private Long errorCount;
        private String resultMessage;
        private String errorMessage;
        private Long executionTimeMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;
    }
}
