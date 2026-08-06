package com.trung.paymentservice.controller;

import com.trung.paymentservice.dto.request.MomoIpnRequest;
import com.trung.paymentservice.dto.response.PaymentUrlResponse;
import com.trung.paymentservice.dto.response.WalletResponse;
import com.trung.paymentservice.entity.Transaction;
import com.trung.paymentservice.mapper.PaymentMapper;
import com.trung.paymentservice.repository.TransactionRepository;
import com.trung.paymentservice.service.WalletService;
import com.trung.paymentservice.service.impl.MomoService;
import com.trung.paymentservice.service.impl.VnpayService;
import com.trung.paymentservice.strategy.PaymentFactory;
import com.trung.paymentservice.strategy.PaymentStrategy;
import com.trung.paymentservice.util.enums.TransactionStatus;
import com.trung.paymentservice.util.enums.TransactionType;
import com.trung.paymentservice.util.enums.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final WalletService walletService;
    private final PaymentMapper paymentMapper;
    private final MomoService momoService;
    private final VnpayService vnpayService;
    private final TransactionRepository transactionRepository;
    private final PaymentFactory paymentFactory;

    @GetMapping("/wallet")
    public ResponseEntity<WalletResponse> getMyWallet(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String roleStr) {
        UserType userType = UserType.valueOf(roleStr.toUpperCase());
        return ResponseEntity.ok(paymentMapper.toWalletResponse(walletService.getOrCreateWallet(userId, userType)));
    }

    @GetMapping("/booking/{bookingId}/payment-status")
    public ResponseEntity<String> checkPaymentStatus(@PathVariable Long bookingId) {
        boolean isSuccess = transactionRepository.findByBookingId(bookingId)
                .stream()
                .anyMatch(tx -> tx.getTransactionType() == TransactionType.TRIP_PAYMENT
                        && tx.getStatus() == TransactionStatus.SUCCESS);
        return ResponseEntity.ok(isSuccess ? "SUCCESS" : "PENDING");
    }

    @PostMapping("/payment/create")
    public ResponseEntity<PaymentUrlResponse> createUniversalPayment(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam String method,
            @RequestParam String type,
            @RequestParam(required = false) Long bookingId,
            @RequestParam(required = false) Long driverId,
            @RequestParam BigDecimal amount) {

        log.info("Khởi tạo thanh toán. Cổng: {}, Luồng: {}, Số tiền: {}", method, type, amount);

        PaymentStrategy strategy = paymentFactory.getStrategy(method);
        PaymentUrlResponse response = strategy.createPayment(userId, driverId, bookingId, amount, type);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<Transaction>> getTransactions(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String roleStr) {
        UserType userType = UserType.valueOf(roleStr.toUpperCase());
        var wallet = walletService.getOrCreateWallet(userId, userType);
        return ResponseEntity.ok(transactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId()));
    }

    @PostMapping("/driver/withdraw")
    public ResponseEntity<Void> withdrawWallet(
            @RequestHeader("X-User-Id") Long driverId,
            @RequestParam BigDecimal amount) {
        walletService.withdrawWallet(driverId, amount);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/momo/ipn")
    public ResponseEntity<Void> handleMomoIPN(@RequestBody MomoIpnRequest request) {
        momoService.processMomoIpn(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/momo/return")
    public ResponseEntity<String> handleMomoReturn(
            @RequestParam("orderId") String orderId,
            @RequestParam("resultCode") Integer resultCode,
            @RequestParam(value = "message", defaultValue = "") String message,
            @RequestParam(value = "extraData", defaultValue = "") String extraData) {
        momoService.processMomoReturn(orderId, resultCode, extraData);
        return renderReturnHtml(resultCode == 0 ? "00" : "FAIL", orderId, message);
    }

    @GetMapping("/vnpay/return")
    public ResponseEntity<String> handleVnpayReturn(
            @RequestParam("vnp_TxnRef") String orderId,
            @RequestParam("vnp_ResponseCode") String responseCode,
            @RequestParam("vnp_OrderInfo") String orderInfo) {

        vnpayService.processVnpayReturn(orderId, responseCode, orderInfo);
        return renderReturnHtml(responseCode, orderId, "Giao dịch kết thúc");
    }

    @PutMapping("/booking/{bookingId}/cancel-pending")
    public ResponseEntity<Void> cancelPendingPayments(@PathVariable Long bookingId) {
        log.info("Yêu cầu hủy các giao dịch PENDING của cuốc xe #{}", bookingId);
        walletService.cancelPendingTransactionsByBookingId(bookingId);
        return ResponseEntity.ok().build();
    }

    private ResponseEntity<String> renderReturnHtml(String code, String orderId, String subText) {
        String title = "00".equals(code) ? "Thanh Toán Thành Công!" : "Thanh Toán Thất Bại";
        String icon = "00".equals(code) ? "✅" : "❌";
        String color = "00".equals(code) ? "#2e7d32" : "#d32f2f";

        String html = """
                <!DOCTYPE html><html><head><meta charset='UTF-8'><title>Kết Quả</title>
                <style>body{font-family:'Segoe UI';background:#f4f6f9;display:flex;justify-content:center;align-items:center;height:100vh;margin:0;}
                .card{background:#fff;padding:40px;border-radius:16px;box-shadow:0 10px 25px rgba(0,0,0,0.08);text-align:center;max-width:400px;width:90%%;}
                h2{color:%s;}.btn{display:inline-block;background:#ae2070;color:#white;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:bold;}</style></head>
                <body><div class='card'><h2>%s %s</h2><p>Mã đơn: %s</p><p>%s</p><a href='javascript:window.close()' class='btn' style='color:white;'>Đóng Cửa Sổ</a></div></body></html>
                """.formatted(color, icon, title, orderId, subText);
        return ResponseEntity.ok().header("Content-Type", "text/html; charset=UTF-8").body(html);
    }
}