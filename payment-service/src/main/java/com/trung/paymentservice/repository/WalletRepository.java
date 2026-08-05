package com.trung.paymentservice.repository;

import com.trung.paymentservice.entity.Wallet;
import com.trung.paymentservice.util.enums.UserType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserIdAndUserType(Long userId, UserType userType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.userId = :userId AND w.userType = :userType")
    Optional<Wallet> findByUserIdAndUserTypeWithLock(@Param("userId") Long userId, @Param("userType") UserType userType);
}