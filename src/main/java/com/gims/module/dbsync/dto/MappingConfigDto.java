package com.gims.module.dbsync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    }
}
