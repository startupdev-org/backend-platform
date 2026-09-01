package com.platform.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "locations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    private Business business;

    private String name;
    private String address;
    private String city;
    private String country;

    private Double latitude;
    private Double longitude;

    // @Builder.Default is load-bearing: without it Lombok's builder drops this initializer
    // and Location.builder()...build() writes null against a NOT NULL column. Same class
    // of bug as User.isEnabled (see V2__user_profile_fields.sql) - currently masked only
    // because LocationService.createLocation happens to set the field explicitly.
    @Builder.Default
    @Column(nullable = false)
    private Boolean isDefaultLocation = false;  // true if this is the "virtual" default location

}
