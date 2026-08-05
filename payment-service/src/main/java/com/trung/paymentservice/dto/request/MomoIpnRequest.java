package com.trung.paymentservice.dto.request;

import lombok.Data;

@Data
public class MomoIpnRequest {
    private String partnerCode;
    private String orderId;
    private String requestId;
    private Long amount;
    private String orderInfo;
    private String orderType;
    private String transId;
    private Integer resultCode;
    private String message;
    private String payType;
    private String responseTime;
    private String extraData;
    private String signature;
}