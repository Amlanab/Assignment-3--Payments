package com.payment.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoidResponse {
    private boolean success;
    private String responseCode;
    private String responseMessage;
    private String errorCode;
    private String errorMessage;
}

