package com.payment.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditCardInfo {
    private String cardNumber;
    private String expirationDate; // Format: YYYY-MM
    private String cardCode; // CVV
    private String cardholderName;
}

