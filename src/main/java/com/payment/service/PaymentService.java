package com.payment.service;

import com.payment.dto.request.*;
import com.payment.dto.request.CaptureRequest;
import com.payment.dto.request.RefundRequest;
import com.payment.dto.response.OrderSummary;
import com.payment.dto.response.PaymentResponse;
import com.payment.entity.Order;
import com.payment.entity.PaymentTransaction;
import com.payment.entity.User;
import com.payment.enums.OrderStatus;
import com.payment.enums.TransactionStatus;
import com.payment.enums.TransactionType;
import com.payment.exception.PaymentException;
import com.payment.exception.PaymentNotFoundException;
import com.payment.exception.PaymentValidationException;
import com.payment.gateway.PaymentGateway;
import com.payment.gateway.dto.*;
import com.payment.repository.OrderRepository;
import com.payment.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {
    
    private final PaymentGateway paymentGateway;
    private final OrderRepository orderRepository;
    private final PaymentTransactionRepository transactionRepository;
    
    @Transactional
    public PaymentResponse purchase(PaymentRequest request, User user) {
        log.info("Processing purchase for orderId={}, userId={}", request.getOrderId(), user.getId());
        
        // Validate idempotency
        if (request.getIdempotencyKey() != null) {
            transactionRepository.findByIdempotencyKey(request.getIdempotencyKey())
                    .ifPresent(existing -> {
                        throw new PaymentValidationException("Duplicate transaction detected");
                    });
        }
        
        // Load order
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new PaymentNotFoundException("Order not found: " + request.getOrderId()));
        
        // Validate order ownership
        if (!order.getUser().getId().equals(user.getId())) {
            throw new PaymentValidationException("Order does not belong to user");
        }
        
        // Validate order status
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new PaymentValidationException("Order is not in PENDING status");
        }
        
        // Validate amount
        if (request.getAmount().compareTo(order.getTotalAmount()) != 0) {
            throw new PaymentValidationException("Amount does not match order total");
        }
        
        // Create transaction entity
        String idempotencyKey = request.getIdempotencyKey() != null ? 
                request.getIdempotencyKey() : UUID.randomUUID().toString();
        
        PaymentTransaction transaction = PaymentTransaction.builder()
                .order(order)
                .transactionType(TransactionType.PURCHASE)
                .status(TransactionStatus.PENDING)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .lastFourDigits(extractLastFour(request.getPaymentMethod().getCardNumber()))
                .cardExpiryMonth(Integer.parseInt(request.getPaymentMethod().getExpiryMonth()))
                .cardExpiryYear(Integer.parseInt(request.getPaymentMethod().getExpiryYear()))
                .idempotencyKey(idempotencyKey)
                .refundedAmount(BigDecimal.ZERO)
                .build();
        
        transaction = transactionRepository.save(transaction);
        
        try {
            // Call gateway
            PurchaseRequest gatewayRequest = PurchaseRequest.builder()
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .creditCard(convertCreditCardInfo(request.getPaymentMethod()))
                    .invoiceNumber(order.getOrderNumber())
                    .description(request.getDescription())
                    .build();
            
            PurchaseResponse gatewayResponse = paymentGateway.purchase(gatewayRequest);
            
            // Update transaction
            if (gatewayResponse.isSuccess()) {
                transaction.setStatus(TransactionStatus.SUCCESS);
                transaction.setAuthorizeNetTransactionId(gatewayResponse.getTransactionId());
                transaction.setLastFourDigits(extractLastFour(gatewayResponse.getAccountNumber()));
                transaction.setCardBrand(gatewayResponse.getAccountType());
                transaction.setRefundedAmount(BigDecimal.ZERO);
                
                // Update order
                order.setStatus(OrderStatus.COMPLETED);
                orderRepository.save(order);
            } else {
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setErrorCode(gatewayResponse.getErrorCode());
                transaction.setErrorMessage(gatewayResponse.getErrorMessage());
                
                order.setStatus(OrderStatus.FAILED);
                orderRepository.save(order);
            }
            
            transaction = transactionRepository.save(transaction);
            
            return mapToPaymentResponse(transaction, order);
            
        } catch (Exception e) {
            log.error("Error processing purchase", e);
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setErrorMessage("Gateway error: " + e.getMessage());
            transaction = transactionRepository.save(transaction);
            
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
            
            throw new PaymentException("Payment processing failed: " + e.getMessage(), e);
        }
    }
    
    @Transactional
    public PaymentResponse authorize(PaymentRequest request, User user) {
        log.info("Processing authorization for orderId={}, userId={}", request.getOrderId(), user.getId());
        
        // Validate idempotency
        if (request.getIdempotencyKey() != null) {
            transactionRepository.findByIdempotencyKey(request.getIdempotencyKey())
                    .ifPresent(existing -> {
                        throw new PaymentValidationException("Duplicate transaction detected");
                    });
        }
        
        // Load order
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new PaymentNotFoundException("Order not found: " + request.getOrderId()));
        
        // Validate order ownership
        if (!order.getUser().getId().equals(user.getId())) {
            throw new PaymentValidationException("Order does not belong to user");
        }
        
        // Validate order status
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new PaymentValidationException("Order is not in PENDING status");
        }
        
        // Validate amount
        if (request.getAmount().compareTo(order.getTotalAmount()) != 0) {
            throw new PaymentValidationException("Amount does not match order total");
        }
        
        // Create transaction entity
        String idempotencyKey = request.getIdempotencyKey() != null ? 
                request.getIdempotencyKey() : UUID.randomUUID().toString();
        
        PaymentTransaction transaction = PaymentTransaction.builder()
                .order(order)
                .transactionType(TransactionType.AUTHORIZE)
                .status(TransactionStatus.PENDING)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .authorizedAmount(request.getAmount())
                .capturedAmount(BigDecimal.ZERO)
                .lastFourDigits(extractLastFour(request.getPaymentMethod().getCardNumber()))
                .cardExpiryMonth(Integer.parseInt(request.getPaymentMethod().getExpiryMonth()))
                .cardExpiryYear(Integer.parseInt(request.getPaymentMethod().getExpiryYear()))
                .idempotencyKey(idempotencyKey)
                .authorizationExpiresAt(LocalDateTime.now().plusDays(30))
                .build();
        
        transaction = transactionRepository.save(transaction);
        
        try {
            // Call gateway
            AuthorizeRequest gatewayRequest = AuthorizeRequest.builder()
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .creditCard(convertCreditCardInfo(request.getPaymentMethod()))
                    .invoiceNumber(order.getOrderNumber())
                    .description(request.getDescription())
                    .build();
            
            AuthorizeResponse gatewayResponse = paymentGateway.authorize(gatewayRequest);
            
            // Update transaction
            if (gatewayResponse.isSuccess()) {
                transaction.setStatus(TransactionStatus.SUCCESS);
                transaction.setAuthorizeNetTransactionId(gatewayResponse.getTransactionId());
                transaction.setLastFourDigits(extractLastFour(gatewayResponse.getAccountNumber()));
                transaction.setCardBrand(gatewayResponse.getAccountType());
                
                // Update order
                order.setStatus(OrderStatus.PROCESSING);
                orderRepository.save(order);
            } else {
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setErrorCode(gatewayResponse.getErrorCode());
                transaction.setErrorMessage(gatewayResponse.getErrorMessage());
                
                order.setStatus(OrderStatus.FAILED);
                orderRepository.save(order);
            }
            
            transaction = transactionRepository.save(transaction);
            
            return mapToPaymentResponse(transaction, order);
            
        } catch (Exception e) {
            log.error("Error processing authorization", e);
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setErrorMessage("Gateway error: " + e.getMessage());
            transaction = transactionRepository.save(transaction);
            
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
            
            throw new PaymentException("Authorization failed: " + e.getMessage(), e);
        }
    }
    
    @Transactional
    public PaymentResponse capture(CaptureRequest request, User user) {
        log.info("Processing capture for authorizationTransactionId={}, userId={}", 
                request.getAuthorizationTransactionId(), user.getId());
        
        // Load authorization transaction
        PaymentTransaction authTransaction = transactionRepository.findById(request.getAuthorizationTransactionId())
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Authorization transaction not found: " + request.getAuthorizationTransactionId()));
        
        // Validate ownership
        if (!authTransaction.getOrder().getUser().getId().equals(user.getId())) {
            throw new PaymentValidationException("Transaction does not belong to user");
        }
        
        // Validate transaction type
        if (authTransaction.getTransactionType() != TransactionType.AUTHORIZE) {
            throw new PaymentValidationException("Transaction is not an authorization");
        }
        
        // Validate status
        if (authTransaction.getStatus() != TransactionStatus.SUCCESS) {
            throw new PaymentValidationException("Authorization is not in SUCCESS status");
        }
        
        // Validate expiration
        if (authTransaction.getAuthorizationExpiresAt() != null 
                && authTransaction.getAuthorizationExpiresAt().isBefore(LocalDateTime.now())) {
            throw new PaymentValidationException("Authorization has expired");
        }
        
        // Validate amount
        BigDecimal remainingAmount = authTransaction.getAuthorizedAmount()
                .subtract(authTransaction.getCapturedAmount() != null ? 
                        authTransaction.getCapturedAmount() : BigDecimal.ZERO);
        if (request.getAmount().compareTo(remainingAmount) > 0) {
            throw new PaymentValidationException("Capture amount exceeds remaining authorized amount");
        }
        
        // Validate currency
        if (!request.getCurrency().equals(authTransaction.getCurrency())) {
            throw new PaymentValidationException("Currency mismatch");
        }
        
        // Validate idempotency
        if (request.getIdempotencyKey() != null) {
            transactionRepository.findByIdempotencyKey(request.getIdempotencyKey())
                    .ifPresent(existing -> {
                        throw new PaymentValidationException("Duplicate transaction detected");
                    });
        }
        
        // Create capture transaction
        String idempotencyKey = request.getIdempotencyKey() != null ? 
                request.getIdempotencyKey() : UUID.randomUUID().toString();
        
        PaymentTransaction captureTransaction = PaymentTransaction.builder()
                .order(authTransaction.getOrder())
                .parentTransaction(authTransaction)
                .transactionType(TransactionType.CAPTURE)
                .status(TransactionStatus.PENDING)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .idempotencyKey(idempotencyKey)
                .build();
        
        captureTransaction = transactionRepository.save(captureTransaction);
        
        try {
            // Call gateway
            com.payment.gateway.dto.CaptureRequest gatewayRequest = com.payment.gateway.dto.CaptureRequest.builder()
                    .authorizationTransactionId(authTransaction.getAuthorizeNetTransactionId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .build();
            
            CaptureResponse gatewayResponse = paymentGateway.capture(gatewayRequest);
            
            // Update capture transaction
            if (gatewayResponse.isSuccess()) {
                captureTransaction.setStatus(TransactionStatus.SUCCESS);
                // Note: Don't set authorize_net_transaction_id on capture - it's the same as the parent authorization
                // The parent authorization transaction already has the transaction ID
                
                // Update authorization transaction
                BigDecimal newCapturedAmount = authTransaction.getCapturedAmount() != null ?
                        authTransaction.getCapturedAmount().add(request.getAmount()) :
                        request.getAmount();
                authTransaction.setCapturedAmount(newCapturedAmount);
                transactionRepository.save(authTransaction);
                
                // Update order
                Order order = authTransaction.getOrder();
                if (newCapturedAmount.compareTo(authTransaction.getAuthorizedAmount()) >= 0) {
                    order.setStatus(OrderStatus.COMPLETED);
                } else {
                    order.setStatus(OrderStatus.PARTIALLY_CAPTURED);
                }
                orderRepository.save(order);
            } else {
                captureTransaction.setStatus(TransactionStatus.FAILED);
                captureTransaction.setErrorCode(gatewayResponse.getErrorCode());
                captureTransaction.setErrorMessage(gatewayResponse.getErrorMessage());
            }
            
            captureTransaction = transactionRepository.save(captureTransaction);
            
            return mapToPaymentResponse(captureTransaction, authTransaction.getOrder());
            
        } catch (Exception e) {
            log.error("Error processing capture", e);
            captureTransaction.setStatus(TransactionStatus.FAILED);
            captureTransaction.setErrorMessage("Gateway error: " + e.getMessage());
            captureTransaction = transactionRepository.save(captureTransaction);
            
            throw new PaymentException("Capture failed: " + e.getMessage(), e);
        }
    }
    
    @Transactional
    public PaymentResponse cancel(CancelRequest request, User user) {
        log.info("Processing cancel for authorizationTransactionId={}, userId={}", 
                request.getAuthorizationTransactionId(), user.getId());
        
        // Load authorization transaction
        PaymentTransaction authTransaction = transactionRepository.findById(request.getAuthorizationTransactionId())
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Authorization transaction not found: " + request.getAuthorizationTransactionId()));
        
        // Validate ownership
        if (!authTransaction.getOrder().getUser().getId().equals(user.getId())) {
            throw new PaymentValidationException("Transaction does not belong to user");
        }
        
        // Validate transaction type
        if (authTransaction.getTransactionType() != TransactionType.AUTHORIZE) {
            throw new PaymentValidationException("Transaction is not an authorization");
        }
        
        // Validate status
        if (authTransaction.getStatus() != TransactionStatus.SUCCESS) {
            throw new PaymentValidationException("Authorization is not in SUCCESS status");
        }
        
        // Validate not captured
        BigDecimal capturedAmount = authTransaction.getCapturedAmount() != null ?
                authTransaction.getCapturedAmount() : BigDecimal.ZERO;
        if (capturedAmount.compareTo(BigDecimal.ZERO) > 0) {
            throw new PaymentValidationException("Cannot void: authorization has been captured");
        }
        
        // Validate time window (24 hours)
        long hoursSinceAuth = java.time.Duration.between(
                authTransaction.getCreatedAt(), LocalDateTime.now()).toHours();
        if (hoursSinceAuth > 24) {
            throw new PaymentValidationException("Cannot void: authorization is outside the 24-hour void window");
        }
        
        // Create void transaction
        PaymentTransaction voidTransaction = PaymentTransaction.builder()
                .order(authTransaction.getOrder())
                .parentTransaction(authTransaction)
                .transactionType(TransactionType.VOID)
                .status(TransactionStatus.PENDING)
                .amount(BigDecimal.ZERO)
                .currency(authTransaction.getCurrency())
                .build();
        
        voidTransaction = transactionRepository.save(voidTransaction);
        
        try {
            // Call gateway
            VoidRequest gatewayRequest = VoidRequest.builder()
                    .authorizationTransactionId(authTransaction.getAuthorizeNetTransactionId())
                    .build();
            
            VoidResponse gatewayResponse = paymentGateway.voidTransaction(gatewayRequest);
            
            // Update void transaction
            if (gatewayResponse.isSuccess()) {
                voidTransaction.setStatus(TransactionStatus.SUCCESS);
                
                // Update authorization transaction
                authTransaction.setStatus(TransactionStatus.VOIDED);
                transactionRepository.save(authTransaction);
                
                // Update order
                Order order = authTransaction.getOrder();
                order.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(order);
            } else {
                voidTransaction.setStatus(TransactionStatus.FAILED);
                voidTransaction.setErrorCode(gatewayResponse.getErrorCode());
                voidTransaction.setErrorMessage(gatewayResponse.getErrorMessage());
            }
            
            voidTransaction = transactionRepository.save(voidTransaction);
            
            return mapToPaymentResponse(voidTransaction, authTransaction.getOrder());
            
        } catch (Exception e) {
            log.error("Error processing void", e);
            voidTransaction.setStatus(TransactionStatus.FAILED);
            voidTransaction.setErrorMessage("Gateway error: " + e.getMessage());
            voidTransaction = transactionRepository.save(voidTransaction);
            
            throw new PaymentException("Void failed: " + e.getMessage(), e);
        }
    }
    
    @Transactional
    public PaymentResponse refund(RefundRequest request, User user) {
        log.info("Processing refund for transactionId={}, userId={}", request.getTransactionId(), user.getId());
        
        // Load transaction to refund
        PaymentTransaction originalTransaction = transactionRepository.findById(request.getTransactionId())
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Transaction not found: " + request.getTransactionId()));
        
        // Validate ownership
        if (!originalTransaction.getOrder().getUser().getId().equals(user.getId())) {
            throw new PaymentValidationException("Transaction does not belong to user");
        }
        
        // Validate transaction type
        if (originalTransaction.getTransactionType() != TransactionType.CAPTURE 
                && originalTransaction.getTransactionType() != TransactionType.PURCHASE) {
            throw new PaymentValidationException("Only CAPTURE or PURCHASE transactions can be refunded");
        }
        
        // Validate status
        if (originalTransaction.getStatus() != TransactionStatus.SUCCESS) {
            throw new PaymentValidationException("Transaction is not in SUCCESS status");
        }
        
        // Validate amount
        BigDecimal refundedAmount = originalTransaction.getRefundedAmount() != null ?
                originalTransaction.getRefundedAmount() : BigDecimal.ZERO;
        BigDecimal remainingAmount = originalTransaction.getAmount().subtract(refundedAmount);
        if (request.getAmount().compareTo(remainingAmount) > 0) {
            throw new PaymentValidationException("Refund amount exceeds remaining refundable amount");
        }
        
        // Validate currency
        if (!request.getCurrency().equals(originalTransaction.getCurrency())) {
            throw new PaymentValidationException("Currency mismatch");
        }
        
        // Validate idempotency
        if (request.getIdempotencyKey() != null) {
            transactionRepository.findByIdempotencyKey(request.getIdempotencyKey())
                    .ifPresent(existing -> {
                        throw new PaymentValidationException("Duplicate transaction detected");
                    });
        }
        
        // Create refund transaction
        String idempotencyKey = request.getIdempotencyKey() != null ? 
                request.getIdempotencyKey() : UUID.randomUUID().toString();
        
        PaymentTransaction refundTransaction = PaymentTransaction.builder()
                .order(originalTransaction.getOrder())
                .parentTransaction(originalTransaction)
                .transactionType(TransactionType.REFUND)
                .status(TransactionStatus.PENDING)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .idempotencyKey(idempotencyKey)
                .build();
        
        refundTransaction = transactionRepository.save(refundTransaction);
        
        try {
            // Call gateway
            com.payment.gateway.dto.RefundRequest gatewayRequest = com.payment.gateway.dto.RefundRequest.builder()
                    .transactionId(originalTransaction.getAuthorizeNetTransactionId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .lastFourDigits(originalTransaction.getLastFourDigits())
                    .expirationDate(formatExpirationDate(originalTransaction.getCardExpiryMonth(), 
                            originalTransaction.getCardExpiryYear()))
                    .build();
            
            RefundResponse gatewayResponse = paymentGateway.refund(gatewayRequest);
            
            // Update refund transaction
            if (gatewayResponse.isSuccess()) {
                refundTransaction.setStatus(TransactionStatus.SUCCESS);
                refundTransaction.setAuthorizeNetTransactionId(gatewayResponse.getTransactionId());
                
                // Update original transaction
                BigDecimal newRefundedAmount = refundedAmount.add(request.getAmount());
                originalTransaction.setRefundedAmount(newRefundedAmount);
                transactionRepository.save(originalTransaction);
                
                // Update order
                Order order = originalTransaction.getOrder();
                if (newRefundedAmount.compareTo(originalTransaction.getAmount()) >= 0) {
                    order.setStatus(OrderStatus.REFUNDED);
                } else {
                    order.setStatus(OrderStatus.PARTIALLY_REFUNDED);
                }
                orderRepository.save(order);
            } else {
                refundTransaction.setStatus(TransactionStatus.FAILED);
                refundTransaction.setErrorCode(gatewayResponse.getErrorCode());
                refundTransaction.setErrorMessage(gatewayResponse.getErrorMessage());
            }
            
            refundTransaction = transactionRepository.save(refundTransaction);
            
            return mapToPaymentResponse(refundTransaction, originalTransaction.getOrder());
            
        } catch (Exception e) {
            log.error("Error processing refund", e);
            refundTransaction.setStatus(TransactionStatus.FAILED);
            refundTransaction.setErrorMessage("Gateway error: " + e.getMessage());
            refundTransaction = transactionRepository.save(refundTransaction);
            
            throw new PaymentException("Refund failed: " + e.getMessage(), e);
        }
    }
    
    // Helper methods
    
    private CreditCardInfo convertCreditCardInfo(PaymentMethodRequest paymentMethod) {
        String expirationDate = paymentMethod.getExpiryYear() + "-" + paymentMethod.getExpiryMonth();
        return CreditCardInfo.builder()
                .cardNumber(paymentMethod.getCardNumber())
                .expirationDate(expirationDate)
                .cardCode(paymentMethod.getCvv())
                .cardholderName(paymentMethod.getCardholderName())
                .build();
    }
    
    private String extractLastFour(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return null;
        }
        return cardNumber.substring(cardNumber.length() - 4);
    }
    
    private String formatExpirationDate(Integer month, Integer year) {
        if (month == null || year == null) {
            return null;
        }
        return String.format("%04d-%02d", year, month);
    }
    
    private PaymentResponse mapToPaymentResponse(PaymentTransaction transaction, Order order) {
        return PaymentResponse.builder()
                .transactionId(transaction.getId())
                .orderId(order.getId())
                .transactionType(transaction.getTransactionType())
                .status(transaction.getStatus())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .gatewayTransactionId(transaction.getAuthorizeNetTransactionId())
                .lastFourDigits(transaction.getLastFourDigits())
                .cardBrand(transaction.getCardBrand())
                .createdAt(transaction.getCreatedAt())
                .errorCode(transaction.getErrorCode())
                .errorMessage(transaction.getErrorMessage())
                .order(OrderSummary.builder()
                        .id(order.getId())
                        .orderNumber(order.getOrderNumber())
                        .status(order.getStatus())
                        .totalAmount(order.getTotalAmount())
                        .build())
                .build();
    }
}

