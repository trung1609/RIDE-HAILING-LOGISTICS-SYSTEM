package com.trung.paymentservice.service.impl;

import com.trung.paymentservice.config.MomoConfig;
import com.trung.paymentservice.dto.request.MomoCreateRequest;
import com.trung.paymentservice.dto.request.MomoIpnRequest;
import com.trung.paymentservice.dto.response.MomoCreateResponse;
import com.trung.paymentservice.dto.response.PaymentUrlResponse;
import com.trung.paymentservice.entity.Transaction;
import com.trung.paymentservice.entity.Wallet;
import com.trung.paymentservice.event.BookingCompletedEvent;
import com.trung.paymentservice.repository.TransactionRepository;
import com.trung.paymentservice.service.MomoEncoder;
import com.trung.paymentservice.service.WalletService;
import com.trung.paymentservice.strategy.PaymentStrategy;
import com.trung.paymentservice.util.enums.PaymentMethod;
import com.trung.paymentservice.util.enums.TransactionStatus;
import com.trung.paymentservice.util.enums.TransactionType;
import com.trung.paymentservice.util.enums.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MomoService implements PaymentStrategy {

    private final MomoConfig momoConfig;
    private final MomoEncoder momoEncoder;
    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final RestTemplate restTemplate;

    @Override
    public String getPaymentMethod() {
        return "MOMO";
    }

    @Override
    @Transactional
    public PaymentUrlResponse createPayment(Long userId, Long driverId, Long bookingId, BigDecimal amount, String type) {
        MomoCreateResponse momoResponse;
        String orderId;

        if ("DEPOSIT".equalsIgnoreCase(type)) {
            Wallet wallet = walletService.getOrCreateWallet(userId, UserType.DRIVER);
            orderId = "DEPOSIT_" + userId + "_" + System.currentTimeMillis();
            String requestId = UUID.randomUUID().toString();
            String extraData = "USER_TYPE=DRIVER;USER_ID=" + userId;

            savePendingTransaction(wallet.getId(), null, orderId, amount, TransactionType.DEPOSIT);
            momoResponse = executeMomoApi(orderId, requestId, amount, "Nap tien vi tai xe #" + userId, "captureWallet", extraData);
        } else {
            Wallet customerWallet = walletService.getOrCreateWallet(userId, UserType.CUSTOMER);
            orderId = "TRIP_" + bookingId + "_" + System.currentTimeMillis();
            String requestId = UUID.randomUUID().toString();
            String extraData = "BOOKING_ID=" + bookingId + ";DRIVER_ID=" + driverId;

            savePendingTransaction(customerWallet.getId(), bookingId, orderId, amount, TransactionType.TRIP_PAYMENT);
            momoResponse = executeMomoApi(orderId, requestId, amount, "Thanh toan cuoc xe #" + bookingId, "captureWallet", extraData);
        }

        return PaymentUrlResponse.builder()
                .orderId(orderId)
                .paymentUrl(momoResponse != null ? momoResponse.getPayUrl() : null)
                .build();
    }

    private void savePendingTransaction(Long walletId, Long bookingId, String orderId, BigDecimal amount, TransactionType type) {
        Transaction transaction = Transaction.builder()
                .walletId(walletId)
                .bookingId(bookingId)
                .orderId(orderId)
                .amount(amount)
                .transactionType(type)
                .paymentMethod(PaymentMethod.MOMO)
                .status(TransactionStatus.PENDING)
                .build();
        transactionRepository.save(transaction);
    }

    @Transactional
    public void processMomoIpn(MomoIpnRequest ipnRequest) {
        if (!verifyIpnSignature(ipnRequest)) {
            log.error("CẢNH BÁO: Chữ ký MoMo IPN không hợp lệ! OrderId: {}", ipnRequest.getOrderId());
            throw new RuntimeException("Chữ ký IPN không hợp lệ!");
        }

        Transaction transaction = transactionRepository.findByOrderId(ipnRequest.getOrderId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch: " + ipnRequest.getOrderId()));

        if (transaction.getStatus() == TransactionStatus.SUCCESS) return;

        if (ipnRequest.getResultCode() != null && ipnRequest.getResultCode() == 0) {
            transaction.setStatus(TransactionStatus.SUCCESS);
            transaction.setGatewayTransId(ipnRequest.getTransId());
            transactionRepository.save(transaction);

            if (transaction.getTransactionType() == TransactionType.DEPOSIT) {
                Long userId = parseDataFromExtra(ipnRequest.getExtraData(), "USER_ID=");
                if (userId != null) walletService.creditWallet(userId, UserType.DRIVER, transaction.getAmount().doubleValue());
            } else if (transaction.getTransactionType() == TransactionType.TRIP_PAYMENT) {
                Long driverId = parseDataFromExtra(ipnRequest.getExtraData(), "DRIVER_ID=");
                creditDriverForTrip(driverId, transaction);
            }
        } else {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
        }
    }

    @Transactional
    public void processMomoReturn(String orderId, Integer resultCode, String extraData) {
        Transaction transaction = transactionRepository.findByOrderId(orderId).orElse(null);
        if (transaction == null || transaction.getStatus() == TransactionStatus.SUCCESS) return;

        if (resultCode == 0) {
            transaction.setStatus(TransactionStatus.SUCCESS);
            transactionRepository.save(transaction);

            if (transaction.getTransactionType() == TransactionType.DEPOSIT) {
                Long userId = parseDataFromExtra(extraData, "USER_ID=");
                if (userId != null) walletService.creditWallet(userId, UserType.DRIVER, transaction.getAmount().doubleValue());
            } else if (transaction.getTransactionType() == TransactionType.TRIP_PAYMENT) {
                Long driverId = parseDataFromExtra(extraData, "DRIVER_ID=");
                creditDriverForTrip(driverId, transaction);
            }
        } else if (resultCode == 1006) {
            transaction.setStatus(TransactionStatus.CANCELED);
            transactionRepository.save(transaction);
        } else {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
        }
    }

    private void creditDriverForTrip(Long driverId, Transaction customerTransaction) {
        if (driverId == null) return;
        Wallet driverWallet = walletService.getOrCreateWallet(driverId, UserType.DRIVER);

        walletService.creditWallet(driverId, UserType.DRIVER, customerTransaction.getAmount().doubleValue());

        Transaction driverIncomeTx = Transaction.builder()
                .walletId(driverWallet.getId())
                .bookingId(customerTransaction.getBookingId())
                .orderId("INC_" + customerTransaction.getOrderId())
                .amount(customerTransaction.getAmount())
                .transactionType(TransactionType.TRIP_INCOME)
                .paymentMethod(PaymentMethod.WALLET)
                .status(TransactionStatus.SUCCESS)
                .build();
        transactionRepository.save(driverIncomeTx);

        BookingCompletedEvent event = BookingCompletedEvent.builder()
                .bookingId(customerTransaction.getBookingId())
                .driverId(driverId)
                .amount(customerTransaction.getAmount().doubleValue())
                .build();
        walletService.deductCommission(event);
    }

    private boolean verifyIpnSignature(MomoIpnRequest req) {
        try {
            String rawSignature = "accessKey=" + momoConfig.getAccessKey() + "&amount=" + req.getAmount()
                    + "&extraData=" + req.getExtraData() + "&message=" + req.getMessage()
                    + "&orderId=" + req.getOrderId() + "&orderInfo=" + req.getOrderInfo()
                    + "&orderType=" + req.getOrderType() + "&partnerCode=" + req.getPartnerCode()
                    + "&payType=" + req.getPayType() + "&requestId=" + req.getRequestId()
                    + "&responseTime=" + req.getResponseTime() + "&resultCode=" + req.getResultCode()
                    + "&transId=" + req.getTransId();
            return momoEncoder.signHmacSHA256(rawSignature, momoConfig.getSecretKey()).equals(req.getSignature());
        } catch (Exception e) {
            return false;
        }
    }

    private Long parseDataFromExtra(String extraData, String key) {
        if (extraData == null || extraData.isEmpty()) return null;
        try {
            for (String param : extraData.split(";")) {
                if (param.startsWith(key)) return Long.parseLong(param.split("=")[1]);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private MomoCreateResponse executeMomoApi(String orderId, String requestId, BigDecimal amount, String orderInfo, String requestType, String extraData) {
        try {
            String amountStr = String.valueOf(amount.setScale(0, RoundingMode.HALF_UP).longValue());
            String rawSignature = "accessKey=" + momoConfig.getAccessKey() + "&amount=" + amountStr
                    + "&extraData=" + extraData + "&ipnUrl=" + momoConfig.getIpnUrl() + "&orderId=" + orderId
                    + "&orderInfo=" + orderInfo + "&partnerCode=" + momoConfig.getPartnerCode()
                    + "&redirectUrl=" + momoConfig.getRedirectUrl() + "&requestId=" + requestId + "&requestType=" + requestType;
            String signature = momoEncoder.signHmacSHA256(rawSignature, momoConfig.getSecretKey());

            MomoCreateRequest request = MomoCreateRequest.builder().partnerCode(momoConfig.getPartnerCode())
                    .requestId(requestId).amount(amount.longValue()).orderId(orderId).orderInfo(orderInfo)
                    .redirectUrl(momoConfig.getRedirectUrl()).ipnUrl(momoConfig.getIpnUrl()).requestType(requestType)
                    .extraData(extraData).lang("vi").signature(signature).build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<MomoCreateRequest> entity = new HttpEntity<>(request, headers);

            // Gọi API MoMo
            URI targetUrl = URI.create(momoConfig.getEndpoint());

            return restTemplate.postForEntity(targetUrl, entity, MomoCreateResponse.class).getBody();
        } catch (HttpStatusCodeException ex) {
            log.error("MoMo API báo lỗi HTTP Status: {}, Response Body: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new RuntimeException("MoMo từ chối yêu cầu: " + ex.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Lỗi hệ thống khi gọi MoMo: ", e);
            throw new RuntimeException("Lỗi kết nối MoMo do hệ thống nội bộ");
        }
    }
}