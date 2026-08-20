package com.trung.bookingservice.repository;

import com.trung.bookingservice.entity.Booking;
import com.trung.bookingservice.util.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    boolean existsByCustomerIdAndStatusIn(Long customerId, List<BookingStatus> status);
    List<Booking> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    @Query("SELECT b FROM Booking b WHERE b.driverId = :driverId AND b.status = 'COMPLETED' AND b.completedAt >= :startOfDay AND b.completedAt <= :endOfDay")
    List<Booking> findCompletedBookingsByDriverToday(
            @Param("driverId") Long driverId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );
}
