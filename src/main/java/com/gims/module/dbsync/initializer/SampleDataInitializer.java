package com.gims.module.dbsync.initializer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.Random;

/**
 * Source/Target DB 샘플 테이블 및 데이터 초기화
 *
 * Source DB에 테스트용 테이블들을 생성하고 샘플 데이터를 삽입합니다.
 * Target DB에는 대응하는 테이블 구조만 생성합니다.
 */
@Slf4j
@Component
public class SampleDataInitializer implements ApplicationRunner {

    private final DataSource sourceDataSource;
    private final DataSource targetDataSource;

    private final Random random = new Random();

    // @RequiredArgsConstructor와 @Qualifier를 함께 사용하면 Qualifier가 적용되지 않음
    public SampleDataInitializer(
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Qualifier("targetDataSource") DataSource targetDataSource) {
        this.sourceDataSource = sourceDataSource;
        this.targetDataSource = targetDataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("=== 샘플 테이블 및 데이터 초기화 시작 ===");

        // 1. Source DB 테이블 생성 및 데이터 삽입
        initializeSourceDb();

        // 2. Target DB 테이블 생성 (데이터 없음)
        initializeTargetDb();

        log.info("=== 샘플 테이블 및 데이터 초기화 완료 ===");
    }

    /**
     * Source DB 초기화
     */
    private void initializeSourceDb() throws SQLException {
        try (Connection conn = sourceDataSource.getConnection()) {
            conn.setAutoCommit(false);

            // 1. source_data 테이블
            createSourceDataTable(conn);
            initializeSourceData(conn);

            // 2. source_users 테이블
            createSourceUsersTable(conn);
            initializeSourceUsers(conn);

            // 3. source_orders 테이블
            createSourceOrdersTable(conn);
            initializeSourceOrders(conn);

            conn.commit();
        }
    }

    /**
     * Target DB 초기화
     */
    private void initializeTargetDb() throws SQLException {
        try (Connection conn = targetDataSource.getConnection()) {
            conn.setAutoCommit(false);

            // 1. target_data 테이블
            createTargetDataTable(conn);

            // 2. target_users 테이블
            createTargetUsersTable(conn);

            // 3. target_orders 테이블
            createTargetOrdersTable(conn);

            conn.commit();
        }
    }

    // ==================== source_data 테이블 ====================

    private void createSourceDataTable(Connection conn) throws SQLException {
        String createSql = "CREATE TABLE IF NOT EXISTS source_data (" +
            "id BIGSERIAL PRIMARY KEY, " +
            "name VARCHAR(255), " +
            "value1 DOUBLE PRECISION, " +
            "value2 DOUBLE PRECISION, " +
            "value3 DOUBLE PRECISION, " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createSql);
            log.info("source_data 테이블 생성/확인 완료");
        }
    }

    private void initializeSourceData(Connection conn) throws SQLException {
        // 기존 데이터가 있으면 건너뜀
        int count = countTable(conn, "source_data");
        if (count > 0) {
            log.info("source_data 테이블에 기존 데이터 {}건이 있습니다", count);
            return;
        }

        String insertSql = "INSERT INTO source_data (name, value1, value2, value3) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            for (int i = 1; i <= 1000; i++) {
                pstmt.setString(1, "Sample_Data_" + i);
                pstmt.setDouble(2, random.nextDouble() * 100);
                pstmt.setDouble(3, random.nextDouble() * 200);
                pstmt.setDouble(4, random.nextDouble() * 300);
                pstmt.addBatch();

                if (i % 100 == 0) {
                    pstmt.executeBatch();
                }
            }
            pstmt.executeBatch();
        }
        log.info("source_data 테이블에 1000건 삽입 완료");
    }

    // ==================== target_data 테이블 ====================

    private void createTargetDataTable(Connection conn) throws SQLException {
        String createSql = "CREATE TABLE IF NOT EXISTS target_data (" +
            "target_id BIGINT PRIMARY KEY, " +
            "target_name VARCHAR(255), " +
            "target_value1 DOUBLE PRECISION, " +
            "target_value2 DOUBLE PRECISION, " +
            "target_value3 DOUBLE PRECISION, " +
            "sync_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createSql);
            log.info("target_data 테이블 생성/확인 완료");
        }
    }

    // ==================== source_users 테이블 ====================

    private void createSourceUsersTable(Connection conn) throws SQLException {
        String createSql = "CREATE TABLE IF NOT EXISTS source_users (" +
            "user_id BIGSERIAL PRIMARY KEY, " +
            "username VARCHAR(100) NOT NULL, " +
            "email VARCHAR(255), " +
            "phone VARCHAR(50), " +
            "status VARCHAR(20), " +
            "created_date DATE, " +
            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createSql);
            log.info("source_users 테이블 생성/확인 완료");
        }
    }

    private void initializeSourceUsers(Connection conn) throws SQLException {
        int count = countTable(conn, "source_users");
        if (count > 0) {
            log.info("source_users 테이블에 기존 데이터 {}건이 있습니다", count);
            return;
        }

        String insertSql = "INSERT INTO source_users (username, email, phone, status, created_date) VALUES (?, ?, ?, ?, ?)";
        String[] statuses = {"ACTIVE", "INACTIVE", "PENDING", "SUSPENDED"};
        LocalDate today = LocalDate.now();

        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            for (int i = 1; i <= 500; i++) {
                pstmt.setString(1, "user_" + i);
                pstmt.setString(2, "user" + i + "@example.com");
                pstmt.setString(3, "010-" + String.format("%04d", random.nextInt(10000)) + "-" + String.format("%04d", random.nextInt(10000)));
                pstmt.setString(4, statuses[random.nextInt(statuses.length)]);
                // 현재 날짜 기준 최근 60일 내 랜덤 날짜
                pstmt.setDate(5, Date.valueOf(today.minusDays(random.nextInt(60))));
                pstmt.addBatch();

                if (i % 100 == 0) {
                    pstmt.executeBatch();
                }
            }
            pstmt.executeBatch();
        }
        log.info("source_users 테이블에 500건 삽입 완료");
    }

    // ==================== target_users 테이블 ====================

    private void createTargetUsersTable(Connection conn) throws SQLException {
        String createSql = "CREATE TABLE IF NOT EXISTS target_users (" +
            "sync_user_id BIGINT PRIMARY KEY, " +
            "user_name VARCHAR(100), " +
            "user_email VARCHAR(255), " +
            "user_phone VARCHAR(50), " +
            "user_status VARCHAR(20), " +
            "register_date DATE, " +
            "sync_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createSql);
            log.info("target_users 테이블 생성/확인 완료");
        }
    }

    // ==================== source_orders 테이블 ====================

    private void createSourceOrdersTable(Connection conn) throws SQLException {
        String createSql = "CREATE TABLE IF NOT EXISTS source_orders (" +
            "order_id BIGSERIAL PRIMARY KEY, " +
            "order_number VARCHAR(50) NOT NULL, " +
            "customer_name VARCHAR(100), " +
            "product_name VARCHAR(200), " +
            "quantity INTEGER, " +
            "unit_price DECIMAL(10,2), " +
            "total_amount DECIMAL(12,2), " +
            "order_status VARCHAR(30), " +
            "order_date DATE, " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createSql);
            log.info("source_orders 테이블 생성/확인 완료");
        }
    }

    private void initializeSourceOrders(Connection conn) throws SQLException {
        int count = countTable(conn, "source_orders");
        if (count > 0) {
            log.info("source_orders 테이블에 기존 데이터 {}건이 있습니다", count);
            return;
        }

        String insertSql = "INSERT INTO source_orders (order_number, customer_name, product_name, quantity, unit_price, total_amount, order_status, order_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String[] products = {"노트북", "모니터", "키보드", "마우스", "헤드셋", "웹캠", "USB허브", "외장하드"};
        String[] statuses = {"PENDING", "CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED"};
        LocalDate today = LocalDate.now();

        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            for (int i = 1; i <= 800; i++) {
                int qty = random.nextInt(10) + 1;
                double unitPrice = (random.nextInt(50) + 1) * 10000.0;
                double totalAmount = qty * unitPrice;

                pstmt.setString(1, "ORD-" + String.format("%06d", i));
                pstmt.setString(2, "고객_" + random.nextInt(100));
                pstmt.setString(3, products[random.nextInt(products.length)]);
                pstmt.setInt(4, qty);
                pstmt.setBigDecimal(5, new java.math.BigDecimal(unitPrice));
                pstmt.setBigDecimal(6, new java.math.BigDecimal(totalAmount));
                pstmt.setString(7, statuses[random.nextInt(statuses.length)]);
                // 현재 날짜 기준 최근 90일 내 랜덤 날짜
                pstmt.setDate(8, Date.valueOf(today.minusDays(random.nextInt(90))));
                pstmt.addBatch();

                if (i % 100 == 0) {
                    pstmt.executeBatch();
                }
            }
            pstmt.executeBatch();
        }
        log.info("source_orders 테이블에 800건 삽입 완료");
    }

    // ==================== target_orders 테이블 ====================

    private void createTargetOrdersTable(Connection conn) throws SQLException {
        String createSql = "CREATE TABLE IF NOT EXISTS target_orders (" +
            "sync_order_id BIGINT PRIMARY KEY, " +
            "ord_number VARCHAR(50), " +
            "cust_name VARCHAR(100), " +
            "prod_name VARCHAR(200), " +
            "qty INTEGER, " +
            "price DECIMAL(10,2), " +
            "total DECIMAL(12,2), " +
            "status VARCHAR(30), " +
            "ord_date DATE, " +
            "sync_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createSql);
            log.info("target_orders 테이블 생성/확인 완료");
        }
    }

    // ==================== 유틸리티 ====================

    private int countTable(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            // 테이블이 없을 수 있음
            log.debug("테이블 조회 오류 (테이블 없을 수 있음): {}", e.getMessage());
        }
        return 0;
    }
}
