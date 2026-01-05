package com.payment.dto.response;

import com.payment.enums.TransactionStatus;
import com.payment.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {
    private Long transactionId;
    private Long orderId;
    private TransactionType transactionType;
    private TransactionStatus status;
    private BigDecimal amount;
    private String currency;
    private String gatewayTransactionId;
    private String authorizationCode;
    private String lastFourDigits;
    private String cardBrand;
    private LocalDateTime createdAt;
    private OrderSummary order;
    
    // Error information (only present when status is FAILED)
    private String errorCode;
    private String errorMessage;
}

