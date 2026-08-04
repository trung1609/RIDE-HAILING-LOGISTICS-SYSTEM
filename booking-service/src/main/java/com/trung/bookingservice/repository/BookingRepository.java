package com.trung.bookingservice.repository;

import com.trung.bookingservice.entity.Booking;
import com.trung.bookingservice.util.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    boolean existsByCustomerIdAndStatusIn(Long customerId, List<BookingStatus> status);
}
