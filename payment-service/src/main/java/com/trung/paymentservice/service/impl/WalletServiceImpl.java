package com.trung.paymentservice.service.impl;

import com.trung.paymentservice.entity.Transaction;
import com.trung.paymentservice.entity.Wallet;
import com.trung.paymentservice.repository.TransactionRepository;
import com.trung.paymentservice.repository.WalletRepository;
import com.trung.paymentservice.service.WalletService;
import com.trung.paymentservice.util.enums.PaymentMethod;
import com.trung.paymentservice.util.enums.TransactionStatus;
import com.trung.paymentservice.util.enums.TransactionType;
import com.trung.paymentservice.util.enums.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

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
    public void deductCommission(Long driverId, Long bookingId, BigDecimal tripAmount) {
        boolean alreadyDeducted = transactionRepository.findByWalletIdOrderByCreatedAtDesc(
                        getOrCreateWallet(driverId, UserType.DRIVER).getId())
                .stream().anyMatch(tx -> tx.getBookingId() != null
                        && tx.getBookingId().equals(bookingId)
                        && tx.getTransactionType() == TransactionType.COMMISSION_FEE);

        if (alreadyDeducted) {
            log.info("Cuốc xe #{} đã được thu hoa hồng trước đó khi khách trả qua MoMo, bỏ qua trừ lần 2.", bookingId);
            return;
        }

        BigDecimal commissionFee = tripAmount.multiply(new BigDecimal("0.20"));
        Wallet wallet = walletRepository.findByUserIdAndUserTypeWithLock(driverId, UserType.DRIVER)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy ví tài xế"));

        if (wallet.getBalance().compareTo(commissionFee) < 0) {
            log.warn("Ví tài xế không đủ tiền trừ phí sàn. Ghi nhận công nợ âm.");
        }

        wallet.setBalance(wallet.getBalance().subtract(commissionFee));
        walletRepository.save(wallet);

        Transaction transaction = Transaction.builder()
                .walletId(wallet.getId())
                .bookingId(bookingId)
                .orderId("FEE_CASH_" + bookingId + "_" + System.currentTimeMillis())
                .amount(commissionFee)
                .transactionType(TransactionType.COMMISSION_FEE)
                .paymentMethod(PaymentMethod.WALLET)
                .status(TransactionStatus.SUCCESS)
                .build();
        transactionRepository.save(transaction);

        log.info("Đã thu hoa hồng sau cuốc xe #{}: -{} VND", bookingId, commissionFee);
    }

    @Transactional
    public void withdrawWallet(Long driverId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Số tiền rút phải lớn hơn 0!");
        }

        Wallet wallet = walletRepository.findByUserIdAndUserTypeWithLock(driverId, UserType.DRIVER)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy ví tài xế"));

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Số dư ví không đủ để thực hiện lệnh rút tiền này!");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        Transaction transaction = Transaction.builder()
                .walletId(wallet.getId())
                .orderId("WITHDRAW_" + driverId + "_" + System.currentTimeMillis())
                .amount(amount)
                .transactionType(TransactionType.WITHDRAWAL)
                .paymentMethod(PaymentMethod.WALLET)
                .status(TransactionStatus.SUCCESS)
                .build();
        transactionRepository.save(transaction);

        log.info("Tài xế ID {} đã rút thành công {} VND. Số dư còn lại: {} VND",
                driverId, amount, wallet.getBalance());
    }

    @Override
    @Transactional
    public void cancelPendingTransactionsByBookingId(Long bookingId) {
        List<Transaction> pendingTxs = transactionRepository.findByBookingIdAndStatus(bookingId, TransactionStatus.PENDING);
        if (!pendingTxs.isEmpty()) {
            for (Transaction tx : pendingTxs) {
                tx.setStatus(TransactionStatus.CANCELED);
            }
            transactionRepository.saveAll(pendingTxs);
            log.info("Đã chuyển trạng thái sang CANCELED cho {} giao dịch của booking {}", pendingTxs.size(), bookingId);
        }
    }
}