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
public class PurchaseResponse {
    private boolean success;
    private String transactionId;
    private String authorizationCode;
    private String responseCode;
    private String responseMessage;
    private String errorCode;
    private String errorMessage;
    private String avsResultCode;
    private String cvvResultCode;
    private String accountNumber; // Last 4 digits
    private String accountType; // Card brand
    private BigDecimal amount;
    private String currency;
}

