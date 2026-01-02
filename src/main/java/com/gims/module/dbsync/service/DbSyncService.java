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
 * DB 동기화 서비스 (동적 SQL 기반) - 2단계 동기화
 *
 * 동기화 흐름:
 * 1단계: Source DB → Copy 테이블 (Target DB 내, Source와 동일 구조)
 * 2단계: Copy 테이블 → Target 테이블 (매핑 적용)
 *
 * Copy 테이블: "copy_" + sourceTable (예: copy_source_data)
 * Target DB에 생성되며, Source 테이블과 동일한 컬럼 구조 + copy_at 컬럼
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

            // 동기화 기간 정보 로깅
            log.info("  동기화 기간: {} ~ {}", mappingConfig.getSyncStartDt(), mappingConfig.getSyncEndDt());

            if (mappingConfig.getTableMappings() != null) {
                for (MappingConfigDto.TableMappingDto tm : mappingConfig.getTableMappings()) {
                    log.info("  - 테이블 매핑: {} -> {}, PK: {} -> {}, 날짜컬럼: {}, 컬럼 수={}",
                            tm.getSourceTable(), tm.getTargetTable(),
                            tm.getPkColumn(), tm.getTargetPkColumn(),
                            tm.getSourceDateColumn(),
                            tm.getColumnMappings() != null ? tm.getColumnMappings().size() : 0);
                }
            }
        }
    }

    /**
     * DB 동기화 실행 (기본 DataSource 사용)
     */
    public void executeSync(Long execId, String configJson, MappingConfigDto mappingConfig) {
        executeSync(execId, configJson, mappingConfig, sourceDataSource, targetDataSource);
    }

    /**
     * DB 동기화 실행 (동적 DataSource 사용)
     * 관리 시스템에서 전달받은 DB 연결 정보로 생성된 DataSource 사용
     */
    public void executeSync(Long execId, String configJson, MappingConfigDto mappingConfig,
                            DataSource dynamicSourceDs, DataSource dynamicTargetDs) {
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
                    long processedCount = syncTable(execId, tableMapping, baseProgress, (int) ((80.0 / tableCount)),
                            dynamicSourceDs, dynamicTargetDs);
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
     * 단일 테이블 동기화 (2단계)
     * 1단계: Source → Copy 테이블 (동일 구조 복사)
     * 2단계: Copy → Target 테이블 (매핑 적용)
     */
    private long syncTable(Long execId, MappingConfigDto.TableMappingDto tableMapping,
                           int baseProgress, int progressRange,
                           DataSource srcDataSource, DataSource tgtDataSource) throws SQLException {

        String sourceTable = tableMapping.getSourceTable();
        String targetTable = tableMapping.getTargetTable();
        String sourcePkColumn = tableMapping.getPkColumn();
        String targetPkColumn = tableMapping.getTargetPkColumn();
        List<MappingConfigDto.ColumnMappingDto> columnMappings = tableMapping.getColumnMappings();

        // Copy 테이블 이름 생성: "copy_" + sourceTable
        String copyTable = "copy_" + sourceTable;

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

        // sourceDateColumn이 설정되어 있지 않으면 자동 감지 시도
        if ((sourceDateColumn == null || sourceDateColumn.isEmpty()) && syncStartDt != null && syncEndDt != null) {
            sourceDateColumn = detectSourceDateColumn(srcDataSource, sourceTable);
            if (sourceDateColumn != null) {
                log.info("Source 날짜 컬럼 자동 감지됨: {}", sourceDateColumn);
            }
        }

        boolean useDateFilter = sourceDateColumn != null && !sourceDateColumn.isEmpty()
                && syncStartDt != null && syncEndDt != null;

        // 기간 지정 동기화 실행 여부 (is_updated 값 결정용)
        boolean isManualDateRange = currentMappingConfig.getIsManualDateRange() != null
                && currentMappingConfig.getIsManualDateRange();

        log.info("========================================");
        log.info("테이블 동기화 시작 (2단계 구조)");
        log.info("  Source: {} -> Copy: {} -> Target: {}", sourceTable, copyTable, targetTable);
        log.info("  PK: {} -> {}", sourcePkColumn, targetPkColumn);
        log.info("  동기화 기간: {} ~ {}", syncStartDt, syncEndDt);
        log.info("  기간지정실행: {} (is_updated = {})", isManualDateRange, isManualDateRange);
        if (useDateFilter) {
            log.info("  기간 필터링: {} 컬럼 기준", sourceDateColumn);
        }
        log.info("========================================");

        // ========== 1단계: Source → Copy ==========
        log.info("[1단계] Source → Copy 시작");
        managerApiClient.reportProgress(execId, "1단계: Source→Copy", baseProgress, 0L, null,
                String.format("Source %s 에서 Copy %s 로 데이터를 복사합니다", sourceTable, copyTable), "INFO");

        long copyCount = syncSourceToCopy(execId, srcDataSource, tgtDataSource,
                sourceTable, copyTable, sourcePkColumn, sourceDateColumn,
                syncStartDt, syncEndDt, useDateFilter, isManualDateRange);

        if (copyCount == 0) {
            log.info("[1단계] 복사할 데이터가 없습니다.");
            return 0;
        }
        log.info("[1단계] Source → Copy 완료: {}건", copyCount);

        // ========== 2단계: Copy → Target ==========
        log.info("[2단계] Copy → Target 시작");
        managerApiClient.reportProgress(execId, "2단계: Copy→Target",
                baseProgress + (progressRange / 2), copyCount, null,
                String.format("Copy %s 에서 Target %s 로 매핑 적용하여 저장합니다", copyTable, targetTable), "INFO");

        long targetCount = syncCopyToTarget(execId, tgtDataSource,
                copyTable, targetTable, sourcePkColumn, targetPkColumn,
                columnMappings, sourceDateColumn, isManualDateRange);

        log.info("[2단계] Copy → Target 완료: {}건", targetCount);

        return targetCount;
    }

    /**
     * 1단계: Source → Copy (동일 구조 복사)
     * Source 테이블의 모든 컬럼을 그대로 Copy 테이블에 복사
     * source_uk 생성: {sourceTable}_{sourcePK}
     * @param isManualDateRange 기간 지정 동기화 실행 여부 (is_updated 값 결정용)
     */
    private long syncSourceToCopy(Long execId, DataSource srcDataSource, DataSource tgtDataSource,
                                   String sourceTable, String copyTable, String sourcePkColumn,
                                   String sourceDateColumn, LocalDateTime syncStartDt, LocalDateTime syncEndDt,
                                   boolean useDateFilter, boolean isManualDateRange) throws SQLException {

        // Source 테이블의 모든 컬럼 조회
        List<String> sourceColumns = getAllColumns(srcDataSource, sourceTable);
        if (sourceColumns.isEmpty()) {
            throw new RuntimeException("Source 테이블 컬럼을 조회할 수 없습니다: " + sourceTable);
        }

        log.info("  Source 컬럼: {}", sourceColumns);

        // Source에서 데이터 조회 (기간 필터링 적용)
        String selectSql = buildSelectSql(sourceTable, sourceColumns, sourceDateColumn, useDateFilter);
        log.debug("  SELECT SQL: {}", selectSql);

        List<Map<String, Object>> sourceData = new ArrayList<>();
        try (Connection sourceConn = srcDataSource.getConnection();
             PreparedStatement pstmt = sourceConn.prepareStatement(selectSql)) {

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
                    // source_uk 생성: {sourceTable}_{sourcePK}
                    Object pkValue = row.get(sourcePkColumn);
                    String sourceUk = sourceTable + "_" + pkValue;
                    row.put("source_uk", sourceUk);
                    sourceData.add(row);
                }
            }
        }

        log.info("  Source 데이터 조회 완료: {}건 (source_uk 생성됨)", sourceData.size());

        if (sourceData.isEmpty()) {
            return 0;
        }

        // Copy 테이블에 UPSERT (동일 구조이므로 컬럼명 그대로 사용 + source_uk 추가)
        Map<String, String> copyColumnMap = new LinkedHashMap<>();
        copyColumnMap.put("source_uk", "source_uk");  // source_uk 먼저 추가
        for (String col : sourceColumns) {
            copyColumnMap.put(col, col);  // Source 컬럼 = Copy 컬럼 (동일)
        }

        long processedCount = 0;
        int batchSize = 100;

        try (Connection targetConn = tgtDataSource.getConnection()) {
            targetConn.setAutoCommit(false);

            for (int i = 0; i < sourceData.size(); i += batchSize) {
                int endIdx = Math.min(i + batchSize, sourceData.size());
                List<Map<String, Object>> batch = sourceData.subList(i, endIdx);

                for (Map<String, Object> row : batch) {
                    upsertRowToCopy(targetConn, copyTable, copyColumnMap, row, isManualDateRange);
                }

                targetConn.commit();
                processedCount = endIdx;
            }
        }

        return processedCount;
    }

    /**
     * 2단계: Copy → Target (매핑 적용)
     * Copy 테이블에서 데이터를 읽어 Target 테이블에 매핑하여 저장
     * source_uk를 사용하여 UPSERT
     */
    /**
     * @param isManualDateRange 기간 지정 동기화 실행 여부 (is_updated 값 결정용)
     */
    private long syncCopyToTarget(Long execId, DataSource tgtDataSource,
                                   String copyTable, String targetTable,
                                   String sourcePkColumn, String targetPkColumn,
                                   List<MappingConfigDto.ColumnMappingDto> columnMappings,
                                   String sourceDateColumn, boolean isManualDateRange) throws SQLException {

        // Copy 테이블에서 컬럼 목록 구성 (source_uk 필수 포함)
        List<String> copyColumns = new ArrayList<>();
        copyColumns.add("source_uk");  // source_uk 필수
        copyColumns.add(sourcePkColumn);
        if (columnMappings != null) {
            for (MappingConfigDto.ColumnMappingDto cm : columnMappings) {
                if (!cm.getSourceColumn().equalsIgnoreCase(sourcePkColumn)
                        && !cm.getSourceColumn().equalsIgnoreCase("source_uk")
                        && !copyColumns.contains(cm.getSourceColumn())) {
                    copyColumns.add(cm.getSourceColumn());
                }
            }
        }

        // Copy → Target 컬럼 매핑 맵 생성 (source_uk는 그대로)
        Map<String, String> columnMap = new LinkedHashMap<>();
        columnMap.put("source_uk", "source_uk");  // source_uk는 그대로 매핑
        // PK는 매핑에서 제외 (Target에 source의 PK 컬럼은 저장 안 함)
        if (columnMappings != null) {
            for (MappingConfigDto.ColumnMappingDto cm : columnMappings) {
                if (!cm.getSourceColumn().equalsIgnoreCase(sourcePkColumn)
                        && !cm.getSourceColumn().equalsIgnoreCase("source_uk")) {
                    columnMap.put(cm.getSourceColumn(), cm.getTargetColumn());
                }
            }
        }

        // 날짜 컬럼 자동 매핑 (created_at -> source_created_at 등)
        if (sourceDateColumn != null && !sourceDateColumn.isEmpty()
                && !columnMap.containsKey(sourceDateColumn)) {
            String targetDateColumn = detectTargetDateColumn(tgtDataSource, targetTable, sourceDateColumn);
            if (targetDateColumn != null) {
                if (!copyColumns.contains(sourceDateColumn)) {
                    copyColumns.add(sourceDateColumn);
                }
                columnMap.put(sourceDateColumn, targetDateColumn);
                log.info("  날짜 컬럼 자동 매핑: {} -> {}", sourceDateColumn, targetDateColumn);
            }
        }

        // 모든 날짜 컬럼 자동 매핑
        Map<String, String> autoDateMappings = detectAllDateColumnMappings(
                tgtDataSource, tgtDataSource, copyTable, targetTable, new ArrayList<>(columnMap.keySet()));
        for (Map.Entry<String, String> entry : autoDateMappings.entrySet()) {
            if (!columnMap.containsKey(entry.getKey())) {
                if (!copyColumns.contains(entry.getKey())) {
                    copyColumns.add(entry.getKey());
                }
                columnMap.put(entry.getKey(), entry.getValue());
            }
        }

        log.info("  Copy → Target 컬럼 매핑:");
        for (Map.Entry<String, String> entry : columnMap.entrySet()) {
            log.info("    {} -> {}", entry.getKey(), entry.getValue());
        }

        // Copy 테이블에서 데이터 조회 (전체 - 이미 1단계에서 필터링됨)
        String selectSql = "SELECT " + String.join(", ", copyColumns) + " FROM " + copyTable;
        log.debug("  SELECT SQL: {}", selectSql);

        List<Map<String, Object>> copyData = new ArrayList<>();
        try (Connection conn = tgtDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(selectSql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (String col : copyColumns) {
                    row.put(col, rs.getObject(col));
                }
                copyData.add(row);
            }
        }

        log.info("  Copy 테이블 데이터 조회 완료: {}건", copyData.size());

        if (copyData.isEmpty()) {
            return 0;
        }

        // Target 테이블에 UPSERT (source_uk 기준)
        long processedCount = 0;
        int batchSize = 100;

        try (Connection targetConn = tgtDataSource.getConnection()) {
            targetConn.setAutoCommit(false);

            for (int i = 0; i < copyData.size(); i += batchSize) {
                int endIdx = Math.min(i + batchSize, copyData.size());
                List<Map<String, Object>> batch = copyData.subList(i, endIdx);

                for (Map<String, Object> row : batch) {
                    upsertRowToTarget(targetConn, targetTable, columnMap, row, isManualDateRange);
                }

                targetConn.commit();
                processedCount = endIdx;
            }
        }

        return processedCount;
    }

    /**
     * Copy 테이블에 UPSERT (source_uk 기준, copy_at 컬럼 자동 설정)
     * @param isManualDateRange 기간 지정 동기화 실행 여부 (true=기간지정, false=기본실행)
     */
    private void upsertRowToCopy(Connection conn, String copyTable,
                                  Map<String, String> columnMap,
                                  Map<String, Object> sourceRow, boolean isManualDateRange) throws SQLException {

        String sourceUk = (String) sourceRow.get("source_uk");

        // source_uk로 기존 데이터 존재 여부 확인
        String checkSql = String.format("SELECT 1 FROM %s WHERE source_uk = ?", copyTable);
        boolean exists = false;

        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setString(1, sourceUk);
            try (ResultSet rs = checkStmt.executeQuery()) {
                exists = rs.next();
            }
        }

        if (exists) {
            updateRowInCopy(conn, copyTable, columnMap, sourceRow, isManualDateRange);
        } else {
            insertRowToCopy(conn, copyTable, columnMap, sourceRow, isManualDateRange);
        }
    }

    /**
     * Copy 테이블에 INSERT (copy_at, is_updated 컬럼 자동 추가)
     * @param isManualDateRange 기간 지정 동기화 실행 여부 (true=기간지정, false=기본실행)
     */
    private void insertRowToCopy(Connection conn, String copyTable,
                                  Map<String, String> columnMap,
                                  Map<String, Object> sourceRow, boolean isManualDateRange) throws SQLException {

        List<String> columns = new ArrayList<>(columnMap.values());

        // copy_at 컬럼 추가 (있으면)
        String copyAtColumn = detectCopyAtColumn(conn, copyTable, columns);
        boolean hasCopyAt = copyAtColumn != null;
        if (hasCopyAt) {
            columns.add(copyAtColumn);
        }

        // is_updated 컬럼 추가
        columns.add("is_updated");

        String columnList = String.join(", ", columns);
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        String insertSql = String.format("INSERT INTO %s (%s) VALUES (%s)", copyTable, columnList, placeholders);

        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
            int idx = 1;
            for (String sourceCol : columnMap.keySet()) {
                Object value = sourceRow.get(sourceCol);
                insertStmt.setObject(idx++, value);
            }
            if (hasCopyAt) {
                insertStmt.setTimestamp(idx++, Timestamp.valueOf(LocalDateTime.now()));
            }
            // is_updated: 기간지정=true, 기본실행=false
            insertStmt.setBoolean(idx, isManualDateRange);
            insertStmt.executeUpdate();
        }
    }

    /**
     * Copy 테이블 UPDATE (source_uk 기준, copy_at, is_updated 컬럼 자동 업데이트)
     * @param isManualDateRange 기간 지정 동기화 실행 여부 (true=기간지정, false=기본실행)
     */
    private void updateRowInCopy(Connection conn, String copyTable,
                                  Map<String, String> columnMap,
                                  Map<String, Object> sourceRow, boolean isManualDateRange) throws SQLException {

        // source_uk를 제외한 컬럼들만 업데이트
        Map<String, String> nonUkColumns = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : columnMap.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase("source_uk")) {
                nonUkColumns.put(entry.getKey(), entry.getValue());
            }
        }

        List<String> existingCols = new ArrayList<>(nonUkColumns.values());
        existingCols.add("source_uk");
        String copyAtColumn = detectCopyAtColumn(conn, copyTable, existingCols);
        boolean hasCopyAt = copyAtColumn != null;

        StringBuilder setClauseBuilder = new StringBuilder();
        if (!nonUkColumns.isEmpty()) {
            setClauseBuilder.append(nonUkColumns.values().stream()
                    .map(col -> col + " = ?")
                    .collect(Collectors.joining(", ")));
        }
        if (hasCopyAt) {
            if (setClauseBuilder.length() > 0) {
                setClauseBuilder.append(", ");
            }
            setClauseBuilder.append(copyAtColumn).append(" = ?");
        }
        // is_updated 컬럼 추가
        if (setClauseBuilder.length() > 0) {
            setClauseBuilder.append(", ");
        }
        setClauseBuilder.append("is_updated = ?");

        String updateSql = String.format("UPDATE %s SET %s WHERE source_uk = ?",
                copyTable, setClauseBuilder.toString());

        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
            int idx = 1;
            for (String sourceCol : nonUkColumns.keySet()) {
                Object value = sourceRow.get(sourceCol);
                updateStmt.setObject(idx++, value);
            }
            if (hasCopyAt) {
                updateStmt.setTimestamp(idx++, Timestamp.valueOf(LocalDateTime.now()));
            }
            // is_updated: 기간지정=true, 기본실행=false
            updateStmt.setBoolean(idx++, isManualDateRange);
            updateStmt.setString(idx, (String) sourceRow.get("source_uk"));
            updateStmt.executeUpdate();
        }
    }

    /**
     * Target 테이블에 UPSERT (source_uk 기준)
     * @param isManualDateRange 기간 지정 동기화 실행 여부 (true=기간지정, false=기본실행)
     */
    private void upsertRowToTarget(Connection conn, String targetTable,
                                    Map<String, String> columnMap,
                                    Map<String, Object> sourceRow, boolean isManualDateRange) throws SQLException {

        String sourceUk = (String) sourceRow.get("source_uk");

        // source_uk로 기존 데이터 존재 여부 확인
        String checkSql = String.format("SELECT 1 FROM %s WHERE source_uk = ?", targetTable);
        boolean exists = false;

        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setString(1, sourceUk);
            try (ResultSet rs = checkStmt.executeQuery()) {
                exists = rs.next();
            }
        }

        if (exists) {
            updateRowInTarget(conn, targetTable, columnMap, sourceRow, isManualDateRange);
        } else {
            insertRowToTarget(conn, targetTable, columnMap, sourceRow, isManualDateRange);
        }
    }

    /**
     * Target 테이블에 INSERT (sync 타임스탬프, is_updated 자동 추가)
     * @param isManualDateRange 기간 지정 동기화 실행 여부 (true=기간지정, false=기본실행)
     */
    private void insertRowToTarget(Connection conn, String targetTable,
                                    Map<String, String> columnMap,
                                    Map<String, Object> sourceRow, boolean isManualDateRange) throws SQLException {

        List<String> targetColumns = new ArrayList<>(columnMap.values());

        // sync 타임스탬프 컬럼 자동 추가
        String syncColumn = detectSyncTimestampColumn(conn, targetTable, targetColumns);
        boolean hasSyncColumn = syncColumn != null;
        if (hasSyncColumn) {
            targetColumns.add(syncColumn);
        }

        // is_updated 컬럼 추가
        targetColumns.add("is_updated");

        String columnList = String.join(", ", targetColumns);
        String placeholders = targetColumns.stream().map(c -> "?").collect(Collectors.joining(", "));
        String insertSql = String.format("INSERT INTO %s (%s) VALUES (%s)", targetTable, columnList, placeholders);

        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
            int idx = 1;
            for (String sourceCol : columnMap.keySet()) {
                Object value = sourceRow.get(sourceCol);
                insertStmt.setObject(idx++, value);
            }
            if (hasSyncColumn) {
                insertStmt.setTimestamp(idx++, Timestamp.valueOf(LocalDateTime.now()));
            }
            // is_updated: 기간지정=true, 기본실행=false
            insertStmt.setBoolean(idx, isManualDateRange);
            insertStmt.executeUpdate();
        }
    }

    /**
     * Target 테이블 UPDATE (source_uk 기준, sync 타임스탬프, is_updated 자동 업데이트)
     * @param isManualDateRange 기간 지정 동기화 실행 여부 (true=기간지정, false=기본실행)
     */
    private void updateRowInTarget(Connection conn, String targetTable,
                                    Map<String, String> columnMap,
                                    Map<String, Object> sourceRow, boolean isManualDateRange) throws SQLException {

        // source_uk를 제외한 컬럼들만 업데이트
        Map<String, String> nonUkColumns = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : columnMap.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase("source_uk")) {
                nonUkColumns.put(entry.getKey(), entry.getValue());
            }
        }

        List<String> existingTargetCols = new ArrayList<>(nonUkColumns.values());
        existingTargetCols.add("source_uk");
        String syncColumn = detectSyncTimestampColumn(conn, targetTable, existingTargetCols);
        boolean hasSyncColumn = syncColumn != null;

        StringBuilder setClauseBuilder = new StringBuilder();
        if (!nonUkColumns.isEmpty()) {
            setClauseBuilder.append(nonUkColumns.values().stream()
                    .map(col -> col + " = ?")
                    .collect(Collectors.joining(", ")));
        }
        if (hasSyncColumn) {
            if (setClauseBuilder.length() > 0) {
                setClauseBuilder.append(", ");
            }
            setClauseBuilder.append(syncColumn).append(" = ?");
        }
        // is_updated 컬럼 추가
        if (setClauseBuilder.length() > 0) {
            setClauseBuilder.append(", ");
        }
        setClauseBuilder.append("is_updated = ?");

        String updateSql = String.format("UPDATE %s SET %s WHERE source_uk = ?",
                targetTable, setClauseBuilder.toString());

        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
            int idx = 1;
            for (String sourceCol : nonUkColumns.keySet()) {
                Object value = sourceRow.get(sourceCol);
                updateStmt.setObject(idx++, value);
            }
            if (hasSyncColumn) {
                updateStmt.setTimestamp(idx++, Timestamp.valueOf(LocalDateTime.now()));
            }
            // is_updated: 기간지정=true, 기본실행=false
            updateStmt.setBoolean(idx++, isManualDateRange);
            updateStmt.setString(idx, (String) sourceRow.get("source_uk"));
            updateStmt.executeUpdate();
        }
    }

    /**
     * copy_at 컬럼 감지
     */
    private String detectCopyAtColumn(Connection conn, String table, List<String> existingColumns) {
        String[] copyAtPatterns = {"copy_at", "copied_at", "copy_date", "copy_timestamp"};

        try {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet columns = metaData.getColumns(null, null, table, null)) {
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME").toLowerCase();
                    boolean alreadyMapped = existingColumns.stream()
                            .anyMatch(c -> c.equalsIgnoreCase(columnName));
                    if (alreadyMapped) {
                        continue;
                    }
                    for (String pattern : copyAtPatterns) {
                        if (columnName.equals(pattern)) {
                            return columnName;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.debug("copy_at 컬럼 감지 실패 (무시): {}", e.getMessage());
        }
        return null;
    }

    /**
     * 테이블의 모든 컬럼 목록 조회
     */
    private List<String> getAllColumns(DataSource dataSource, String tableName) {
        List<String> columns = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, null, tableName, null)) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME").toLowerCase());
                }
            }
        } catch (SQLException e) {
            log.error("테이블 컬럼 조회 실패: {}", tableName, e);
        }
        return columns;
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
     * Target 테이블에서 sync 타임스탬프 컬럼 감지
     * 이미 매핑에 포함된 컬럼은 제외
     */
    private String detectSyncTimestampColumn(Connection conn, String targetTable, List<String> existingColumns) {
        // 일반적인 sync 타임스탬프 컬럼명 패턴
        String[] syncColumnPatterns = {"sync_date", "sync_timestamp", "sync_at", "synced_at", "last_sync"};

        try {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet columns = metaData.getColumns(null, null, targetTable, null)) {
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME").toLowerCase();
                    // 이미 매핑에 있으면 건너뜀
                    boolean alreadyMapped = existingColumns.stream()
                            .anyMatch(c -> c.equalsIgnoreCase(columnName));
                    if (alreadyMapped) {
                        continue;
                    }
                    // sync 타임스탬프 컬럼인지 확인
                    for (String pattern : syncColumnPatterns) {
                        if (columnName.equals(pattern)) {
//                            log.debug("Sync 타임스탬프 컬럼 감지: {} in {}", columnName, targetTable);
                            return columnName;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.debug("테이블 메타데이터 조회 실패 (무시): {}", e.getMessage());
        }
        return null;
    }

    /**
     * Source 날짜 컬럼에 대응하는 Target 컬럼 감지
     * 예: created_at -> source_created_at, updated_at -> source_updated_at
     */
    private String detectTargetDateColumn(DataSource targetDataSource, String targetTable, String sourceDateColumn) {
        // 대응 패턴: source_{컬럼명}, {컬럼명}
        String[] patterns = {
            "source_" + sourceDateColumn,       // source_created_at
            sourceDateColumn                     // created_at (동일 이름)
        };

        try (Connection conn = targetDataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet columns = metaData.getColumns(null, null, targetTable, null)) {
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME").toLowerCase();
                    for (String pattern : patterns) {
                        if (columnName.equals(pattern.toLowerCase())) {
                            return columnName;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.debug("Target 날짜 컬럼 감지 실패 (무시): {}", e.getMessage());
        }
        return null;
    }

    /**
     * Source 테이블에서 날짜 컬럼 자동 감지
     * created_at, updated_at, create_date, update_date 등
     */
    private String detectSourceDateColumn(DataSource sourceDataSource, String sourceTable) {
        // 일반적인 날짜 컬럼명 패턴 (우선순위 순)
        String[] dateColumnPatterns = {
            "created_at", "create_at", "created_date", "create_date", "reg_dt", "reg_date",
            "updated_at", "update_at", "updated_date", "update_date", "upd_dt", "mod_date",
            "insert_date", "insert_dt", "timestamp", "date_created"
        };

        try (Connection conn = sourceDataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet columns = metaData.getColumns(null, null, sourceTable, null)) {
                java.util.Set<String> tableColumns = new java.util.HashSet<>();
                while (columns.next()) {
                    tableColumns.add(columns.getString("COLUMN_NAME").toLowerCase());
                }

                // 패턴 순서대로 매칭
                for (String pattern : dateColumnPatterns) {
                    if (tableColumns.contains(pattern)) {
                        log.info("Source 날짜 컬럼 자동 감지: {} in {}", pattern, sourceTable);
                        return pattern;
                    }
                }
            }
        } catch (SQLException e) {
            log.debug("Source 날짜 컬럼 감지 실패 (무시): {}", e.getMessage());
        }
        return null;
    }

    /**
     * Source 테이블의 모든 날짜성 컬럼 감지 (복수)
     * Target에 대응 컬럼이 있으면 모두 자동 매핑
     */
    private Map<String, String> detectAllDateColumnMappings(
            DataSource sourceDataSource, DataSource targetDataSource,
            String sourceTable, String targetTable, List<String> existingMappedSourceCols) {

        Map<String, String> dateColumnMappings = new LinkedHashMap<>();

        // 일반적인 날짜 컬럼명 패턴
        String[] dateColumnPatterns = {
            "created_at", "create_at", "created_date", "create_date", "reg_dt", "reg_date",
            "updated_at", "update_at", "updated_date", "update_date", "upd_dt", "mod_date"
        };

        try (Connection sourceConn = sourceDataSource.getConnection()) {
            DatabaseMetaData metaData = sourceConn.getMetaData();
            try (ResultSet columns = metaData.getColumns(null, null, sourceTable, null)) {
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME").toLowerCase();

                    // 이미 매핑된 컬럼은 건너뜀
                    if (existingMappedSourceCols.stream().anyMatch(c -> c.equalsIgnoreCase(columnName))) {
                        continue;
                    }

                    // 날짜 컬럼 패턴 매칭
                    for (String pattern : dateColumnPatterns) {
                        if (columnName.equals(pattern)) {
                            // Target에 대응하는 컬럼 찾기
                            String targetCol = detectTargetDateColumn(targetDataSource, targetTable, columnName);
                            if (targetCol != null) {
                                dateColumnMappings.put(columnName, targetCol);
                                log.info("날짜 컬럼 자동 매핑: {}.{} -> {}.{}",
                                        sourceTable, columnName, targetTable, targetCol);
                            }
                            break;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.debug("날짜 컬럼 자동 감지 실패 (무시): {}", e.getMessage());
        }

        return dateColumnMappings;
    }
}
