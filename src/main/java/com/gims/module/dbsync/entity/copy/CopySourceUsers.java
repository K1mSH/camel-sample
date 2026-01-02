package com.gims.module.dbsync.entity.copy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Copy 테이블 - Source Users의 1차 복사본 (Target DB에 저장)
 * source_users와 동일한 구조 + source_uk (역추적용)
 */
@Entity
@Table(name = "copy_source_users", indexes = {
    @Index(name = "idx_copy_source_users_uk", columnList = "source_uk", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CopySourceUsers {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "copy_id")
    private Long copyId;

    /**
     * Source 역추적용 UK: {sourceTable}_{sourcePK}
     * 예: source_users_456
     */
    @Column(name = "source_uk", length = 100, nullable = false, unique = true)
    private String sourceUk;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 100, nullable = false)
    private String username;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "created_date")
    private LocalDate createdDate;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "copy_at")
    private LocalDateTime copyAt;

    /**
     * 추가/수정 구분: false=신규추가, true=수정
     */
    @Builder.Default
    @Column(name = "is_updated")
    private Boolean isUpdated = false;
}
