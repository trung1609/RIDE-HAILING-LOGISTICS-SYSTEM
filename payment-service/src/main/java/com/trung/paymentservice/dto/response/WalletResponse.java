package com.trung.paymentservice.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class WalletResponse {
    private Long walletId;
    private BigDecimal balance;
    private String userType;
}