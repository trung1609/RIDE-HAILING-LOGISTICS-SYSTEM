package com.trung.userdriverservice.repository;

import com.trung.userdriverservice.entity.DriverProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DriverProfileRepository extends JpaRepository<DriverProfile,Long> {
    boolean existsByLicensePlate(String licensePlate);
}
