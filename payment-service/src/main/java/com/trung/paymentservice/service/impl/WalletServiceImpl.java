package com.trung.paymentservice.service.impl;

import com.trung.paymentservice.entity.Wallet;
import com.trung.paymentservice.repository.WalletRepository;
import com.trung.paymentservice.service.WalletService;
import com.trung.paymentservice.util.enums.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;

    @Override
    @Transactional(readOnly = true)
    public Wallet getOrCreateWallet(Long userId, UserType userType) {
        return walletRepository.findByUserIdAndUserType(userId, userType)
                .orElseGet(() -> walletRepository.save(Wallet.builder()
                        .userId(userId)
                        .userType(userType)
                        .balance(BigDecimal.ZERO)
                        .build()));
    }

    @Override
    @Transactional
    public Wallet creditWallet(Long userId, UserType userType, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUserIdAndUserTypeWithLock(userId, userType)
                .orElseGet(() -> walletRepository.save(Wallet.builder()
                        .userId(userId)
                        .userType(userType)
                        .balance(BigDecimal.ZERO)
                        .build()));

        wallet.setBalance(wallet.getBalance().add(amount));
        log.info("Đã cộng {} VND vào ví của User ID {}. Số dư mới: {} VND",
                amount, userId, wallet.getBalance());
        return walletRepository.save(wallet);
    }

    @Override
    @Transactional
    public void deductCommission(Long driverId, BigDecimal amount) {
        // khóa Database lại cho đến khi tính toán xong
        Wallet wallet = walletRepository.findByUserIdAndUserTypeWithLock(driverId, UserType.DRIVER)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy ví tài xế"));

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Số dư ví không đủ để trừ hoa hồng. Vui lòng nạp thêm!");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);
    }
}