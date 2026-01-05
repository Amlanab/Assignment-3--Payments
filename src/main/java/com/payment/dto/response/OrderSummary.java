package com.payment.dto.response;

import com.payment.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderSummary {
    private Long id;
    private String orderNumber;
    private OrderStatus status;
    private BigDecimal totalAmount;
}

