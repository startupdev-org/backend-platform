package com.platform.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "business_features",
        uniqueConstraints = @UniqueConstraint(columnNames = {"business_id", "name"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long featureId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    // Unique per business, not platform-wide - enforced by the composite
    // uniqueConstraints above, not here. A column-level `unique = true` on name alone
    // used to let the first business to add "WiFi" claim that name for every business
    // on the platform forever. See V10__scope_business_feature_name_uniqueness.sql.
    @Column(nullable = false)
    private String name;
}
