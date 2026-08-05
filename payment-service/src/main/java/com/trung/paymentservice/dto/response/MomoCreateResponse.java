package com.trung.paymentservice.dto.response;

import lombok.Data;

@Data
public class MomoCreateResponse {
    private String partnerCode;
    private String requestId;
    private String orderId;
    private Long amount;
    private Long responseTime;
    private String message;
    private Integer resultCode;
    private String payUrl;     
    private String shortLink;
}