package com.platform.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    @ToString.Include
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    // Never serialized. Belt-and-braces: any DTO or endpoint that accidentally holds a
    // User entity still cannot leak the hash.
    @JsonIgnore
    @Column(nullable = false)
    private String password;

    // Nullable in the DB because rows created before these columns existed have no real
    // value to backfill. Required at the write path instead - see RegisterRequest.
    @Column(length = 100)
    private String firstName;

    @Column(length = 100)
    private String lastName;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // @Builder.Default is load-bearing: without it Lombok's builder ignores the field
    // initializer and every registered user lands with is_enabled = false.
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean isEnabled = true;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum UserRole {
        PLATFORM_ADMIN, BUSINESS_ADMIN
    }
}
