package com.trung.userdriverservice.entity;

import com.trung.userdriverservice.util.enums.DriverStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "driver_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DriverProfile {

    @Id
    @Column(name = "driver_id")
    private Long driverId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "driver_id")
    private User user;

    @Column(nullable = false, length = 20)
    private String vehicleType;

    @Column(unique = true, nullable = false, length = 20)
    private String licensePlate;

    @Column(nullable = false, length = 50)
    private String vehicleModel;

    private Boolean isActive = false;

    @Enumerated(EnumType.STRING)
    private DriverStatus status;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}