package com.trung.paymentservice.strategy;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentFactory {
    private final Map<String, PaymentStrategy> strategies = new HashMap<>();

    public PaymentFactory(List<PaymentStrategy> paymentStrategies) {
        for (PaymentStrategy strategy : paymentStrategies) {
            strategies.put(strategy.getPaymentMethod().toUpperCase(), strategy);
        }
    }

    public PaymentStrategy getStrategy(String method) {
        PaymentStrategy strategy = strategies.get(method.toUpperCase());
        if (strategy == null) {
            throw new IllegalArgumentException("Hệ thống chưa hỗ trợ phương thức thanh toán: " + method);
        }
        return strategy;
    }
}