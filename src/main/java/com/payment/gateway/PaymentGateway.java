package com.payment.gateway;

import com.payment.gateway.dto.*;

/**
 * Payment Gateway Interface
 * Abstracts payment gateway operations to allow for easy swapping of implementations
 */
public interface PaymentGateway {
    
    /**
     * Process a purchase transaction (authorize + capture in one step)
     */
    PurchaseResponse purchase(PurchaseRequest request);
    
    /**
     * Authorize a payment (hold funds without capturing)
     */
    AuthorizeResponse authorize(AuthorizeRequest request);
    
    /**
     * Capture previously authorized funds
     */
    CaptureResponse capture(CaptureRequest request);
    
    /**
     * Void/cancel an authorization before capture
     */
    VoidResponse voidTransaction(VoidRequest request);
    
    /**
     * Refund a captured or purchased transaction
     */
    RefundResponse refund(RefundRequest request);
}

