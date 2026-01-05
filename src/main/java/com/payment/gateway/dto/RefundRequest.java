package com.payment.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundRequest {
    private String transactionId;
    private BigDecimal amount;
    private String currency;
    private String lastFourDigits;
    private String expirationDate;
}

