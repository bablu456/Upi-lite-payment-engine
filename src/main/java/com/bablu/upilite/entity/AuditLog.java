package com.bablu.upilite.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(length = 60)
    private String status;

    @Column(length = 120)
    private String actorEmail;

    private UUID actorUserId;

    @Column(length = 64)
    private String requestId;

    @Column(length = 120)
    private String path;

    @Column(length = 600)
    private String details;

    @Column(length = 400)
    private String errorMessage;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
