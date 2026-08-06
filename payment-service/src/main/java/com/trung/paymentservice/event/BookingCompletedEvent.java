package com.trung.paymentservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCompletedEvent {
    private Long bookingId;
    private Long driverId;
    private Long customerId;
    private Double amount;
}
