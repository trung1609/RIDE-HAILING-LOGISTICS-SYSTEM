package com.trung.paymentservice.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentUrlResponse {
    private String orderId;
    private String paymentUrl;
}