package com.gims.module.dbsync.entity.source;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Source DB 사용자 Entity
 */
@Entity
@Table(name = "source_users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SourceUsers {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
}
