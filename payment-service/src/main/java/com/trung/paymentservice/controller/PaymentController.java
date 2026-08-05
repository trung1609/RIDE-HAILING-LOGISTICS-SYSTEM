package com.trung.paymentservice.controller;

import com.trung.paymentservice.dto.request.MomoIpnRequest;
import com.trung.paymentservice.dto.response.MomoCreateResponse;
import com.trung.paymentservice.dto.response.WalletResponse;
import com.trung.paymentservice.entity.Transaction;
import com.trung.paymentservice.mapper.PaymentMapper;
import com.trung.paymentservice.repository.TransactionRepository;
import com.trung.paymentservice.service.WalletService;
import com.trung.paymentservice.service.impl.MomoService;
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
    private final TransactionRepository transactionRepository;

    @GetMapping("/wallet")
    public ResponseEntity<WalletResponse> getMyWallet(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String roleStr) {

        UserType userType = UserType.valueOf(roleStr.toUpperCase());
        WalletResponse response = paymentMapper.toWalletResponse(walletService.getOrCreateWallet(userId, userType));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/driver/deposit")
    public ResponseEntity<MomoCreateResponse> depositWallet(
            @RequestHeader("X-User-Id") Long driverId,
            @RequestParam BigDecimal amount) {

        log.info("Tài xế ID {} gửi yêu cầu nạp {} VND vào ví qua MoMo", driverId, amount);
        MomoCreateResponse response = momoService.createDriverDepositRequest(driverId, amount);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/customer/pay-trip")
    public ResponseEntity<MomoCreateResponse> payTripByMomo(
            @RequestHeader("X-User-Id") Long customerId,
            @RequestParam Long bookingId,
            @RequestParam Long driverId,
            @RequestParam BigDecimal amount) {

        log.info("Khách hàng {} yêu cầu thanh toán MoMo cho chuyến đi #{}, Tài xế hưởng: {}, Số tiền: {}", customerId, bookingId, driverId, amount);
        MomoCreateResponse response = momoService.createTripPaymentRequest(customerId, driverId, bookingId, amount);
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

        String title, icon, color, subText;
        if (resultCode == 0) {
            title = "Thanh Toán Thành Công!";
            icon = "✅";
            color = "#2e7d32";
            subText = "Giao dịch của bạn đã được hệ thống xác nhận thành công.";
        } else if (resultCode == 1006) {
            title = "Đã Hủy Giao Dịch";
            icon = "🛑";
            color = "#ed6c02";
            subText = "Bạn đã chủ động hủy quá trình thanh toán trên MoMo.";
        } else {
            title = "Thanh Toán Thất Bại";
            icon = "❌";
            color = "#d32f2f";
            subText = "Lỗi: " + message;
        }

        String html = """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Kết Quả Thanh Toán MoMo</title>
                    <style>
                        body { font-family: 'Segoe UI', Roboto, sans-serif; background-color: #f4f6f9; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; }
                        .card { background: #ffffff; padding: 40px; border-radius: 16px; box-shadow: 0 10px 25px rgba(0,0,0,0.08); text-align: center; max-width: 400px; width: 90%%; }
                        .icon { font-size: 64px; margin-bottom: 10px; }
                        h2 { color: %s; margin-bottom: 12px; font-size: 24px; }
                        p { color: #555; font-size: 15px; line-height: 1.5; margin-bottom: 25px; }
                        .order-id { background: #f8f9fa; border: 1px dashed #ccc; padding: 8px 12px; border-radius: 6px; font-family: monospace; font-size: 13px; color: #333; margin-bottom: 20px; }
                        .btn { display: inline-block; background-color: #ae2070; color: white; text-decoration: none; padding: 12px 28px; border-radius: 8px; font-weight: bold; font-size: 15px; transition: background 0.2s; }
                        .btn:hover { background-color: #8c1958; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <div class="icon">%s</div>
                        <h2>%s</h2>
                        <div class="order-id">Mã đơn: %s</div>
                        <p>%s</p>
                        <a href="javascript:window.close()" class="btn">Đóng Cửa Sổ</a>
                    </div>
                </body>
                </html>
                """.formatted(color, icon, title, orderId, subText);

        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(html);
    }
}