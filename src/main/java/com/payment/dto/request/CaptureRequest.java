package com.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CaptureRequest {
    
    @NotNull(message = "Authorization transaction ID is required")
    private Long authorizationTransactionId;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;
    
    @NotNull(message = "Currency is required")
    private String currency;
    
    private String idempotencyKey;
}

