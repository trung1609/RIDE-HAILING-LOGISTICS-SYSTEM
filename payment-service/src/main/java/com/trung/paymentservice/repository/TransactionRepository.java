package com.trung.paymentservice.repository;

import com.trung.paymentservice.entity.Transaction;
import com.trung.paymentservice.util.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByOrderId(String orderId);

    List<Transaction> findByWalletIdOrderByCreatedAtDesc(Long walletId);

    List<Transaction> findByBookingId(Long bookingId);

    List<Transaction> findByBookingIdAndStatus(Long bookingId, TransactionStatus status);

    List<Transaction> findByStatusAndCreatedAtBefore(TransactionStatus status, LocalDateTime cutoffTime);
}