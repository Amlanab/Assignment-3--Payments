package com.payment.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CancelRequest {
    
    @NotNull(message = "Authorization transaction ID is required")
    private Long authorizationTransactionId;
    
    private String reason;
}

