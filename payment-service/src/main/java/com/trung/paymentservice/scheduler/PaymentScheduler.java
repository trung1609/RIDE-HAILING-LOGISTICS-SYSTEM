package com.trung.paymentservice.scheduler;

import com.trung.paymentservice.entity.Transaction;
import com.trung.paymentservice.repository.TransactionRepository;
import com.trung.paymentservice.util.enums.TransactionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentScheduler {

    private final TransactionRepository transactionRepository;

    @Scheduled(cron = "0 */5 * * * *")
//    @Scheduled(cron = "*/5 * * * * *")
    @Transactional
    public void cleanExpiredPendingTransactions() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(5);
//        LocalDateTime cutoffTime = LocalDateTime.now().minusSeconds(20);
        List<Transaction> expiredTxs = transactionRepository.findByStatusAndCreatedAtBefore(
                TransactionStatus.PENDING, cutoffTime
        );

        if (!expiredTxs.isEmpty()) {
            log.info("Phát hiện {} giao dịch PENDING treo quá 15 phút. Đang tiến hành tự động hủy...", expiredTxs.size());

            for (Transaction tx : expiredTxs) {
                tx.setStatus(TransactionStatus.CANCELED);
            }

            transactionRepository.saveAll(expiredTxs);
            log.info("Đã hủy thành công {} giao dịch hết hạn.", expiredTxs.size());
        }
    }
}