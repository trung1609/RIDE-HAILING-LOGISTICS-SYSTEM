package com.trung.paymentservice.mapper;

import com.trung.paymentservice.dto.response.WalletResponse;
import com.trung.paymentservice.entity.Wallet;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {
    public WalletResponse toWalletResponse(Wallet wallet) {
        return WalletResponse.builder()
                .walletId(wallet.getId())
                .balance(wallet.getBalance())
                .userType(wallet.getUserType().name())
                .build();
    }
}