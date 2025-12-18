package com.gims.module.dbsync.service;

import com.gims.module.dbsync.client.ManagerApiClient;
import com.gims.module.dbsync.dto.MappingConfigDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DB 동기화 서비스 (동적 SQL 기반)
 *
 * Source DB에서 Target DB로 데이터를 동기화하며
 * 매핑 설정에 따라 동적으로 SQL을 생성하여 실행합니다.
 *
 * PK 매핑은 TableMapping의 pkColumn/targetPkColumn을 사용하여 자동 처리됩니다.
 */
@Slf4j
@Service
public class DbSyncService {

    private final ManagerApiClient managerApiClient;
    private final DataSource sourceDataSource;
    private final DataSource targetDataSource;

    public DbSyncService(
            ManagerApiClient managerApiClient,
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Qualifier("targetDataSource") DataSource targetDataSource) {
        this.managerApiClient = managerApiClient;
        this.sourceDataSource = sourceDataSource;
        this.targetDataSource = targetDataSource;
    }

    private MappingConfigDto currentMappingConfig;

    public void setMappingConfig(MappingConfigDto mappingConfig) {
        this.currentMappingConfig = mappingConfig;
        if (mappingConfig != null) {
            log.info("매핑 설정 수신: moduleId={}, 테이블 매핑 수={}",
                    mappingConfig.getModuleId(),
                    mappingConfig.getTableMappings() != null ? mappingConfig.getTableMappings().size() : 0);

            if (mappingConfig.getTableMappings() != null) {
                for (MappingConfigDto.TableMappingDto tm : mappingConfig.getTableMappings()) {
                    log.info("  - 테이블 매핑: {} -> {}, PK: {} -> {}, 컬럼 수={}",
                            tm.getSourceTable(), tm.getTargetTable(),
                            tm.getPkColumn(), tm.getTargetPkColumn(),
                            tm.getColumnMappings() != null ? tm.getColumnMappings().size() : 0);
                }
            }
        }
    }

    /**
     * DB 동기화 실행
     */
    public void executeSync(Long execId, String configJson, MappingConfigDto mappingConfig) {
        if (mappingConfig != null) {
            setMappingConfig(mappingConfig);
        }
        long startTime = System.currentTimeMillis();
        long totalProcessedCount = 0;
        long totalErrorCount = 0;
        boolean success = true;
        String errorMessage = null;

        try {
            if (currentMappingConfig == null || currentMappingConfig.getTableMappings() == null
                    || currentMappingConfig.getTableMappings().isEmpty()) {
                throw new RuntimeException("테이블 매핑 설정이 없습니다.");
            }

            List<MappingConfigDto.TableMappingDto> tableMappings = currentMappingConfig.getTableMappings();
            int tableCount = tableMappings.size();

            managerApiClient.reportProgress(execId, "동기화 시작", 5, 0L, null,
                    String.format("%d개 테이블 매핑에 대해 동기화를 시작합니다", tableCount), "INFO");

            for (int i = 0; i < tableCount; i++) {
                MappingConfigDto.TableMappingDto tableMapping = tableMappings.get(i);
                int baseProgress = 10 + (int) ((i * 1.0 / tableCount) * 80);

                try {
                    long processedCount = syncTable(execId, tableMapping, baseProgress, (int) ((80.0 / tableCount)));
                    totalProcessedCount += processedCount;

//                    log.info("테이블 동기화 완료: {} -> {}, {}건",
//                            tableMapping.getSourceTable(), tableMapping.getTargetTable(), processedCount);

                } catch (Exception e) {
                    log.error("테이블 동기화 중 오류: {} -> {}",
                            tableMapping.getSourceTable(), tableMapping.getTargetTable(), e);
                    totalErrorCount++;
                    managerApiClient.reportProgress(execId, "테이블 오류", null, totalProcessedCount, null,
                            String.format("테이블 %s -> %s 동기화 오류: %s",
                                    tableMapping.getSourceTable(), tableMapping.getTargetTable(), e.getMessage()),
                            "ERROR");
                }
            }

            managerApiClient.reportProgress(execId, "완료", 100, totalProcessedCount, null,
                    String.format("동기화 완료: %d개 테이블, 총 %d건 처리", tableCount, totalProcessedCount), "INFO");

        } catch (Exception e) {
            log.error("DB 동기화 중 오류 발생", e);
            success = false;
            errorMessage = e.getMessage();
            totalErrorCount++;

            managerApiClient.reportProgress(execId, "오류 발생", null, totalProcessedCount, null,
                    "오류: " + e.getMessage(), "ERROR");

        } finally {
            long executionTimeMs = System.currentTimeMillis() - startTime;

            String resultMessage = success
                    ? String.format("동기화 완료: 성공 %d건, 실패 %d건", totalProcessedCount, totalErrorCount)
                    : "동기화 실패";

            managerApiClient.reportExecutionComplete(
                    execId,
                    success,
                    totalProcessedCount,
                    totalErrorCount,
                    resultMessage,
                    errorMessage,
                    executionTimeMs
            );

            log.info("DB 동기화 완료: success={}, processed={}, errors={}, time={}ms",
                    success, totalProcessedCount, totalErrorCount, executionTimeMs);
        }
    }

    /**
     * 단일 테이블 동기화 (동적 SQL)
     * PK는 TableMapping의 pkColumn/targetPkColumn에서 자동으로 가져옴
     */
    private long syncTable(Long execId, MappingConfigDto.TableMappingDto tableMapping,
                           int baseProgress, int progressRange) throws SQLException {

        String sourceTable = tableMapping.getSourceTable();
        String targetTable = tableMapping.getTargetTable();
        String sourcePkColumn = tableMapping.getPkColumn();
        String targetPkColumn = tableMapping.getTargetPkColumn();
        List<MappingConfigDto.ColumnMappingDto> columnMappings = tableMapping.getColumnMappings();

        // PK 컬럼 필수 검증
        if (sourcePkColumn == null || sourcePkColumn.isEmpty()) {
            String errorMsg = String.format("Source PK 컬럼이 설정되지 않았습니다: %s -> %s", sourceTable, targetTable);
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }
        if (targetPkColumn == null || targetPkColumn.isEmpty()) {
            String errorMsg = String.format("Target PK 컬럼이 설정되지 않았습니다: %s -> %s", sourceTable, targetTable);
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }

        // 기간 필터링 정보
        String sourceDateColumn = tableMapping.getSourceDateColumn();
        LocalDateTime syncStartDt = currentMappingConfig.getSyncStartDt();
        LocalDateTime syncEndDt = currentMappingConfig.getSyncEndDt();
        boolean useDateFilter = sourceDateColumn != null && !sourceDateColumn.isEmpty()
                && syncStartDt != null && syncEndDt != null;

        log.info("테이블 동기화 시작: {} -> {}", sourceTable, targetTable);
        log.info("  PK 매핑 (자동): {} -> {}", sourcePkColumn, targetPkColumn);
        if (useDateFilter) {
            log.info("  기간 필터링: {} ({} ~ {})", sourceDateColumn, syncStartDt, syncEndDt);
        } else {
            log.info("  기간 필터링: 미적용 (전체 데이터)");
        }

        // 컬럼 매핑 로깅
        if (columnMappings != null && !columnMappings.isEmpty()) {
            log.info("  컬럼 매핑: {}개", columnMappings.size());
            for (MappingConfigDto.ColumnMappingDto cm : columnMappings) {
                log.info("    - {} -> {}", cm.getSourceColumn(), cm.getTargetColumn());
            }
        }

        String periodInfo = useDateFilter
                ? String.format(" (기간: %s ~ %s)", syncStartDt.toLocalDate(), syncEndDt.toLocalDate())
                : " (전체)";
        managerApiClient.reportProgress(execId, "테이블 조회", baseProgress, 0L, null,
                String.format("테이블 %s에서 데이터를 조회합니다%s", sourceTable, periodInfo), "INFO");

        // Source 컬럼 목록 (PK 포함)
        List<String> sourceColumns = new ArrayList<>();
        sourceColumns.add(sourcePkColumn);  // PK는 항상 첫 번째
        if (columnMappings != null) {
            for (MappingConfigDto.ColumnMappingDto cm : columnMappings) {
                if (!cm.getSourceColumn().equalsIgnoreCase(sourcePkColumn)) {
                    sourceColumns.add(cm.getSourceColumn());
                }
            }
        }

        // Target 컬럼 목록 (PK 포함)
        List<String> targetColumns = new ArrayList<>();
        targetColumns.add(targetPkColumn);  // PK는 항상 첫 번째
        if (columnMappings != null) {
            for (MappingConfigDto.ColumnMappingDto cm : columnMappings) {
                if (!cm.getTargetColumn().equalsIgnoreCase(targetPkColumn)) {
                    targetColumns.add(cm.getTargetColumn());
                }
            }
        }

        // Source -> Target 컬럼 매핑 맵 생성 (PK 포함)
        Map<String, String> columnMap = new LinkedHashMap<>();
        columnMap.put(sourcePkColumn, targetPkColumn);  // PK 매핑 추가
        if (columnMappings != null) {
            for (MappingConfigDto.ColumnMappingDto cm : columnMappings) {
                if (!cm.getSourceColumn().equalsIgnoreCase(sourcePkColumn)) {
                    columnMap.put(cm.getSourceColumn(), cm.getTargetColumn());
                }
            }
        }

        // Source에서 데이터 조회 (기간 필터링 적용)
        String selectSql = buildSelectSql(sourceTable, sourceColumns, sourceDateColumn, useDateFilter);
        log.debug("SELECT SQL: {}", selectSql);

        List<Map<String, Object>> sourceData = new ArrayList<>();
        try (Connection sourceConn = sourceDataSource.getConnection();
             PreparedStatement pstmt = sourceConn.prepareStatement(selectSql)) {

            // 기간 필터링 파라미터 바인딩
            if (useDateFilter) {
                pstmt.setTimestamp(1, Timestamp.valueOf(syncStartDt));
                pstmt.setTimestamp(2, Timestamp.valueOf(syncEndDt));
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (String col : sourceColumns) {
                        row.put(col, rs.getObject(col));
                    }
                    sourceData.add(row);
                }
            }
        }

        long totalCount = sourceData.size();
        log.info("Source 데이터 조회 완료: {} 테이블, {}건", sourceTable, totalCount);

        if (totalCount == 0) {
            return 0;
        }

        managerApiClient.reportProgress(execId, "데이터 저장", baseProgress + (progressRange / 4), 0L, totalCount,
                String.format("%s: %d건 조회 완료, Target에 저장 시작", sourceTable, totalCount), "INFO");

        // Target에 UPSERT
        long processedCount = 0;
        int batchSize = 100;

        try (Connection targetConn = targetDataSource.getConnection()) {
            targetConn.setAutoCommit(false);

            for (int i = 0; i < sourceData.size(); i += batchSize) {
                int endIdx = Math.min(i + batchSize, sourceData.size());
                List<Map<String, Object>> batch = sourceData.subList(i, endIdx);

                for (Map<String, Object> row : batch) {
                    upsertRow(targetConn, targetTable, columnMap, row, sourcePkColumn, targetPkColumn);
                }

                targetConn.commit();
                processedCount = endIdx;

                int progress = baseProgress + (int) ((processedCount * 1.0 / totalCount) * progressRange);
//                managerApiClient.reportProgress(execId, "데이터 저장", progress, processedCount, totalCount,
//                        String.format("%s: %d / %d 건 저장 중...", targetTable, processedCount, totalCount), "INFO");
            }
        }

        return processedCount;
    }

    /**
     * SELECT SQL 생성 (기간 필터링 조건 포함)
     */
    private String buildSelectSql(String tableName, List<String> columns,
                                   String dateColumn, boolean useDateFilter) {
        String columnList = String.join(", ", columns);
        StringBuilder sql = new StringBuilder();
        sql.append(String.format("SELECT %s FROM %s", columnList, tableName));

        if (useDateFilter && dateColumn != null) {
            sql.append(String.format(" WHERE %s >= ? AND %s < ?", dateColumn, dateColumn));
        }

        return sql.toString();
    }

    /**
     * UPSERT (INSERT or UPDATE)
     */
    private void upsertRow(Connection conn, String targetTable,
                           Map<String, String> columnMap,
                           Map<String, Object> sourceRow,
                           String sourcePkColumn, String targetPkColumn) throws SQLException {

        Object pkValue = sourceRow.get(sourcePkColumn);

        // 기존 데이터 존재 여부 확인
        String checkSql = String.format("SELECT 1 FROM %s WHERE %s = ?", targetTable, targetPkColumn);
        boolean exists = false;

        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setObject(1, pkValue);
            try (ResultSet rs = checkStmt.executeQuery()) {
                exists = rs.next();
            }
        }

        if (exists) {
            updateRow(conn, targetTable, columnMap, sourceRow, sourcePkColumn, targetPkColumn);
        } else {
            insertRow(conn, targetTable, columnMap, sourceRow, sourcePkColumn);
        }
    }

    /**
     * INSERT 실행
     */
    private void insertRow(Connection conn, String targetTable,
                           Map<String, String> columnMap,
                           Map<String, Object> sourceRow,
                           String sourcePkColumn) throws SQLException {

        List<String> targetColumns = new ArrayList<>(columnMap.values());
        String columnList = String.join(", ", targetColumns);
        String placeholders = targetColumns.stream().map(c -> "?").collect(Collectors.joining(", "));
        String insertSql = String.format("INSERT INTO %s (%s) VALUES (%s)", targetTable, columnList, placeholders);

        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
            int idx = 1;
            for (String sourceCol : columnMap.keySet()) {
                Object value = sourceRow.get(sourceCol);
                insertStmt.setObject(idx++, value);
            }
            insertStmt.executeUpdate();
        }
    }

    /**
     * UPDATE 실행
     */
    private void updateRow(Connection conn, String targetTable,
                           Map<String, String> columnMap,
                           Map<String, Object> sourceRow,
                           String sourcePkColumn, String targetPkColumn) throws SQLException {

        // PK를 제외한 컬럼들만 업데이트
        Map<String, String> nonPkColumns = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : columnMap.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase(sourcePkColumn)) {
                nonPkColumns.put(entry.getKey(), entry.getValue());
            }
        }

        if (nonPkColumns.isEmpty()) {
            return; // PK만 있으면 업데이트할 것이 없음
        }

        String setClause = nonPkColumns.values().stream()
                .map(col -> col + " = ?")
                .collect(Collectors.joining(", "));

        String updateSql = String.format("UPDATE %s SET %s WHERE %s = ?",
                targetTable, setClause, targetPkColumn);

        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
            int idx = 1;
            for (String sourceCol : nonPkColumns.keySet()) {
                Object value = sourceRow.get(sourceCol);
                updateStmt.setObject(idx++, value);
            }
            // WHERE 조건의 PK 값
            updateStmt.setObject(idx, sourceRow.get(sourcePkColumn));
            updateStmt.executeUpdate();
        }
    }
}
