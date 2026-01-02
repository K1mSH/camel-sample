package com.gims.module.dbsync.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;

/**
 * 동적 DataSource 팩토리
 * 관리 시스템에서 전달받은 DB 연결 정보로 동적으로 DataSource 생성
 */
@Slf4j
@Component
public class DynamicDataSourceFactory {

    /**
     * DB 연결 정보로 DataSource 생성
     *
     * @param dbConfig DB 연결 정보 Map
     *   - host: DB 호스트
     *   - port: DB 포트
     *   - databaseName: 데이터베이스명
     *   - username: 사용자명
     *   - password: 비밀번호
     *   - dbType: DB 유형 (ORACLE, POSTGRESQL, MYSQL, MSSQL 등)
     *   - jdbcUrl: JDBC URL (선택, 있으면 직접 사용)
     *   - driverClassName: 드라이버 클래스명 (선택)
     */
    public HikariDataSource createDataSource(Map<String, Object> dbConfig) {
        String connectionId = getString(dbConfig, "connectionId", "dynamic");
        String dbType = getString(dbConfig, "dbType", "ORACLE").toUpperCase();
        String host = getString(dbConfig, "host", "localhost");
        Integer port = getInteger(dbConfig, "port", getDefaultPort(dbType));
        String databaseName = getString(dbConfig, "databaseName", "");
        String schemaName = getString(dbConfig, "schemaName", "");
        String username = getString(dbConfig, "username", "");
        String password = getString(dbConfig, "password", "");

        // JDBC URL 생성 또는 직접 사용
        String jdbcUrl = getString(dbConfig, "jdbcUrl", null);
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            jdbcUrl = buildJdbcUrl(dbType, host, port, databaseName);
        }

        // 드라이버 클래스 결정
        String driverClassName = getString(dbConfig, "driverClassName", null);
        if (driverClassName == null || driverClassName.isEmpty()) {
            driverClassName = resolveDriverClassName(dbType);
        }

        log.info("동적 DataSource 생성: connectionId={}, dbType={}, host={}:{}, database={}",
                connectionId, dbType, host, port, databaseName);

        HikariConfig config = new HikariConfig();
        config.setPoolName("DynamicPool-" + connectionId);
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName(driverClassName);
        config.setUsername(username);
        config.setPassword(password);

        // 풀 설정 (동적 연결이므로 작게 유지)
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(5);
        config.setConnectionTimeout(30000L);
        config.setIdleTimeout(600000L);
        config.setMaxLifetime(1800000L);

        // 스키마 설정
        if (schemaName != null && !schemaName.isEmpty()) {
            config.setSchema(schemaName);
        }

        // 연결 검증
        config.setConnectionTestQuery(getValidationQuery(dbType));

        return new HikariDataSource(config);
    }

    /**
     * JDBC URL 생성
     */
    private String buildJdbcUrl(String dbType, String host, Integer port, String databaseName) {
        switch (dbType) {
            case "ORACLE":
                return String.format("jdbc:oracle:thin:@%s:%d:%s", host, port, databaseName);
            case "ORACLE_SERVICE":
                return String.format("jdbc:oracle:thin:@//%s:%d/%s", host, port, databaseName);
            case "POSTGRESQL":
                return String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName);
            case "MYSQL":
            case "MARIADB":
                return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=Asia/Seoul",
                        host, port, databaseName);
            case "MSSQL":
                return String.format("jdbc:sqlserver://%s:%d;databaseName=%s", host, port, databaseName);
            default:
                return String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName);
        }
    }

    /**
     * 드라이버 클래스 결정
     */
    private String resolveDriverClassName(String dbType) {
        switch (dbType) {
            case "ORACLE":
            case "ORACLE_SERVICE":
                return "oracle.jdbc.OracleDriver";
            case "POSTGRESQL":
                return "org.postgresql.Driver";
            case "MYSQL":
                return "com.mysql.cj.jdbc.Driver";
            case "MARIADB":
                return "org.mariadb.jdbc.Driver";
            case "MSSQL":
                return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            default:
                return "org.postgresql.Driver";
        }
    }

    /**
     * DB 유형별 기본 포트
     */
    private Integer getDefaultPort(String dbType) {
        switch (dbType) {
            case "ORACLE":
            case "ORACLE_SERVICE":
                return 1521;
            case "POSTGRESQL":
                return 5432;
            case "MYSQL":
            case "MARIADB":
                return 3306;
            case "MSSQL":
                return 1433;
            default:
                return 5432;
        }
    }

    /**
     * DB 유형별 연결 검증 쿼리
     */
    private String getValidationQuery(String dbType) {
        switch (dbType) {
            case "ORACLE":
            case "ORACLE_SERVICE":
                return "SELECT 1 FROM DUAL";
            default:
                return "SELECT 1";
        }
    }

    /**
     * Map에서 String 값 추출
     */
    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    /**
     * Map에서 Integer 값 추출
     */
    private Integer getInteger(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
