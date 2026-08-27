package com.platform.entity;

import com.platform.enums.ServiceDeliveryType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@Table(name = "businesses")
public class Business {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    @ToString.Include
    private UUID id;

    @ToString.Include
    @Column(nullable = false)
    private String name;

    @Column()
    private String businessEmail;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String phone;

    private String website;

    // Object-storage key, not a URL. See com.platform.storage.ImageKeys.
    private String logoKey;

    private String coverImageKey;

    @Column(columnDefinition = "NUMERIC(3,2) DEFAULT 0")
    private Double ratingOverall;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @OneToMany(mappedBy = "business", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Employee> employees;

    @OneToMany(mappedBy = "business")
    private List<Location> locations;

    @OneToMany(
            mappedBy = "business",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<BusinessWorkingHours> workingHours = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private ServiceDeliveryType serviceDeliveryType;

    @Enumerated(EnumType.STRING)
    private BusinessCategoryType businessCategory;

    @OneToMany(mappedBy = "business", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProvidedService> providedServices;

    @OneToMany(mappedBy = "business", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<BusinessFeature> features = new HashSet<>();


    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.ratingOverall = 0.0;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isNotOwner(User userToCheck) {
        return !owner.getId().equals(userToCheck.getId());
    }
}
