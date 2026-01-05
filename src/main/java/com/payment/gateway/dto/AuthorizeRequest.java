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
public class AuthorizeRequest {
    private BigDecimal amount;
    private String currency;
    private CreditCardInfo creditCard;
    private String invoiceNumber;
    private String description;
}

