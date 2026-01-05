package com.payment.gateway;

import com.payment.config.AuthorizeNetConfig;
import com.payment.gateway.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.authorize.Environment;
import net.authorize.api.contract.v1.*;
import net.authorize.api.controller.CreateTransactionController;
import net.authorize.api.controller.base.ApiOperationBase;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Authorize.Net Payment Gateway Implementation
 * Wraps Authorize.Net SDK and isolates it from business logic
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthorizeNetGateway implements PaymentGateway {
    
    private final AuthorizeNetConfig config;
    
    @Override
    public PurchaseResponse purchase(PurchaseRequest request) {
        log.debug("Processing purchase transaction: amount={}, invoiceNumber={}", 
                request.getAmount(), request.getInvoiceNumber());
        
        try {
            initializeApiOperationBase();
            
            // Create credit card
            CreditCardType creditCard = createCreditCard(request.getCreditCard());
            
            // Create payment type
            PaymentType paymentType = new PaymentType();
            paymentType.setCreditCard(creditCard);
            
            // Create transaction request
            TransactionRequestType transactionRequest = new TransactionRequestType();
            transactionRequest.setTransactionType(TransactionTypeEnum.AUTH_CAPTURE_TRANSACTION.value());
            transactionRequest.setAmount(request.getAmount());
            transactionRequest.setPayment(paymentType);
            
            // Add order info
            if (request.getInvoiceNumber() != null || request.getDescription() != null) {
                OrderType order = new OrderType();
                if (request.getInvoiceNumber() != null) {
                    order.setInvoiceNumber(request.getInvoiceNumber());
                }
                if (request.getDescription() != null) {
                    order.setDescription(request.getDescription());
                }
                transactionRequest.setOrder(order);
            }
            
            // Execute transaction
            CreateTransactionResponse response = executeTransaction(transactionRequest);
            
            return mapPurchaseResponse(response, request.getAmount(), request.getCurrency());
            
        } catch (Exception e) {
            log.error("Error processing purchase transaction", e);
            return PurchaseResponse.builder()
                    .success(false)
                    .errorMessage("Gateway error: " + e.getMessage())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .build();
        }
    }
    
    @Override
    public AuthorizeResponse authorize(AuthorizeRequest request) {
        log.debug("Processing authorization: amount={}, invoiceNumber={}", 
                request.getAmount(), request.getInvoiceNumber());
        
        try {
            initializeApiOperationBase();
            
            // Create credit card
            CreditCardType creditCard = createCreditCard(request.getCreditCard());
            
            // Create payment type
            PaymentType paymentType = new PaymentType();
            paymentType.setCreditCard(creditCard);
            
            // Create transaction request
            TransactionRequestType transactionRequest = new TransactionRequestType();
            transactionRequest.setTransactionType(TransactionTypeEnum.AUTH_ONLY_TRANSACTION.value());
            transactionRequest.setAmount(request.getAmount());
            transactionRequest.setPayment(paymentType);
            
            // Add order info
            if (request.getInvoiceNumber() != null || request.getDescription() != null) {
                OrderType order = new OrderType();
                if (request.getInvoiceNumber() != null) {
                    order.setInvoiceNumber(request.getInvoiceNumber());
                }
                if (request.getDescription() != null) {
                    order.setDescription(request.getDescription());
                }
                transactionRequest.setOrder(order);
            }
            
            // Execute transaction
            CreateTransactionResponse response = executeTransaction(transactionRequest);
            
            return mapAuthorizeResponse(response, request.getAmount(), request.getCurrency());
            
        } catch (Exception e) {
            log.error("Error processing authorization", e);
            return AuthorizeResponse.builder()
                    .success(false)
                    .errorMessage("Gateway error: " + e.getMessage())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .build();
        }
    }
    
    @Override
    public CaptureResponse capture(CaptureRequest request) {
        log.debug("Processing capture: authorizationTransactionId={}, amount={}", 
                request.getAuthorizationTransactionId(), request.getAmount());
        
        try {
            initializeApiOperationBase();
            
            // Create transaction request
            TransactionRequestType transactionRequest = new TransactionRequestType();
            transactionRequest.setTransactionType(TransactionTypeEnum.PRIOR_AUTH_CAPTURE_TRANSACTION.value());
            transactionRequest.setAmount(request.getAmount());
            transactionRequest.setRefTransId(request.getAuthorizationTransactionId());
            
            // Execute transaction
            CreateTransactionResponse response = executeTransaction(transactionRequest);
            
            return mapCaptureResponse(response, request.getAmount(), request.getCurrency());
            
        } catch (Exception e) {
            log.error("Error processing capture", e);
            return CaptureResponse.builder()
                    .success(false)
                    .errorMessage("Gateway error: " + e.getMessage())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .build();
        }
    }
    
    @Override
    public VoidResponse voidTransaction(VoidRequest request) {
        log.debug("Processing void: authorizationTransactionId={}", request.getAuthorizationTransactionId());
        
        try {
            initializeApiOperationBase();
            
            // Create transaction request
            TransactionRequestType transactionRequest = new TransactionRequestType();
            transactionRequest.setTransactionType(TransactionTypeEnum.VOID_TRANSACTION.value());
            transactionRequest.setRefTransId(request.getAuthorizationTransactionId());
            
            // Execute transaction
            CreateTransactionResponse response = executeTransaction(transactionRequest);
            
            return mapVoidResponse(response);
            
        } catch (Exception e) {
            log.error("Error processing void", e);
            return VoidResponse.builder()
                    .success(false)
                    .errorMessage("Gateway error: " + e.getMessage())
                    .build();
        }
    }
    
    @Override
    public RefundResponse refund(RefundRequest request) {
        log.debug("Processing refund: transactionId={}, amount={}", 
                request.getTransactionId(), request.getAmount());
        
        try {
            initializeApiOperationBase();
            
            // Create transaction request
            TransactionRequestType transactionRequest = new TransactionRequestType();
            transactionRequest.setTransactionType(TransactionTypeEnum.REFUND_TRANSACTION.value());
            transactionRequest.setAmount(request.getAmount());
            transactionRequest.setRefTransId(request.getTransactionId());
            
            // For refunds, card details are optional if we have a valid reference transaction ID
            // Authorize.Net can process refunds with just the reference transaction ID
            // However, if card details are provided, include them for better matching
            if (request.getLastFourDigits() != null || request.getExpirationDate() != null) {
                CreditCardType creditCard = new CreditCardType();
                if (request.getLastFourDigits() != null) {
                    // Use full card number format if available, otherwise use masked format
                    creditCard.setCardNumber("XXXX" + request.getLastFourDigits());
                }
                if (request.getExpirationDate() != null) {
                    creditCard.setExpirationDate(request.getExpirationDate());
                }
                
                PaymentType paymentType = new PaymentType();
                paymentType.setCreditCard(creditCard);
                transactionRequest.setPayment(paymentType);
            }
            
            // Execute transaction
            CreateTransactionResponse response = executeTransaction(transactionRequest);
            
            return mapRefundResponse(response, request.getAmount(), request.getCurrency());
            
        } catch (Exception e) {
            log.error("Error processing refund", e);
            return RefundResponse.builder()
                    .success(false)
                    .errorMessage("Gateway error: " + e.getMessage())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .build();
        }
    }
    
    /**
     * Initialize API Operation Base with authentication and environment
     */
    private void initializeApiOperationBase() {
        // Set merchant authentication
        MerchantAuthenticationType merchantAuthentication = new MerchantAuthenticationType();
        merchantAuthentication.setName(config.getApiLoginId());
        merchantAuthentication.setTransactionKey(config.getTransactionKey());
        ApiOperationBase.setMerchantAuthentication(merchantAuthentication);
        
        // Set environment (sandbox or production)
        Environment environment = "sandbox".equalsIgnoreCase(config.getEnvironment()) 
                ? Environment.SANDBOX 
                : Environment.PRODUCTION;
        ApiOperationBase.setEnvironment(environment);
    }
    
    /**
     * Create credit card type from credit card info
     */
    private CreditCardType createCreditCard(CreditCardInfo creditCardInfo) {
        CreditCardType creditCard = new CreditCardType();
        creditCard.setCardNumber(creditCardInfo.getCardNumber());
        creditCard.setExpirationDate(creditCardInfo.getExpirationDate());
        if (creditCardInfo.getCardCode() != null) {
            creditCard.setCardCode(creditCardInfo.getCardCode());
        }
        return creditCard;
    }
    
    /**
     * Execute transaction and return response
     */
    private CreateTransactionResponse executeTransaction(TransactionRequestType transactionRequest) {
        CreateTransactionRequest apiRequest = new CreateTransactionRequest();
        apiRequest.setTransactionRequest(transactionRequest);
        
        CreateTransactionController controller = new CreateTransactionController(apiRequest);
        controller.execute();
        
        return controller.getApiResponse();
    }
    
    /**
     * Map Authorize.Net response to PurchaseResponse
     */
    private PurchaseResponse mapPurchaseResponse(CreateTransactionResponse response, 
                                                 BigDecimal amount, String currency) {
        if (response == null) {
            return PurchaseResponse.builder()
                    .success(false)
                    .errorMessage("No response from gateway")
                    .amount(amount)
                    .currency(currency)
                    .build();
        }
        
        MessagesType messages = response.getMessages();
        TransactionResponse transactionResponse = response.getTransactionResponse();
        
        if (messages != null && MessageTypeEnum.OK.equals(messages.getResultCode()) 
                && transactionResponse != null) {
            return PurchaseResponse.builder()
                    .success(true)
                    .transactionId(transactionResponse.getTransId())
                    .authorizationCode(transactionResponse.getAuthCode())
                    .responseCode(transactionResponse.getResponseCode())
                    .responseMessage(transactionResponse.getMessages() != null && 
                            transactionResponse.getMessages().getMessage() != null &&
                            !transactionResponse.getMessages().getMessage().isEmpty() ? 
                            transactionResponse.getMessages().getMessage().get(0).getDescription() : "Approved")
                    .avsResultCode(transactionResponse.getAvsResultCode())
                    .cvvResultCode(transactionResponse.getCvvResultCode())
                    .accountNumber(transactionResponse.getAccountNumber())
                    .accountType(transactionResponse.getAccountType())
                    .amount(amount)
                    .currency(currency)
                    .build();
        } else {
            String errorCode = null;
            String errorMessage = null;
            
            // Use response code as error code if transaction failed
            if (transactionResponse != null) {
                errorCode = transactionResponse.getResponseCode();
                // Try to get error message from transaction response messages
                if (transactionResponse.getMessages() != null 
                        && transactionResponse.getMessages().getMessage() != null
                        && !transactionResponse.getMessages().getMessage().isEmpty()) {
                    errorMessage = transactionResponse.getMessages().getMessage().get(0).getDescription();
                }
            }
            
            // Fallback to top-level messages if no transaction response message
            if (errorMessage == null && messages != null && messages.getMessage() != null 
                    && !messages.getMessage().isEmpty()) {
                errorMessage = messages.getMessage().get(0).getText();
            }
            
            return PurchaseResponse.builder()
                    .success(false)
                    .responseCode(transactionResponse != null ? transactionResponse.getResponseCode() : null)
                    .errorCode(errorCode)
                    .errorMessage(errorMessage)
                    .amount(amount)
                    .currency(currency)
                    .build();
        }
    }
    
    /**
     * Map Authorize.Net response to AuthorizeResponse
     */
    private AuthorizeResponse mapAuthorizeResponse(CreateTransactionResponse response, 
                                                   BigDecimal amount, String currency) {
        if (response == null) {
            return AuthorizeResponse.builder()
                    .success(false)
                    .errorMessage("No response from gateway")
                    .amount(amount)
                    .currency(currency)
                    .build();
        }
        
        MessagesType messages = response.getMessages();
        TransactionResponse transactionResponse = response.getTransactionResponse();
        
        if (messages != null && MessageTypeEnum.OK.equals(messages.getResultCode()) 
                && transactionResponse != null) {
            return AuthorizeResponse.builder()
                    .success(true)
                    .transactionId(transactionResponse.getTransId())
                    .authorizationCode(transactionResponse.getAuthCode())
                    .responseCode(transactionResponse.getResponseCode())
                    .responseMessage(transactionResponse.getMessages() != null && 
                            transactionResponse.getMessages().getMessage() != null &&
                            !transactionResponse.getMessages().getMessage().isEmpty() ? 
                            transactionResponse.getMessages().getMessage().get(0).getDescription() : "Approved")
                    .avsResultCode(transactionResponse.getAvsResultCode())
                    .cvvResultCode(transactionResponse.getCvvResultCode())
                    .accountNumber(transactionResponse.getAccountNumber())
                    .accountType(transactionResponse.getAccountType())
                    .amount(amount)
                    .currency(currency)
                    .build();
        } else {
            String errorCode = null;
            String errorMessage = null;
            
            // Use response code as error code if transaction failed
            if (transactionResponse != null) {
                errorCode = transactionResponse.getResponseCode();
                // Try to get error message from transaction response messages
                if (transactionResponse.getMessages() != null 
                        && transactionResponse.getMessages().getMessage() != null
                        && !transactionResponse.getMessages().getMessage().isEmpty()) {
                    errorMessage = transactionResponse.getMessages().getMessage().get(0).getDescription();
                }
            }
            
            // Fallback to top-level messages if no transaction response message
            if (errorMessage == null && messages != null && messages.getMessage() != null 
                    && !messages.getMessage().isEmpty()) {
                errorMessage = messages.getMessage().get(0).getText();
            }
            
            return AuthorizeResponse.builder()
                    .success(false)
                    .responseCode(transactionResponse != null ? transactionResponse.getResponseCode() : null)
                    .errorCode(errorCode)
                    .errorMessage(errorMessage)
                    .amount(amount)
                    .currency(currency)
                    .build();
        }
    }
    
    /**
     * Map Authorize.Net response to CaptureResponse
     */
    private CaptureResponse mapCaptureResponse(CreateTransactionResponse response, 
                                               BigDecimal amount, String currency) {
        if (response == null) {
            return CaptureResponse.builder()
                    .success(false)
                    .errorMessage("No response from gateway")
                    .amount(amount)
                    .currency(currency)
                    .build();
        }
        
        MessagesType messages = response.getMessages();
        TransactionResponse transactionResponse = response.getTransactionResponse();
        
        if (messages != null && MessageTypeEnum.OK.equals(messages.getResultCode()) 
                && transactionResponse != null) {
            return CaptureResponse.builder()
                    .success(true)
                    .transactionId(transactionResponse.getTransId())
                    .responseCode(transactionResponse.getResponseCode())
                    .responseMessage(transactionResponse.getMessages() != null && 
                            transactionResponse.getMessages().getMessage() != null &&
                            !transactionResponse.getMessages().getMessage().isEmpty() ? 
                            transactionResponse.getMessages().getMessage().get(0).getDescription() : "Approved")
                    .amount(amount)
                    .currency(currency)
                    .build();
        } else {
            String errorCode = null;
            String errorMessage = null;
            
            // Use response code as error code if transaction failed
            if (transactionResponse != null) {
                errorCode = transactionResponse.getResponseCode();
                // Try to get error message from transaction response messages
                if (transactionResponse.getMessages() != null 
                        && transactionResponse.getMessages().getMessage() != null
                        && !transactionResponse.getMessages().getMessage().isEmpty()) {
                    errorMessage = transactionResponse.getMessages().getMessage().get(0).getDescription();
                }
            }
            
            // Fallback to top-level messages if no transaction response message
            if (errorMessage == null && messages != null && messages.getMessage() != null 
                    && !messages.getMessage().isEmpty()) {
                errorMessage = messages.getMessage().get(0).getText();
            }
            
            return CaptureResponse.builder()
                    .success(false)
                    .responseCode(transactionResponse != null ? transactionResponse.getResponseCode() : null)
                    .errorCode(errorCode)
                    .errorMessage(errorMessage)
                    .amount(amount)
                    .currency(currency)
                    .build();
        }
    }
    
    /**
     * Map Authorize.Net response to VoidResponse
     */
    private VoidResponse mapVoidResponse(CreateTransactionResponse response) {
        if (response == null) {
            return VoidResponse.builder()
                    .success(false)
                    .errorMessage("No response from gateway")
                    .build();
        }
        
        MessagesType messages = response.getMessages();
        TransactionResponse transactionResponse = response.getTransactionResponse();
        
        if (messages != null && MessageTypeEnum.OK.equals(messages.getResultCode()) 
                && transactionResponse != null) {
            return VoidResponse.builder()
                    .success(true)
                    .responseCode(transactionResponse.getResponseCode())
                    .responseMessage(transactionResponse.getMessages() != null && 
                            transactionResponse.getMessages().getMessage() != null &&
                            !transactionResponse.getMessages().getMessage().isEmpty() ? 
                            transactionResponse.getMessages().getMessage().get(0).getDescription() : "Approved")
                    .build();
        } else {
            String errorCode = null;
            String errorMessage = null;
            
            // Use response code as error code if transaction failed
            if (transactionResponse != null) {
                errorCode = transactionResponse.getResponseCode();
                // Try to get error message from transaction response messages
                if (transactionResponse.getMessages() != null 
                        && transactionResponse.getMessages().getMessage() != null
                        && !transactionResponse.getMessages().getMessage().isEmpty()) {
                    errorMessage = transactionResponse.getMessages().getMessage().get(0).getDescription();
                }
            }
            
            // Fallback to top-level messages if no transaction response message
            if (errorMessage == null && messages != null && messages.getMessage() != null 
                    && !messages.getMessage().isEmpty()) {
                errorMessage = messages.getMessage().get(0).getText();
            }
            
            return VoidResponse.builder()
                    .success(false)
                    .responseCode(transactionResponse != null ? transactionResponse.getResponseCode() : null)
                    .errorCode(errorCode)
                    .errorMessage(errorMessage)
                    .build();
        }
    }
    
    /**
     * Map Authorize.Net response to RefundResponse
     */
    private RefundResponse mapRefundResponse(CreateTransactionResponse response, 
                                             BigDecimal amount, String currency) {
        if (response == null) {
            return RefundResponse.builder()
                    .success(false)
                    .errorMessage("No response from gateway")
                    .amount(amount)
                    .currency(currency)
                    .build();
        }
        
        MessagesType messages = response.getMessages();
        TransactionResponse transactionResponse = response.getTransactionResponse();
        
        if (messages != null && MessageTypeEnum.OK.equals(messages.getResultCode()) 
                && transactionResponse != null) {
            return RefundResponse.builder()
                    .success(true)
                    .transactionId(transactionResponse.getTransId())
                    .responseCode(transactionResponse.getResponseCode())
                    .responseMessage(transactionResponse.getMessages() != null && 
                            transactionResponse.getMessages().getMessage() != null &&
                            !transactionResponse.getMessages().getMessage().isEmpty() ? 
                            transactionResponse.getMessages().getMessage().get(0).getDescription() : "Approved")
                    .amount(amount)
                    .currency(currency)
                    .build();
        } else {
            String errorCode = null;
            String errorMessage = null;
            
            // Use response code as error code if transaction failed
            if (transactionResponse != null) {
                errorCode = transactionResponse.getResponseCode();
                // Try to get error message from transaction response messages
                if (transactionResponse.getMessages() != null 
                        && transactionResponse.getMessages().getMessage() != null
                        && !transactionResponse.getMessages().getMessage().isEmpty()) {
                    errorMessage = transactionResponse.getMessages().getMessage().get(0).getDescription();
                }
            }
            
            // Fallback to top-level messages if no transaction response message
            if (errorMessage == null && messages != null && messages.getMessage() != null 
                    && !messages.getMessage().isEmpty()) {
                errorMessage = messages.getMessage().get(0).getText();
            }
            
            return RefundResponse.builder()
                    .success(false)
                    .responseCode(transactionResponse != null ? transactionResponse.getResponseCode() : null)
                    .errorCode(errorCode)
                    .errorMessage(errorMessage)
                    .amount(amount)
                    .currency(currency)
                    .build();
        }
    }
}

