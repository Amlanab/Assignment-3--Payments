package com.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RefundRequest {
    
    @NotNull(message = "Transaction ID is required")
    private Long transactionId;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;
    
    @NotNull(message = "Currency is required")
    private String currency;
    
    private String reason;
    
    private String idempotencyKey;
}

