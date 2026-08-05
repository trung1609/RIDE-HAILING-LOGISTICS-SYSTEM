package com.trung.paymentservice.service.impl;

import com.trung.paymentservice.config.MomoConfig;
import com.trung.paymentservice.dto.request.MomoCreateRequest;
import com.trung.paymentservice.dto.request.MomoIpnRequest;
import com.trung.paymentservice.dto.response.MomoCreateResponse;
import com.trung.paymentservice.entity.Transaction;
import com.trung.paymentservice.entity.Wallet;
import com.trung.paymentservice.repository.TransactionRepository;
import com.trung.paymentservice.service.MomoEncoder;
import com.trung.paymentservice.service.WalletService;
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
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MomoService {

    private final MomoConfig momoConfig;
    private final MomoEncoder momoEncoder;
    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final RestTemplate restTemplate;

    @Transactional
    public MomoCreateResponse createDriverDepositRequest(Long driverId, BigDecimal amount) {
        Wallet wallet = walletService.getOrCreateWallet(driverId, UserType.DRIVER);

        String orderId = "DEPOSIT_" + driverId + "_" + System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        String orderInfo = "Nap tien vi tai xe #" + driverId;
        String requestType = "captureWallet";
        String extraData = "USER_TYPE=DRIVER;USER_ID=" + driverId;

        Transaction transaction = Transaction.builder()
                .walletId(wallet.getId())
                .orderId(orderId)
                .amount(amount)
                .transactionType(TransactionType.DEPOSIT)
                .paymentMethod(PaymentMethod.MOMO)
                .status(TransactionStatus.PENDING)
                .build();
        transactionRepository.save(transaction);

        return executeMomoApi(orderId, requestId, amount, orderInfo, requestType, extraData);
    }

    @Transactional
    public MomoCreateResponse createTripPaymentRequest(Long customerId, Long driverId, Long bookingId, BigDecimal amount) {
        Wallet customerWallet = walletService.getOrCreateWallet(customerId, UserType.CUSTOMER);
        String orderId = "TRIP_" + bookingId + "_" + System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        String orderInfo = "Thanh toan cuoc xe #" + bookingId;
        String requestType = "captureWallet";
        String extraData = "BOOKING_ID=" + bookingId + ";DRIVER_ID=" + driverId;

        Transaction transaction = Transaction.builder()
                .walletId(customerWallet.getId())
                .bookingId(bookingId)
                .orderId(orderId)
                .amount(amount)
                .transactionType(TransactionType.TRIP_PAYMENT)
                .paymentMethod(PaymentMethod.MOMO)
                .status(TransactionStatus.PENDING)
                .build();
        transactionRepository.save(transaction);

        return executeMomoApi(orderId, requestId, amount, orderInfo, requestType, extraData);
    }

    @Transactional
    public void processMomoIpn(MomoIpnRequest ipnRequest) {
        log.info("Nhận thông báo giao dịch OrderId: {}, ResultCode: {}",
                ipnRequest.getOrderId(), ipnRequest.getResultCode());

        Transaction transaction = transactionRepository.findByOrderId(ipnRequest.getOrderId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy mã đơn hàng: " + ipnRequest.getOrderId()));

        if (transaction.getStatus() == TransactionStatus.SUCCESS) {
            log.warn("Đơn hàng {} đã xử lý thành công trước đó, bỏ qua trùng lặp IPN.", ipnRequest.getOrderId());
            return;
        }

        if (ipnRequest.getResultCode() != null && ipnRequest.getResultCode() == 0) {
            transaction.setStatus(TransactionStatus.SUCCESS);
            transaction.setGatewayTransId(ipnRequest.getTransId());
            transactionRepository.save(transaction);

            if (transaction.getTransactionType() == TransactionType.DEPOSIT) {
                Long driverId = parseDataFromExtra(ipnRequest.getExtraData(), "USER_ID=");
                if (driverId != null) walletService.creditWallet(driverId, UserType.DRIVER, transaction.getAmount());
            } else if (transaction.getTransactionType() == TransactionType.TRIP_PAYMENT) {
                Long driverId = parseDataFromExtra(ipnRequest.getExtraData(), "DRIVER_ID=");
                creditDriverForTrip(driverId, transaction);
            }
            log.info("Giao dịch {} hoàn tất thành công!", ipnRequest.getOrderId());
        } else {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            log.warn("Giao dịch {} thất bại hoặc bị hủy.", ipnRequest.getOrderId());
        }
    }

    @Transactional
    public void processMomoReturn(String orderId, Integer resultCode, String extraData) {
        Transaction transaction = transactionRepository.findByOrderId(orderId).orElse(null);
        if (transaction == null || transaction.getStatus() == TransactionStatus.SUCCESS) {
            return;
        }

        if (resultCode == 0) {
            transaction.setStatus(TransactionStatus.SUCCESS);
            transactionRepository.save(transaction);

            if (transaction.getTransactionType() == TransactionType.DEPOSIT) {
                Long driverId = parseDataFromExtra(extraData, "USER_ID=");
                if (driverId != null) walletService.creditWallet(driverId, UserType.DRIVER, transaction.getAmount());
            } else if (transaction.getTransactionType() == TransactionType.TRIP_PAYMENT) {
                Long driverId = parseDataFromExtra(extraData, "DRIVER_ID=");
                creditDriverForTrip(driverId, transaction);
            }
        } else if (resultCode == 1006) {
            log.info("Khách hàng đã chủ động hủy giao dịch MoMo: {}", orderId);
            transaction.setStatus(TransactionStatus.CANCELED);
        } else {
            log.warn("Giao dịch MoMo thất bại với mã lỗi {}: {}", resultCode, orderId);
            transaction.setStatus(TransactionStatus.FAILED);
        }
        transactionRepository.save(transaction);
    }

    private void creditDriverForTrip(Long driverId, Transaction customerTransaction) {
        if (driverId == null) return;
        Wallet driverWallet = walletService.getOrCreateWallet(driverId, UserType.DRIVER);

        walletService.creditWallet(driverId, UserType.DRIVER, customerTransaction.getAmount());

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
    }

    private Long parseDataFromExtra(String extraData, String key) {
        if (extraData == null || extraData.isEmpty()) return null;
        try {
            for (String param : extraData.split(";")) {
                if (param.startsWith(key)) {
                    return Long.parseLong(param.split("=")[1]);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private MomoCreateResponse executeMomoApi(String orderId, String requestId, BigDecimal amount,
                                              String orderInfo, String requestType, String extraData) {
        try {
            String rawSignature = "accessKey=" + momoConfig.getAccessKey()
                    + "&amount=" + amount.longValue()
                    + "&extraData=" + extraData
                    + "&ipnUrl=" + momoConfig.getIpnUrl()
                    + "&orderId=" + orderId
                    + "&orderInfo=" + orderInfo
                    + "&partnerCode=" + momoConfig.getPartnerCode()
                    + "&redirectUrl=" + momoConfig.getRedirectUrl()
                    + "&requestId=" + requestId
                    + "&requestType=" + requestType;

            String signature = momoEncoder.signHmacSHA256(rawSignature, momoConfig.getSecretKey());

            MomoCreateRequest request = MomoCreateRequest.builder()
                    .partnerCode(momoConfig.getPartnerCode())
                    .requestId(requestId)
                    .amount(amount.longValue())
                    .orderId(orderId)
                    .orderInfo(orderInfo)
                    .redirectUrl(momoConfig.getRedirectUrl())
                    .ipnUrl(momoConfig.getIpnUrl())
                    .requestType(requestType)
                    .extraData(extraData)
                    .lang("vi")
                    .signature(signature)
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<MomoCreateRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<MomoCreateResponse> response = restTemplate.postForEntity(
                    momoConfig.getEndpoint(), entity, MomoCreateResponse.class);

            log.info("Đã tạo yêu cầu thanh toán MoMo thành công cho Order #{}. URL: {}",
                    orderId, response.getBody() != null ? response.getBody().getPayUrl() : "NULL");

            return response.getBody();

        } catch (Exception e) {
            log.error("Lỗi khi gọi MoMo Gateway", e);
            throw new RuntimeException("Không thể tạo thanh toán MoMo lúc này!");
        }
    }

    private Long parseUserIdFromExtraData(String extraData) {
        try {
            for (String param : extraData.split(";")) {
                if (param.startsWith("USER_ID=")) {
                    return Long.parseLong(param.split("=")[1]);
                }
            }
        } catch (Exception ignored) {
        }
        throw new RuntimeException("Không thể phân tích USER_ID từ extraData: " + extraData);
    }
}