package com.gims.module.dbsync.entity.source;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Source DB 데이터 Entity
 */
@Entity
@Table(name = "source_data")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SourceData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
}