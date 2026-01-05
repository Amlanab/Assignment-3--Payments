package com.payment.controller;

import com.payment.dto.request.CancelRequest;
import com.payment.dto.request.CaptureRequest;
import com.payment.dto.request.PaymentRequest;
import com.payment.dto.request.RefundRequest;
import com.payment.dto.response.PaymentResponse;
import com.payment.entity.User;
import com.payment.security.SecurityUser;
import com.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {
    
    private final PaymentService paymentService;
    
    /**
     * Extract User entity from SecurityUser
     */
    private User getUserFromSecurityUser(SecurityUser securityUser) {
        return securityUser.getUser();
    }
    
    @PostMapping("/purchase")
    public ResponseEntity<PaymentResponse> purchase(
            @Valid @RequestBody PaymentRequest request,
            @AuthenticationPrincipal SecurityUser user) {
        if (user == null) {
            log.error("User is null in purchase request for orderId={}", request.getOrderId());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        log.info("Purchase request received for orderId={}, userId={}", 
                request.getOrderId(), user.getId());
        
        PaymentResponse response = paymentService.purchase(request, getUserFromSecurityUser(user));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
    
    @PostMapping("/authorize")
    public ResponseEntity<PaymentResponse> authorize(
            @Valid @RequestBody PaymentRequest request,
            @AuthenticationPrincipal SecurityUser user) {
        log.info("Authorize request received for orderId={}, userId={}", 
                request.getOrderId(), user.getId());
        
        PaymentResponse response = paymentService.authorize(request, getUserFromSecurityUser(user));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
    
    @PostMapping("/capture")
    public ResponseEntity<PaymentResponse> capture(
            @Valid @RequestBody CaptureRequest request,
            @AuthenticationPrincipal SecurityUser user) {
        log.info("Capture request received for authorizationTransactionId={}, userId={}", 
                request.getAuthorizationTransactionId(), user.getId());
        
        PaymentResponse response = paymentService.capture(request, getUserFromSecurityUser(user));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
    
    @PostMapping("/cancel")
    public ResponseEntity<PaymentResponse> cancel(
            @Valid @RequestBody CancelRequest request,
            @AuthenticationPrincipal SecurityUser user) {
        log.info("Cancel request received for authorizationTransactionId={}, userId={}", 
                request.getAuthorizationTransactionId(), user.getId());
        
        PaymentResponse response = paymentService.cancel(request, getUserFromSecurityUser(user));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
    
    @PostMapping("/refund")
    public ResponseEntity<PaymentResponse> refund(
            @Valid @RequestBody RefundRequest request,
            @AuthenticationPrincipal SecurityUser user) {
        log.info("Refund request received for transactionId={}, userId={}", 
                request.getTransactionId(), user.getId());
        
        PaymentResponse response = paymentService.refund(request, getUserFromSecurityUser(user));
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}

