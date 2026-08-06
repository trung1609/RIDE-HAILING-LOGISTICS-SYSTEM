package com.trung.paymentservice.service;

import com.trung.paymentservice.entity.Wallet;
import com.trung.paymentservice.event.BookingCompletedEvent;
import com.trung.paymentservice.util.enums.UserType;

import java.math.BigDecimal;

public interface WalletService {
    Wallet getOrCreateWallet(Long userId, UserType userType);
    void deductCommission(BookingCompletedEvent event);
    Wallet creditWallet(Long userId, UserType userType, Double amount);
    void withdrawWallet(Long driverId, Double amount);
    void cancelPendingTransactionsByBookingId(Long bookingId);
}
