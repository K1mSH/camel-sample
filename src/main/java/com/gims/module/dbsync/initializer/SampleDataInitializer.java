package com.gims.module.dbsync.initializer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Random;

/**
 * Source DB 샘플 데이터 초기화
 *
 * 테이블은 JPA Entity 기반으로 자동 생성되고,
 * 이 클래스는 샘플 데이터만 삽입합니다.
 */
@Slf4j
@Component
public class SampleDataInitializer implements ApplicationRunner {

    private final DataSource sourceDataSource;

    private final Random random = new Random();

    public SampleDataInitializer(
            @Qualifier("sourceDataSource") DataSource sourceDataSource) {
        this.sourceDataSource = sourceDataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("=== 샘플 데이터 초기화 시작 ===");

        try (Connection conn = sourceDataSource.getConnection()) {
            conn.setAutoCommit(false);

            // 1. source_data 데이터 삽입
            initializeSourceData(conn);

            // 2. source_users 데이터 삽입
            initializeSourceUsers(conn);

            // 3. source_orders 데이터 삽입
            initializeSourceOrders(conn);

            conn.commit();
        }

        log.info("=== 샘플 데이터 초기화 완료 ===");
    }

    // ==================== source_data ====================

    private void initializeSourceData(Connection conn) throws SQLException {
        int count = countTable(conn, "source_data");
        if (count > 0) {
            log.info("source_data 테이블에 기존 데이터 {}건이 있습니다", count);
            return;
        }

        String insertSql = "INSERT INTO source_data (name, value1, value2, value3, created_at) VALUES (?, ?, ?, ?, ?)";
        LocalDateTime now = LocalDateTime.now();

        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            for (int i = 1; i <= 1000; i++) {
                pstmt.setString(1, "Sample_Data_" + i);
                pstmt.setDouble(2, random.nextDouble() * 100);
                pstmt.setDouble(3, random.nextDouble() * 200);
                pstmt.setDouble(4, random.nextDouble() * 300);

                LocalDateTime randomDateTime;
                if (i <= 200) {
                    // 1~200: 최근 24시간 내 (테스트용 - 24시간 필터에 포함됨)
                    randomDateTime = now.minusHours(random.nextInt(24));
                } else if (i <= 500) {
                    // 201~500: 최근 3일 내
                    randomDateTime = now.minusHours(24 + random.nextInt(48));
                } else {
                    // 501~1000: 최근 14일 내
                    randomDateTime = now.minusDays(3 + random.nextInt(11));
                }
                pstmt.setTimestamp(5, Timestamp.valueOf(randomDateTime));
                pstmt.addBatch();

                if (i % 100 == 0) {
                    pstmt.executeBatch();
                }
            }
            pstmt.executeBatch();
        }
        log.info("source_data 테이블에 1000건 삽입 완료 (최근 24시간: 200건, 3일: 300건, 14일: 500건)");
    }

    // ==================== source_users ====================

    private void initializeSourceUsers(Connection conn) throws SQLException {
        int count = countTable(conn, "source_users");
        if (count > 0) {
            log.info("source_users 테이블에 기존 데이터 {}건이 있습니다", count);
            return;
        }

        String insertSql = "INSERT INTO source_users (username, email, phone, status, created_date, updated_at) VALUES (?, ?, ?, ?, ?, ?)";
        String[] statuses = {"ACTIVE", "INACTIVE", "PENDING", "SUSPENDED"};
        LocalDateTime now = LocalDateTime.now();

        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            for (int i = 1; i <= 500; i++) {
                LocalDateTime randomDateTime;
                if (i <= 100) {
                    // 1~100: 최근 24시간 내
                    randomDateTime = now.minusHours(random.nextInt(24));
                } else if (i <= 250) {
                    // 101~250: 최근 3일 내
                    randomDateTime = now.minusHours(24 + random.nextInt(48));
                } else {
                    // 251~500: 최근 14일 내
                    randomDateTime = now.minusDays(3 + random.nextInt(11));
                }

                pstmt.setString(1, "user_" + i);
                pstmt.setString(2, "user" + i + "@example.com");
                pstmt.setString(3, "010-" + String.format("%04d", random.nextInt(10000)) + "-" + String.format("%04d", random.nextInt(10000)));
                pstmt.setString(4, statuses[random.nextInt(statuses.length)]);
                pstmt.setDate(5, Date.valueOf(randomDateTime.toLocalDate()));
                pstmt.setTimestamp(6, Timestamp.valueOf(randomDateTime));
                pstmt.addBatch();

                if (i % 100 == 0) {
                    pstmt.executeBatch();
                }
            }
            pstmt.executeBatch();
        }
        log.info("source_users 테이블에 500건 삽입 완료 (최근 24시간: 100건, 3일: 150건, 14일: 250건)");
    }

    // ==================== source_orders ====================

    private void initializeSourceOrders(Connection conn) throws SQLException {
        int count = countTable(conn, "source_orders");
        if (count > 0) {
            log.info("source_orders 테이블에 기존 데이터 {}건이 있습니다", count);
            return;
        }

        String insertSql = "INSERT INTO source_orders (order_number, customer_name, product_name, quantity, unit_price, total_amount, order_status, order_date, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String[] products = {"노트북", "모니터", "키보드", "마우스", "헤드셋", "웹캠", "USB허브", "외장하드"};
        String[] statuses = {"PENDING", "CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED"};
        LocalDateTime now = LocalDateTime.now();

        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            for (int i = 1; i <= 800; i++) {
                int qty = random.nextInt(10) + 1;
                double unitPrice = (random.nextInt(50) + 1) * 10000.0;
                double totalAmount = qty * unitPrice;

                LocalDateTime randomDateTime;
                if (i <= 150) {
                    // 1~150: 최근 24시간 내
                    randomDateTime = now.minusHours(random.nextInt(24));
                } else if (i <= 400) {
                    // 151~400: 최근 3일 내
                    randomDateTime = now.minusHours(24 + random.nextInt(48));
                } else {
                    // 401~800: 최근 14일 내
                    randomDateTime = now.minusDays(3 + random.nextInt(11));
                }

                pstmt.setString(1, "ORD-" + String.format("%06d", i));
                pstmt.setString(2, "고객_" + random.nextInt(100));
                pstmt.setString(3, products[random.nextInt(products.length)]);
                pstmt.setInt(4, qty);
                pstmt.setBigDecimal(5, new java.math.BigDecimal(unitPrice));
                pstmt.setBigDecimal(6, new java.math.BigDecimal(totalAmount));
                pstmt.setString(7, statuses[random.nextInt(statuses.length)]);
                pstmt.setDate(8, Date.valueOf(randomDateTime.toLocalDate()));
                pstmt.setTimestamp(9, Timestamp.valueOf(randomDateTime));
                pstmt.addBatch();

                if (i % 100 == 0) {
                    pstmt.executeBatch();
                }
            }
            pstmt.executeBatch();
        }
        log.info("source_orders 테이블에 800건 삽입 완료 (최근 24시간: 150건, 3일: 250건, 14일: 400건)");
    }

    // ==================== 유틸리티 ====================

    private int countTable(Connection conn, String tableName) {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            // 테이블이 아직 없을 수 있음 (JPA가 생성 전)
            log.debug("테이블 조회 실패 (아직 생성 전일 수 있음): {}", e.getMessage());
        }
        return 0;
    }
}
