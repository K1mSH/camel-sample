package com.gims.module.dbsync.service;

import com.gims.module.dbsync.client.ManagerApiClient;
import com.gims.module.dbsync.dto.MappingConfigDto;
import com.gims.module.dbsync.entity.source.SourceData;
import com.gims.module.dbsync.entity.target.TargetData;
import com.gims.module.dbsync.repository.source.SourceDataRepository;
import com.gims.module.dbsync.repository.target.TargetDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DB 동기화 서비스
 *
 * Source DB에서 Target DB로 데이터를 동기화하며
 * 각 단계별로 관리 시스템에 진행 상황을 보고합니다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DbSyncService {

    private final ManagerApiClient managerApiClient;
    private final SourceDataRepository sourceDataRepository;
    private final TargetDataRepository targetDataRepository;

    // 현재 동기화에 사용할 매핑 설정
    private MappingConfigDto currentMappingConfig;

    /**
     * 매핑 설정 저장 (실행 전에 설정)
     */
    public void setMappingConfig(MappingConfigDto mappingConfig) {
        this.currentMappingConfig = mappingConfig;
        if (mappingConfig != null) {
            log.info("매핑 설정 수신: moduleId={}, 테이블 매핑 수={}",
                    mappingConfig.getModuleId(),
                    mappingConfig.getTableMappings() != null ? mappingConfig.getTableMappings().size() : 0);

            // 매핑 상세 로깅
            if (mappingConfig.getTableMappings() != null) {
                for (MappingConfigDto.TableMappingDto tm : mappingConfig.getTableMappings()) {
                    log.info("  - 테이블 매핑: {} -> {}, 컬럼 수={}",
                            tm.getSourceTable(), tm.getTargetTable(),
                            tm.getColumnMappings() != null ? tm.getColumnMappings().size() : 0);
                }
            }
        }
    }

    /**
     * DB 동기화 실행
     */
//    @Transactional("targetTransactionManager")
//    public void executeSync(Long execId, String configJson) {
//        executeSync(execId, configJson, null);
//    }

    /**
     * DB 동기화 실행 (매핑 설정 포함)
     */
    @Transactional("targetTransactionManager")
    public void executeSync(Long execId, String configJson, MappingConfigDto mappingConfig) {
        if (mappingConfig != null) {
            setMappingConfig(mappingConfig);
        }
        long startTime = System.currentTimeMillis();
        long processedCount = 0;
        long errorCount = 0;
        boolean success = true;
        String errorMessage = null;

        try {
            // === 1단계: 데이터 조회 ===
            managerApiClient.reportProgress(execId, "데이터 조회", 10, 0L, null,
                    "Source DB에서 데이터를 조회합니다", "INFO");

            List<SourceData> sourceData = fetchSourceData();
            long totalCount = sourceData.size();

            managerApiClient.reportProgress(execId, "데이터 조회", 20, 0L, totalCount,
                    String.format("총 %d건의 데이터를 조회했습니다", totalCount), "INFO");

            // === 2단계: 데이터 변환 ===
            managerApiClient.reportProgress(execId, "데이터 변환", 40, 0L, totalCount,
                    "데이터를 Target 스키마로 변환합니다", "INFO");

            List<TargetData> transformedData = transformData(sourceData);

            managerApiClient.reportProgress(execId, "데이터 변환", 50, 0L, totalCount,
                    String.format("%d건의 데이터 변환 완료", transformedData.size()), "INFO");

            // === 3단계: 데이터 저장 (Insert or Update) ===
            managerApiClient.reportProgress(execId, "데이터 저장", 60, 0L, totalCount,
                    "Target DB에 데이터를 저장합니다", "INFO");

            // 배치로 저장
            int batchSize = 100;
            for (int i = 0; i < transformedData.size(); i += batchSize) {
                int endIdx = Math.min(i + batchSize, transformedData.size());
                List<TargetData> batch = transformedData.subList(i, endIdx);

                saveDataBatch(batch);
                processedCount = endIdx;

                int progress = 60 + (int) ((processedCount * 1.0 / totalCount) * 30); // 60~90%

                managerApiClient.reportProgress(execId, "데이터 저장", progress,
                        processedCount, totalCount,
                        String.format("%d / %d 건 저장 중...", processedCount, totalCount), "INFO");
            }

            managerApiClient.reportProgress(execId, "데이터 저장", 90, processedCount, totalCount,
                    String.format("총 %d건의 데이터 저장 완료", processedCount), "INFO");

            // === 4단계: 마무리 ===
            managerApiClient.reportProgress(execId, "완료", 100, processedCount, totalCount,
                    "동기화가 성공적으로 완료되었습니다", "INFO");

        } catch (Exception e) {
            log.error("DB 동기화 중 오류 발생", e);
            success = false;
            errorMessage = e.getMessage();
            errorCount++;

            managerApiClient.reportProgress(execId, "오류 발생", null, processedCount, null,
                    "오류: " + e.getMessage(), "ERROR");

        } finally {
            // 실행 완료 보고
            long executionTimeMs = System.currentTimeMillis() - startTime;

            String resultMessage = success
                    ? String.format("동기화 완료: 성공 %d건, 실패 %d건", processedCount, errorCount)
                    : "동기화 실패";

            managerApiClient.reportExecutionComplete(
                    execId,
                    success,
                    processedCount,
                    errorCount,
                    resultMessage,
                    errorMessage,
                    executionTimeMs
            );

            log.info("DB 동기화 완료: success={}, processed={}, errors={}, time={}ms",
                    success, processedCount, errorCount, executionTimeMs);
        }
    }

    /**
     * Source DB 데이터 조회
     */
    private List<SourceData> fetchSourceData() {
        log.info("Source DB에서 데이터 조회 중...");
        return sourceDataRepository.findAll();
    }

    /**
     * 데이터 변환 (매핑 설정 적용)
     */
    private List<TargetData> transformData(List<SourceData> sourceDataList) {
        log.info("데이터 변환 중...");

        // 매핑 설정이 있으면 매핑에 따라 변환
        if (currentMappingConfig != null && currentMappingConfig.getTableMappings() != null) {
            // source_data -> target_data 매핑 찾기
            MappingConfigDto.TableMappingDto tableMapping = currentMappingConfig.getTableMappings().stream()
                    .filter(tm -> "source_data".equalsIgnoreCase(tm.getSourceTable())
                            && "target_data".equalsIgnoreCase(tm.getTargetTable()))
                    .findFirst()
                    .orElse(null);

            if (tableMapping != null && tableMapping.getColumnMappings() != null) {
                log.info("매핑 설정 적용: {} 개의 컬럼 매핑", tableMapping.getColumnMappings().size());
                return transformDataWithMapping(sourceDataList, tableMapping);
            }
        }

        // 매핑 설정이 없으면 기본 변환 (1:1 매핑)
        log.info("기본 변환 로직 적용 (매핑 설정 없음)");
        return sourceDataList.stream()
                .map(source -> TargetData.builder()
                        .targetId(source.getId())
                        .targetName(source.getName())
                        .targetValue1(source.getValue1())
                        .targetValue2(source.getValue2())
                        .targetValue3(source.getValue3())
                        .syncDate(new Date())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 매핑 설정을 적용한 데이터 변환
     */
    private List<TargetData> transformDataWithMapping(List<SourceData> sourceDataList,
                                                       MappingConfigDto.TableMappingDto tableMapping) {
        List<MappingConfigDto.ColumnMappingDto> columnMappings = tableMapping.getColumnMappings();

        return sourceDataList.stream()
                .map(source -> {
                    TargetData.TargetDataBuilder builder = TargetData.builder();

                    // targetId는 항상 source의 id로 설정 (update를 위한 키)
                    builder.targetId(source.getId());
                    // targetName도 기본으로 source의 name 설정
                    builder.targetName(source.getName());

                    // value 컬럼들은 매핑 설정에 따라 적용
                    for (MappingConfigDto.ColumnMappingDto cm : columnMappings) {
                        String srcCol = cm.getSourceColumn().toLowerCase();
                        String tgtCol = cm.getTargetColumn().toLowerCase();

                        // value 컬럼만 매핑 적용 (id, name은 위에서 이미 설정)
                        if (srcCol.startsWith("value") && tgtCol.startsWith("target_value")) {
                            Object sourceValue = getSourceValue(source, cm.getSourceColumn());
                            setTargetValue(builder, cm.getTargetColumn(), sourceValue);
                        }
                    }

                    // syncDate는 항상 현재 시간
                    builder.syncDate(new Date());

                    return builder.build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Source 객체에서 컬럼 값 가져오기
     */
    private Object getSourceValue(SourceData source, String columnName) {
        switch (columnName.toLowerCase()) {
            case "id":
                return source.getId();
            case "name":
                return source.getName();
            case "value1":
                return source.getValue1();
            case "value2":
                return source.getValue2();
            case "value3":
                return source.getValue3();
            default:
                log.warn("알 수 없는 소스 컬럼: {}", columnName);
                return null;
        }
    }

    /**
     * Target Builder에 값 설정
     */
    private void setTargetValue(TargetData.TargetDataBuilder builder, String columnName, Object value) {
        switch (columnName.toLowerCase()) {
            case "target_id":
            case "targetid":
                if (value instanceof Long) {
                    builder.targetId((Long) value);
                } else if (value != null) {
                    builder.targetId(Long.valueOf(value.toString()));
                }
                break;
            case "target_name":
            case "targetname":
                builder.targetName(value != null ? value.toString() : null);
                break;
            case "target_value1":
            case "targetvalue1":
                if (value instanceof Double) {
                    builder.targetValue1((Double) value);
                } else if (value != null) {
                    builder.targetValue1(Double.valueOf(value.toString()));
                }
                break;
            case "target_value2":
            case "targetvalue2":
                if (value instanceof Double) {
                    builder.targetValue2((Double) value);
                } else if (value != null) {
                    builder.targetValue2(Double.valueOf(value.toString()));
                }
                break;
            case "target_value3":
            case "targetvalue3":
                if (value instanceof Double) {
                    builder.targetValue3((Double) value);
                } else if (value != null) {
                    builder.targetValue3(Double.valueOf(value.toString()));
                }
                break;
            default:
                log.warn("알 수 없는 타겟 컬럼: {}", columnName);
        }
    }

    /**
     * 값 변환 (단순 전달)
     */
    private Object applyTransform(Object sourceValue, MappingConfigDto.ColumnMappingDto mapping) {
        // 단순히 값을 그대로 반환
        return sourceValue;
    }

    /**
     * 데이터 배치 저장
     */
    private void saveDataBatch(List<TargetData> batch) {
        log.debug("배치 저장 중: {} 건", batch.size());
        targetDataRepository.saveAll(batch);
    }
}
