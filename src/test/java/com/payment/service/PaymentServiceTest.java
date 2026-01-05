package com.payment.service;

import com.payment.dto.request.*;
import com.payment.dto.response.PaymentResponse;
import com.payment.entity.Order;
import com.payment.entity.PaymentTransaction;
import com.payment.entity.User;
import com.payment.enums.OrderStatus;
import com.payment.enums.TransactionStatus;
import com.payment.enums.TransactionType;
import com.payment.enums.UserRole;
import com.payment.exception.PaymentException;
import com.payment.exception.PaymentNotFoundException;
import com.payment.exception.PaymentValidationException;
import com.payment.gateway.PaymentGateway;
import com.payment.gateway.dto.AuthorizeRequest;
import com.payment.gateway.dto.AuthorizeResponse;
import com.payment.gateway.dto.CaptureResponse;
import com.payment.gateway.dto.PurchaseRequest;
import com.payment.gateway.dto.PurchaseResponse;
import com.payment.gateway.dto.RefundResponse;
import com.payment.gateway.dto.VoidRequest;
import com.payment.gateway.dto.VoidResponse;
import com.payment.repository.OrderRepository;
import com.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    
    @Mock
    private PaymentGateway paymentGateway;
    
    @Mock
    private OrderRepository orderRepository;
    
    @Mock
    private PaymentTransactionRepository transactionRepository;
    
    @InjectMocks
    private PaymentService paymentService;
    
    private User testUser;
    private Order testOrder;
    private PaymentTransaction testTransaction;
    
    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .passwordHash("hashed")
                .role(UserRole.USER)
                .enabled(true)
                .build();
        
        testOrder = Order.builder()
                .id(1L)
                .user(testUser)
                .orderNumber("ORD-001")
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("100.00"))
                .currency("USD")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
    
    // ========== Purchase Tests ==========
    
    @Test
    void testPurchase_Success() {
        // Given
        PaymentRequest request = createPaymentRequest();
        
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction tx = invocation.getArgument(0);
            tx.setId(100L);
            return tx;
        });
        
        PurchaseResponse gatewayResponse = PurchaseResponse.builder()
                .success(true)
                .transactionId("TXN123")
                .authorizationCode("AUTH123")
                .accountNumber("1111")
                .accountType("Visa")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();
        
        when(paymentGateway.purchase(any(PurchaseRequest.class))).thenReturn(gatewayResponse);
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);
        
        // When
        PaymentResponse response = paymentService.purchase(request, testUser);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getTransactionId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(response.getGatewayTransactionId()).isEqualTo("TXN123");
        assertThat(response.getOrder().getStatus()).isEqualTo(OrderStatus.COMPLETED);
        
        verify(transactionRepository, times(2)).save(any(PaymentTransaction.class));
        verify(paymentGateway).purchase(any(PurchaseRequest.class));
        verify(orderRepository).save(any(Order.class));
    }
    
    @Test
    void testPurchase_OrderNotFound() {
        // Given
        PaymentRequest request = createPaymentRequest();
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> paymentService.purchase(request, testUser))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining("Order not found");
        
        verify(transactionRepository, never()).save(any());
        verify(paymentGateway, never()).purchase(any());
    }
    
    @Test
    void testPurchase_OrderNotOwnedByUser() {
        // Given
        User otherUser = User.builder().id(2L).build();
        PaymentRequest request = createPaymentRequest();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        
        // When/Then
        assertThatThrownBy(() -> paymentService.purchase(request, otherUser))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Order does not belong to user");
        
        verify(paymentGateway, never()).purchase(any());
    }
    
    @Test
    void testPurchase_OrderNotPending() {
        // Given
        testOrder.setStatus(OrderStatus.COMPLETED);
        PaymentRequest request = createPaymentRequest();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        
        // When/Then
        assertThatThrownBy(() -> paymentService.purchase(request, testUser))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Order is not in PENDING status");
        
        verify(paymentGateway, never()).purchase(any());
    }
    
    @Test
    void testPurchase_AmountMismatch() {
        // Given
        PaymentRequest request = createPaymentRequest();
        request.setAmount(new BigDecimal("50.00")); // Different from order total
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        
        // When/Then
        assertThatThrownBy(() -> paymentService.purchase(request, testUser))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Amount does not match order total");
        
        verify(paymentGateway, never()).purchase(any());
    }
    
    @Test
    void testPurchase_GatewayFailure() {
        // Given
        PaymentRequest request = createPaymentRequest();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction tx = invocation.getArgument(0);
            if (tx.getId() == null) {
                tx.setId(100L);
            }
            return tx;
        });
        
        PurchaseResponse gatewayResponse = PurchaseResponse.builder()
                .success(false)
                .errorCode("5")
                .errorMessage("Do Not Honor")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();
        
        when(paymentGateway.purchase(any(PurchaseRequest.class))).thenReturn(gatewayResponse);
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);
        
        // When
        PaymentResponse response = paymentService.purchase(request, testUser);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(response.getOrder().getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(orderRepository, atLeastOnce()).save(any(Order.class));
        verify(transactionRepository, atLeastOnce()).save(any(PaymentTransaction.class));
    }
    
    @Test
    void testPurchase_GatewayException() {
        // Given
        PaymentRequest request = createPaymentRequest();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction tx = invocation.getArgument(0);
            if (tx.getId() == null) {
                tx.setId(100L);
            }
            return tx;
        });
        
        when(paymentGateway.purchase(any(PurchaseRequest.class)))
                .thenThrow(new RuntimeException("Network error"));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);
        
        // When/Then
        assertThatThrownBy(() -> paymentService.purchase(request, testUser))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("Payment processing failed");
        
        verify(orderRepository).save(any(Order.class));
    }
    
    // ========== Authorize Tests ==========
    
    @Test
    void testAuthorize_Success() {
        // Given
        PaymentRequest request = createPaymentRequest();
        
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction tx = invocation.getArgument(0);
            tx.setId(100L);
            return tx;
        });
        
        AuthorizeResponse gatewayResponse = AuthorizeResponse.builder()
                .success(true)
                .transactionId("AUTH123")
                .authorizationCode("AUTH456")
                .accountNumber("1111")
                .accountType("Visa")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();
        
        when(paymentGateway.authorize(any(AuthorizeRequest.class))).thenReturn(gatewayResponse);
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);
        
        // When
        PaymentResponse response = paymentService.authorize(request, testUser);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(response.getGatewayTransactionId()).isEqualTo("AUTH123");
        assertThat(response.getOrder().getStatus()).isEqualTo(OrderStatus.PROCESSING);
        
        verify(paymentGateway).authorize(any(AuthorizeRequest.class));
        verify(orderRepository).save(any(Order.class));
    }
    
    @Test
    void testAuthorize_GatewayFailure() {
        // Given
        PaymentRequest request = createPaymentRequest();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction tx = invocation.getArgument(0);
            tx.setId(100L);
            return tx;
        });
        
        AuthorizeResponse gatewayResponse = AuthorizeResponse.builder()
                .success(false)
                .errorCode("44")
                .errorMessage("Insufficient Funds")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();
        
        when(paymentGateway.authorize(any(AuthorizeRequest.class))).thenReturn(gatewayResponse);
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);
        
        // When
        PaymentResponse response = paymentService.authorize(request, testUser);
        
        // Then
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(response.getOrder().getStatus()).isEqualTo(OrderStatus.FAILED);
    }
    
    // ========== Capture Tests ==========
    
    @Test
    void testCapture_Success() {
        // Given
        PaymentTransaction authTransaction = createAuthorizationTransaction();
        CaptureRequest request = createCaptureRequest();
        request.setAuthorizationTransactionId(authTransaction.getId());
        
        when(transactionRepository.findById(authTransaction.getId()))
                .thenReturn(Optional.of(authTransaction));
        when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction tx = invocation.getArgument(0);
            tx.setId(200L);
            return tx;
        });
        
        CaptureResponse gatewayResponse = CaptureResponse.builder()
                .success(true)
                .transactionId("CAP123")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();
        
        when(paymentGateway.capture(any(com.payment.gateway.dto.CaptureRequest.class))).thenReturn(gatewayResponse);
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);
        
        // When
        PaymentResponse response = paymentService.capture(request, testUser);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(response.getGatewayTransactionId()).isEqualTo("CAP123");
        assertThat(response.getOrder().getStatus()).isEqualTo(OrderStatus.COMPLETED);
        
        verify(paymentGateway).capture(any(com.payment.gateway.dto.CaptureRequest.class));
        verify(transactionRepository, atLeastOnce()).save(any(PaymentTransaction.class));
    }
    
    @Test
    void testCapture_PartialCapture() {
        // Given
        PaymentTransaction authTransaction = createAuthorizationTransaction();
        CaptureRequest request = createCaptureRequest();
        request.setAuthorizationTransactionId(authTransaction.getId());
        request.setAmount(new BigDecimal("60.00")); // Partial capture
        
        when(transactionRepository.findById(authTransaction.getId()))
                .thenReturn(Optional.of(authTransaction));
        when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction tx = invocation.getArgument(0);
            tx.setId(200L);
            return tx;
        });
        
        CaptureResponse gatewayResponse = CaptureResponse.builder()
                .success(true)
                .transactionId("CAP123")
                .amount(new BigDecimal("60.00"))
                .currency("USD")
                .build();
        
        when(paymentGateway.capture(any(com.payment.gateway.dto.CaptureRequest.class))).thenReturn(gatewayResponse);
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);
        
        // When
        PaymentResponse response = paymentService.capture(request, testUser);
        
        // Then
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(response.getOrder().getStatus()).isEqualTo(OrderStatus.PARTIALLY_CAPTURED);
    }
    
    @Test
    void testCapture_AuthorizationNotFound() {
        // Given
        CaptureRequest request = createCaptureRequest();
        request.setAuthorizationTransactionId(999L);
        when(transactionRepository.findById(999L)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> paymentService.capture(request, testUser))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining("Authorization transaction not found");
        
        verify(paymentGateway, never()).capture(any());
    }
    
    @Test
    void testCapture_WrongTransactionType() {
        // Given
        PaymentTransaction purchaseTransaction = PaymentTransaction.builder()
                .id(100L)
                .order(testOrder)
                .transactionType(TransactionType.PURCHASE)
                .status(TransactionStatus.SUCCESS)
                .build();
        
        CaptureRequest request = createCaptureRequest();
        request.setAuthorizationTransactionId(purchaseTransaction.getId());
        when(transactionRepository.findById(purchaseTransaction.getId()))
                .thenReturn(Optional.of(purchaseTransaction));
        
        // When/Then
        assertThatThrownBy(() -> paymentService.capture(request, testUser))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Transaction is not an authorization");
    }
    
    @Test
    void testCapture_AuthorizationNotSuccessful() {
        // Given
        PaymentTransaction authTransaction = createAuthorizationTransaction();
        authTransaction.setStatus(TransactionStatus.FAILED);
        
        CaptureRequest request = createCaptureRequest();
        request.setAuthorizationTransactionId(authTransaction.getId());
        when(transactionRepository.findById(authTransaction.getId()))
                .thenReturn(Optional.of(authTransaction));
        
        // When/Then
        assertThatThrownBy(() -> paymentService.capture(request, testUser))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Authorization is not in SUCCESS status");
    }
    
    @Test
    void testCapture_AuthorizationExpired() {
        // Given
        PaymentTransaction authTransaction = createAuthorizationTransaction();
        authTransaction.setAuthorizationExpiresAt(LocalDateTime.now().minusDays(1));
        
        CaptureRequest request = createCaptureRequest();
        request.setAuthorizationTransactionId(authTransaction.getId());
        when(transactionRepository.findById(authTransaction.getId()))
                .thenReturn(Optional.of(authTransaction));
        
        // When/Then
        assertThatThrownBy(() -> paymentService.capture(request, testUser))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Authorization has expired");
    }
    
    @Test
    void testCapture_AmountExceedsRemaining() {
        // Given
        PaymentTransaction authTransaction = createAuthorizationTransaction();
        authTransaction.setCapturedAmount(new BigDecimal("50.00")); // Already captured 50
        
        CaptureRequest request = createCaptureRequest();
        request.setAuthorizationTransactionId(authTransaction.getId());
        request.setAmount(new BigDecimal("60.00")); // Try to capture 60, but only 50 remaining
        
        when(transactionRepository.findById(authTransaction.getId()))
                .thenReturn(Optional.of(authTransaction));
        
        // When/Then
        assertThatThrownBy(() -> paymentService.capture(request, testUser))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Capture amount exceeds remaining authorized amount");
    }
    
    // ========== Cancel/Void Tests ==========
    
    @Test
    void testCancel_Success() {
        // Given
        PaymentTransaction authTransaction = createAuthorizationTransaction();
        CancelRequest request = new CancelRequest();
        request.setAuthorizationTransactionId(authTransaction.getId());
        
        when(transactionRepository.findById(authTransaction.getId()))
                .thenReturn(Optional.of(authTransaction));
        when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction tx = invocation.getArgument(0);
            tx.setId(300L);
            return tx;
        });
        
        VoidResponse gatewayResponse = VoidResponse.builder()
                .success(true)
                .responseCode("1")
                .responseMessage("Approved")
                .build();
        
        when(paymentGateway.voidTransaction(any(VoidRequest.class))).thenReturn(gatewayResponse);
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);
        
        // When
        PaymentResponse response = paymentService.cancel(request, testUser);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(response.getOrder().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        
        verify(paymentGateway).voidTransaction(any(VoidRequest.class));
        verify(orderRepository).save(any(Order.class));
    }
    
    @Test
    void testCancel_AuthorizationNotFound() {
        // Given
        CancelRequest request = new CancelRequest();
        request.setAuthorizationTransactionId(999L);
        when(transactionRepository.findById(999L)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> paymentService.cancel(request, testUser))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining("Authorization transaction not found");
    }
    
    @Test
    void testCancel_AuthorizationAlreadyCaptured() {
        // Given
        PaymentTransaction authTransaction = createAuthorizationTransaction();
        authTransaction.setCapturedAmount(new BigDecimal("100.00"));
        
        CancelRequest request = new CancelRequest();
        request.setAuthorizationTransactionId(authTransaction.getId());
        when(transactionRepository.findById(authTransaction.getId()))
                .thenReturn(Optional.of(authTransaction));
        
        // When/Then
        assertThatThrownBy(() -> paymentService.cancel(request, testUser))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Cannot void: authorization has been captured");
    }
    
    @Test
    void testCancel_OutsideVoidWindow() {
        // Given
        PaymentTransaction authTransaction = createAuthorizationTransaction();
        authTransaction.setCreatedAt(LocalDateTime.now().minusHours(25)); // 25 hours ago
        
        CancelRequest request = new CancelRequest();
        request.setAuthorizationTransactionId(authTransaction.getId());
        when(transactionRepository.findById(authTransaction.getId()))
                .thenReturn(Optional.of(authTransaction));
        
        // When/Then
        assertThatThrownBy(() -> paymentService.cancel(request, testUser))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("authorization is outside the 24-hour void window");
    }
    
    // ========== Refund Tests ==========
    
    @Test
    void testRefund_Success() {
        // Given
        PaymentTransaction purchaseTransaction = createPurchaseTransaction();
        RefundRequest request = createRefundRequest();
        request.setTransactionId(purchaseTransaction.getId());
        
        when(transactionRepository.findById(purchaseTransaction.getId()))
                .thenReturn(Optional.of(purchaseTransaction));
        when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction tx = invocation.getArgument(0);
            tx.setId(400L);
            return tx;
        });
        
        RefundResponse gatewayResponse = RefundResponse.builder()
                .success(true)
                .transactionId("REF123")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();
        
        when(paymentGateway.refund(any(com.payment.gateway.dto.RefundRequest.class))).thenReturn(gatewayResponse);
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);
        
        // When
        PaymentResponse response = paymentService.refund(request, testUser);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(response.getGatewayTransactionId()).isEqualTo("REF123");
        assertThat(response.getOrder().getStatus()).isEqualTo(OrderStatus.REFUNDED);
        
        verify(paymentGateway).refund(any(com.payment.gateway.dto.RefundRequest.class));
        verify(orderRepository).save(any(Order.class));
    }
    
    @Test
    void testRefund_PartialRefund() {
        // Given
        PaymentTransaction purchaseTransaction = createPurchaseTransaction();
        RefundRequest request = createRefundRequest();
        request.setTransactionId(purchaseTransaction.getId());
        request.setAmount(new BigDecimal("50.00")); // Partial refund
        
        when(transactionRepository.findById(purchaseTransaction.getId()))
                .thenReturn(Optional.of(purchaseTransaction));
        when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction tx = invocation.getArgument(0);
            tx.setId(400L);
            return tx;
        });
        
        RefundResponse gatewayResponse = RefundResponse.builder()
                .success(true)
                .transactionId("REF123")
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .build();
        
        when(paymentGateway.refund(any(com.payment.gateway.dto.RefundRequest.class))).thenReturn(gatewayResponse);
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);
        
        // When
        PaymentResponse response = paymentService.refund(request, testUser);
        
        // Then
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(response.getOrder().getStatus()).isEqualTo(OrderStatus.PARTIALLY_REFUNDED);
    }
    
    @Test
    void testRefund_TransactionNotFound() {
        // Given
        RefundRequest request = createRefundRequest();
        request.setTransactionId(999L);
        when(transactionRepository.findById(999L)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> paymentService.refund(request, testUser))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining("Transaction not found");
    }
    
    @Test
    void testRefund_WrongTransactionType() {
        // Given
        PaymentTransaction authTransaction = createAuthorizationTransaction();
        RefundRequest request = createRefundRequest();
        request.setTransactionId(authTransaction.getId());
        when(transactionRepository.findById(authTransaction.getId()))
                .thenReturn(Optional.of(authTransaction));
        
        // When/Then
        assertThatThrownBy(() -> paymentService.refund(request, testUser))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Only CAPTURE or PURCHASE transactions can be refunded");
    }
    
    @Test
    void testRefund_AmountExceedsRemaining() {
        // Given
        PaymentTransaction purchaseTransaction = createPurchaseTransaction();
        purchaseTransaction.setRefundedAmount(new BigDecimal("50.00")); // Already refunded 50
        
        RefundRequest request = createRefundRequest();
        request.setTransactionId(purchaseTransaction.getId());
        request.setAmount(new BigDecimal("60.00")); // Try to refund 60, but only 50 remaining
        
        when(transactionRepository.findById(purchaseTransaction.getId()))
                .thenReturn(Optional.of(purchaseTransaction));
        
        // When/Then
        assertThatThrownBy(() -> paymentService.refund(request, testUser))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Refund amount exceeds remaining refundable amount");
    }
    
    @Test
    void testRefund_TransactionNotSuccessful() {
        // Given
        PaymentTransaction purchaseTransaction = createPurchaseTransaction();
        purchaseTransaction.setStatus(TransactionStatus.FAILED);
        
        RefundRequest request = createRefundRequest();
        request.setTransactionId(purchaseTransaction.getId());
        when(transactionRepository.findById(purchaseTransaction.getId()))
                .thenReturn(Optional.of(purchaseTransaction));
        
        // When/Then
        assertThatThrownBy(() -> paymentService.refund(request, testUser))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Transaction is not in SUCCESS status");
    }
    
    @Test
    void testRefund_CurrencyMismatch() {
        // Given
        PaymentTransaction purchaseTransaction = createPurchaseTransaction();
        RefundRequest request = createRefundRequest();
        request.setTransactionId(purchaseTransaction.getId());
        request.setCurrency("EUR"); // Different currency
        
        when(transactionRepository.findById(purchaseTransaction.getId()))
                .thenReturn(Optional.of(purchaseTransaction));
        
        // When/Then
        assertThatThrownBy(() -> paymentService.refund(request, testUser))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Currency mismatch");
    }
    
    // ========== Helper Methods ==========
    
    private PaymentRequest createPaymentRequest() {
        PaymentRequest request = new PaymentRequest();
        request.setOrderId(1L);
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrency("USD");
        
        PaymentMethodRequest paymentMethod = new PaymentMethodRequest();
        paymentMethod.setCardNumber("4111111111111111");
        paymentMethod.setExpiryMonth("12");
        paymentMethod.setExpiryYear("2025");
        paymentMethod.setCvv("123");
        paymentMethod.setCardholderName("John Doe");
        
        request.setPaymentMethod(paymentMethod);
        request.setDescription("Test payment");
        return request;
    }
    
    private CaptureRequest createCaptureRequest() {
        CaptureRequest request = new CaptureRequest();
        request.setAuthorizationTransactionId(100L);
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrency("USD");
        return request;
    }
    
    private RefundRequest createRefundRequest() {
        RefundRequest request = new RefundRequest();
        request.setTransactionId(100L);
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrency("USD");
        request.setReason("Test refund");
        return request;
    }
    
    private PaymentTransaction createAuthorizationTransaction() {
        PaymentTransaction transaction = PaymentTransaction.builder()
                .id(100L)
                .order(testOrder)
                .transactionType(TransactionType.AUTHORIZE)
                .status(TransactionStatus.SUCCESS)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .authorizedAmount(new BigDecimal("100.00"))
                .capturedAmount(BigDecimal.ZERO)
                .authorizeNetTransactionId("AUTH123")
                .authorizationExpiresAt(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .build();
        return transaction;
    }
    
    private PaymentTransaction createPurchaseTransaction() {
        PaymentTransaction transaction = PaymentTransaction.builder()
                .id(100L)
                .order(testOrder)
                .transactionType(TransactionType.PURCHASE)
                .status(TransactionStatus.SUCCESS)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .authorizeNetTransactionId("PUR123")
                .refundedAmount(BigDecimal.ZERO)
                .lastFourDigits("1111")
                .cardExpiryMonth(12)
                .cardExpiryYear(2025)
                .createdAt(LocalDateTime.now())
                .build();
        return transaction;
    }
}

