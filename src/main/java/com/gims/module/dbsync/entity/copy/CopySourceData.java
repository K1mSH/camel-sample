package com.gims.module.dbsync.entity.copy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Copy 테이블 - Source Data의 1차 복사본 (Target DB에 저장)
 * source_data와 동일한 구조 + source_uk (역추적용)
 */
@Entity
@Table(name = "copy_source_data", indexes = {
    @Index(name = "idx_copy_source_data_uk", columnList = "source_uk", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CopySourceData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "copy_id")
    private Long copyId;

    /**
     * Source 역추적용 UK: {sourceTable}_{sourcePK}
     * 예: source_data_123
     */
    @Column(name = "source_uk", length = 100, nullable = false, unique = true)
    private String sourceUk;

    @Column(name = "id")
    private Long id;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "value1")
    private Double value1;

    @Column(name = "value2")
    private Double value2;

    @Column(name = "value3")
    private Double value3;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "copy_at")
    private LocalDateTime copyAt;

    /**
     * 추가/수정 구분: false=신규추가, true=수정
     */
    @Builder.Default
    @Column(name = "is_updated")
    private Boolean isUpdated = false;
}
