package com.gims.module.dbsync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 매핑 설정 DTO (Manager에서 전달받는 데이터)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MappingConfigDto {

    private String moduleId;
    private String moduleName;
    private List<TableMappingDto> tableMappings;

    // 동기화 기간 정보
    private LocalDateTime syncStartDt;  // 동기화 시작 일시
    private LocalDateTime syncEndDt;    // 동기화 종료 일시

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TableMappingDto {
        private Long tableMappingId;
        private String moduleId;
        private String mappingName;
        private String sourceTable;
        private String targetTable;

        // PK 컬럼 정보 (자동 매핑 및 검증용)
        private String pkColumn;        // Source PK 컬럼
        private String targetPkColumn;  // Target에서 Source PK를 저장하는 컬럼

        // 기간 필터링용 날짜 컬럼 (Source 테이블의 날짜 컬럼명)
        private String sourceDateColumn;

        private List<ColumnMappingDto> columnMappings;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ColumnMappingDto {
        private Long mappingId;
        private String moduleId;
        private String sourceTable;
        private String sourceColumn;
        private String targetTable;
        private String targetColumn;
        private String isPrimaryKey;  // Y/N - PK 매핑 여부
    }
}
