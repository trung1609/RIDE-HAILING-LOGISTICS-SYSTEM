package com.trung.bookingservice.controller;

import com.trung.bookingservice.dto.request.BookingRequest;
import com.trung.bookingservice.dto.response.BookingResponse;
import com.trung.bookingservice.exception.BadRequestException;
import com.trung.bookingservice.exception.ResourceNotFoundException;
import com.trung.bookingservice.service.impl.BookingServiceImpl;
import com.trung.bookingservice.util.enums.BookingStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingServiceImpl bookingService;

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @RequestHeader("X-User-Id") Long customerId,
            @Valid @RequestBody BookingRequest request) throws BadRequestException {

        return ResponseEntity.ok(bookingService.createBooking(customerId, request));
    }
    @PutMapping("/{bookingId}/accept")
    public ResponseEntity<BookingResponse> acceptBooking(
            @RequestHeader("X-User-Id") Long driverId,
            @PathVariable Long bookingId) throws BadRequestException {

        BookingResponse response = bookingService.acceptBooking(driverId, bookingId);
        return ResponseEntity.ok(response);
    }
    @PutMapping("/{bookingId}/arrived")
    public ResponseEntity<BookingResponse> arriveAtPickup(
            @RequestHeader("X-User-Id") Long driverId,
            @PathVariable Long bookingId) throws BadRequestException, ResourceNotFoundException {
        return ResponseEntity.ok(bookingService.updateBookingStatus(driverId, bookingId, BookingStatus.ARRIVED));
    }

    @PutMapping("/{bookingId}/start")
    public ResponseEntity<BookingResponse> startTrip(
            @RequestHeader("X-User-Id") Long driverId,
            @PathVariable Long bookingId) throws BadRequestException, ResourceNotFoundException {
        return ResponseEntity.ok(bookingService.updateBookingStatus(driverId, bookingId, BookingStatus.IN_PROGRESS));
    }

    @PutMapping("/{bookingId}/complete")
    public ResponseEntity<BookingResponse> completeTrip(
            @RequestHeader("X-User-Id") Long driverId,
            @PathVariable Long bookingId) throws BadRequestException, ResourceNotFoundException {
        return ResponseEntity.ok(bookingService.completeTrip(driverId, bookingId));
    }
}