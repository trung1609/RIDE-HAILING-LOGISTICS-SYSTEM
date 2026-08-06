package com.trung.paymentservice.service;

import com.trung.paymentservice.entity.Wallet;
import com.trung.paymentservice.util.enums.UserType;

import java.math.BigDecimal;

public interface WalletService {
    Wallet getOrCreateWallet(Long userId, UserType userType);
    void deductCommission(Long driverId, Long bookingId, BigDecimal tripAmount);
    Wallet creditWallet(Long userId, UserType userType, BigDecimal amount);
    void withdrawWallet(Long driverId, BigDecimal amount);
    void cancelPendingTransactionsByBookingId(Long bookingId);
}
