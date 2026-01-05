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
public class CaptureResponse {
    private boolean success;
    private String transactionId;
    private String responseCode;
    private String responseMessage;
    private String errorCode;
    private String errorMessage;
    private BigDecimal amount;
    private String currency;
}

