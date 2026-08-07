package com.trung.paymentservice.service.impl;

import com.trung.paymentservice.entity.Transaction;
import com.trung.paymentservice.entity.Wallet;
import com.trung.paymentservice.event.BookingCompletedEvent;
import com.trung.paymentservice.event.DriverRegisteredEvent;
import com.trung.paymentservice.repository.TransactionRepository;
import com.trung.paymentservice.repository.WalletRepository;
import com.trung.paymentservice.service.WalletService;
import com.trung.paymentservice.util.enums.PaymentMethod;
import com.trung.paymentservice.util.enums.TransactionStatus;
import com.trung.paymentservice.util.enums.TransactionType;
import com.trung.paymentservice.util.enums.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final RestTemplate internalRestTemplate;

    @Transactional
    @KafkaListener(topics = "driver-registered-topic", groupId = "payment-service")
    public void handleDriverRegistered(DriverRegisteredEvent event) {
        if (event == null || event.getDriverId() == null) {
            log.error("Dữ liệu Kafka Event driver-registered không hợp lệ.");
            return;
        }

        Long driverId = event.getDriverId();

        boolean exists = walletRepository.findByUserIdAndUserType(driverId, UserType.DRIVER).isPresent();
        if (exists) {
            log.info("Ví của Driver ID {} đã tồn tại từ trước, không cần tạo mới.", driverId);
            return;
        }

        Wallet wallet = Wallet.builder()
                .userId(driverId)
                .userType(UserType.DRIVER)
                .balance(BigDecimal.ZERO)
                .build();

        walletRepository.save(wallet);
        log.info("Kafka: Khởi tạo thành công Ví 0 VND cho Driver mới đăng ký, ID: {}", driverId);
    }

    @Override
    @Transactional
    public Wallet getOrCreateWallet(Long userId, UserType userType) {
        try {
            return walletRepository.findByUserIdAndUserType(userId, userType)
                    .orElseGet(() -> walletRepository.save(Wallet.builder()
                            .userId(userId)
                            .userType(userType)
                            .balance(BigDecimal.ZERO)
                            .build()));
        } catch (Exception e) {
            log.error("Lỗi xung đột khi tự động khởi tạo ví cho User {}, thử truy vấn lại: {}", userId, e.getMessage());
            return walletRepository.findByUserIdAndUserType(userId, userType)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "Không thể khởi tạo ví cho User ID " + userId + " sau nhiều lần thử. Vui lòng thử lại sau."
                    ));
        }
    }

    @Override
    @Transactional
    public Wallet creditWallet(Long userId, UserType userType, Double amount) {
        Wallet wallet = walletRepository.findByUserIdAndUserTypeWithLock(userId, userType)
                .orElseGet(() -> walletRepository.save(Wallet.builder()
                        .userId(userId)
                        .userType(userType)
                        .balance(BigDecimal.ZERO)
                        .build()));

        BigDecimal bdAmount = BigDecimal.valueOf(amount);
        wallet.setBalance(wallet.getBalance().add(bdAmount));
        log.info("Đã cộng {} VND vào ví của User ID {}. Số dư mới: {} VND",
                amount, userId, wallet.getBalance());
        return walletRepository.save(wallet);
    }

    @Override
    @Transactional
    @KafkaListener(topics = "booking-completed-topic", groupId = "payment-service")
    public void deductCommission(BookingCompletedEvent event) {
        Long driverId = event.getDriverId();
        Long bookingId = event.getBookingId();
        Double tripAmount = event.getAmount();

        if (driverId == null || bookingId == null || tripAmount == null) {
            log.error("Dữ liệu Kafka Event không hợp lệ: {}", event);
            return;
        }

        boolean alreadyDeducted = transactionRepository.findByWalletIdOrderByCreatedAtDesc(
                        getOrCreateWallet(driverId, UserType.DRIVER).getId())
                .stream().anyMatch(tx -> tx.getBookingId() != null
                        && tx.getBookingId().equals(bookingId)
                        && tx.getTransactionType() == TransactionType.COMMISSION_FEE);

        if (alreadyDeducted) {
            log.info("Cuốc xe #{} đã được thu hoa hồng trước đó, bỏ qua trừ lần 2.", bookingId);
            return;
        }

        double commissionValue = tripAmount * 0.20;
        BigDecimal commissionFee = BigDecimal.valueOf(commissionValue);
        Wallet wallet = walletRepository.findByUserIdAndUserTypeWithLock(driverId, UserType.DRIVER)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy ví của tài xế ID " + driverId + " để trừ phí hoa hồng."
                ));

        if (wallet.getBalance().compareTo(commissionFee) < 0) {
            log.warn("Ví tài xế không đủ tiền trừ phí sàn. Ghi nhận công nợ âm.");
        }

        wallet.setBalance(wallet.getBalance().subtract(commissionFee));
        walletRepository.save(wallet);

        Transaction transaction = Transaction.builder()
                .walletId(wallet.getId())
                .bookingId(bookingId)
                .orderId("FEE_KAFKA_" + bookingId + "_" + System.currentTimeMillis())
                .amount(commissionFee)
                .transactionType(TransactionType.COMMISSION_FEE)
                .paymentMethod(PaymentMethod.WALLET)
                .status(TransactionStatus.SUCCESS)
                .build();
        transactionRepository.save(transaction);

        log.info("Đã thu hoa hồng sau cuốc xe #{}: -{} VND", bookingId, commissionFee);
        if (wallet.getBalance().compareTo(BigDecimal.valueOf(-50000)) <= 0) {
            log.warn("Tài khoản Driver {} nợ cước hệ thống quá hạn mức (-50k). Số dư: {}. Ép OFFLINE!", driverId, wallet.getBalance());
            try {
                String lockUrl = "http://USER-DRIVER-SERVICE/api/v1/drivers/" + driverId + "/status?isActive=false";
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-User-Id", String.valueOf(driverId));

                HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
                internalRestTemplate.exchange(lockUrl, HttpMethod.PUT, requestEntity, Void.class);

                log.info("Đã kích hoạt cơ chế đá tài xế {} về OFFLINE thành công.", driverId);
            } catch (Exception e) {
                log.error("Lỗi khi kết nối gọi ép Offline: {}", e.getMessage());
            }
        }
    }

    @Transactional
    public void withdrawWallet(Long driverId, Double amount) {
        if (amount <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Số tiền rút phải lớn hơn 0!"
            );
        }
        BigDecimal bdAmount = BigDecimal.valueOf(amount);
        Wallet wallet = walletRepository.findByUserIdAndUserTypeWithLock(driverId, UserType.DRIVER)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Không tìm thấy ví của tài xế ID " + driverId + " để trừ phí hoa hồng."
                ));

        if (wallet.getBalance().compareTo(bdAmount) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Số dư ví không đủ để thực hiện lệnh rút tiền này!"
            );
        }

        wallet.setBalance(wallet.getBalance().subtract(bdAmount));
        walletRepository.save(wallet);

        Transaction transaction = Transaction.builder()
                .walletId(wallet.getId())
                .orderId("WITHDRAW_" + driverId + "_" + System.currentTimeMillis())
                .amount(bdAmount)
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