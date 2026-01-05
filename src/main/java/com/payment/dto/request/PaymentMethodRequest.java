package com.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PaymentMethodRequest {
    
    @NotBlank(message = "Card number is required")
    @Pattern(regexp = "\\d{13,19}", message = "Card number must be 13-19 digits")
    private String cardNumber;
    
    @NotBlank(message = "Expiry month is required")
    @Pattern(regexp = "^(0[1-9]|1[0-2])$", message = "Expiry month must be 01-12")
    private String expiryMonth;
    
    @NotBlank(message = "Expiry year is required")
    @Pattern(regexp = "\\d{4}", message = "Expiry year must be 4 digits")
    private String expiryYear;
    
    @NotBlank(message = "CVV is required")
    @Size(min = 3, max = 4, message = "CVV must be 3-4 digits")
    private String cvv;
    
    @NotBlank(message = "Cardholder name is required")
    private String cardholderName;
}

