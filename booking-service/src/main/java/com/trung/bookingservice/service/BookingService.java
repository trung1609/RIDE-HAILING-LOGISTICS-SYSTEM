package com.trung.bookingservice.service;

import com.trung.bookingservice.dto.request.BookingRequest;
import com.trung.bookingservice.dto.response.ApiResponse;
import com.trung.bookingservice.dto.response.BookingResponse;
import com.trung.bookingservice.entity.Booking;
import com.trung.bookingservice.exception.BadRequestException;
import com.trung.bookingservice.exception.ResourceNotFoundException;
import com.trung.bookingservice.util.enums.BookingStatus;

import java.util.List;

public interface BookingService {
    BookingResponse createBooking(Long customerId, BookingRequest request) throws BadRequestException;

    BookingResponse acceptBooking(Long driverId, Long bookingId) throws BadRequestException, ResourceNotFoundException;
    BookingResponse updateBookingStatus(Long driverId, Long bookingId, BookingStatus newStatus) throws ResourceNotFoundException, BadRequestException;
    BookingResponse completeTrip(Long driverId, Long bookingId) throws ResourceNotFoundException, BadRequestException;
    BookingResponse cancelBooking(Long customerId, Long bookingId) throws ResourceNotFoundException, BadRequestException;
    BookingResponse cancelBookingByDriver(Long driverId, Long bookingId) throws ResourceNotFoundException, BadRequestException;
    ApiResponse<List<Booking>> getCustomerBookings(Long customerId);
}
