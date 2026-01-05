package com.payment.entity;

import com.payment.enums.TransactionStatus;
import com.payment.enums.TransactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class PaymentTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_transaction_id")
    private PaymentTransaction parentTransaction;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TransactionType transactionType;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;
    
    @Column(unique = true, length = 50)
    private String authorizeNetTransactionId;
    
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";
    
    @Column(precision = 19, scale = 4)
    private BigDecimal authorizedAmount;
    
    @Column(precision = 19, scale = 4)
    private BigDecimal capturedAmount;
    
    @Column(precision = 19, scale = 4)
    private BigDecimal refundedAmount;
    
    @Column(columnDefinition = "TEXT")
    private String paymentMethodEncrypted;
    
    @Column(length = 4)
    private String lastFourDigits;
    
    @Column(length = 50)
    private String cardBrand;
    
    private Integer cardExpiryMonth;
    
    private Integer cardExpiryYear;
    
    @Column(unique = true, length = 255)
    private String idempotencyKey;
    
    private LocalDateTime authorizationExpiresAt;
    
    @Column(length = 50)
    private String errorCode;
    
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    private String gatewayResponse;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

