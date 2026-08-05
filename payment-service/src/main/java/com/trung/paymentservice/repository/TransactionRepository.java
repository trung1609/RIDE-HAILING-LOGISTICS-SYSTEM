package com.trung.paymentservice.repository;

import com.trung.paymentservice.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByOrderId(String orderId);
    List<Transaction> findByWalletIdOrderByCreatedAtDesc(Long walletId);
}