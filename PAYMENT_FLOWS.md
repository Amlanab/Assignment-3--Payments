# Payment Flow Detailed Specifications

## Flow 1: Purchase (Authorize + Capture in One Step)

### Use Case
Customer makes a one-time payment that should be immediately captured.

### Sequence
```
Client → Controller → PaymentService → AuthorizeNetGateway → Authorize.Net API
                                                                    ↓
Client ← Controller ← PaymentService ← AuthorizeNetGateway ← Response
```

### Steps
1. Client sends payment request with order details and payment method
2. Controller validates request (amount > 0, valid payment method)
3. PaymentService creates Order entity (status: PENDING)
4. PaymentService creates PaymentTransaction (type: PURCHASE, status: PENDING)
5. PaymentService calls AuthorizeNetGateway.purchase()
6. Gateway makes API call to Authorize.Net
7. On Success:
   - Update PaymentTransaction (status: SUCCESS, store transaction ID)
   - Update Order (status: COMPLETED)
   - Return success response to client
8. On Failure:
   - Update PaymentTransaction (status: FAILED, store error details)
   - Update Order (status: FAILED)
   - Return error response to client

### API Request Example
```json
POST /api/payments/purchase
{
  "orderId": "ORD-12345",
  "amount": 100.00,
  "currency": "USD",
  "paymentMethod": {
    "cardNumber": "4111111111111111",
    "expiryMonth": "12",
    "expiryYear": "2025",
    "cvv": "123",
    "cardholderName": "John Doe"
  }
}
```

---

## Flow 2: Authorize (Hold Funds)

### Use Case
Pre-authorize funds (e.g., hotel booking, car rental). Funds are held but not captured.

### Sequence
```
Client → Controller → PaymentService → AuthorizeNetGateway → Authorize.Net API
                                                                    ↓
Client ← Controller ← PaymentService ← AuthorizeNetGateway ← Response (auth code)
```

### Steps
1. Client sends authorize request
2. Controller validates request
3. PaymentService creates PaymentTransaction (type: AUTHORIZE, status: PENDING)
4. PaymentService calls AuthorizeNetGateway.authorize()
5. On Success:
   - Update PaymentTransaction (status: SUCCESS, store authorization code)
   - Order status remains: PROCESSING (waiting for capture)
   - Return authorization response with transaction ID
6. On Failure:
   - Update PaymentTransaction (status: FAILED)
   - Order status: FAILED
   - Return error response

### Important Notes
- Authorization typically expires in 30 days (Authorize.Net default)
- Must capture before expiry or funds are released
- Store authorization transaction ID for later capture

---

## Flow 3: Capture (Capture Previously Authorized Funds)

### Use Case
Capture funds that were previously authorized.

### Sequence
```
Client → Controller → PaymentService → PaymentTransactionRepository
                                                ↓
                                    (find authorization transaction)
                                                ↓
                          PaymentService → AuthorizeNetGateway → Authorize.Net API
                                                                    ↓
Client ← Controller ← PaymentService ← AuthorizeNetGateway ← Response
```

### Steps
1. Client sends capture request with authorization transaction ID
2. Controller validates request
3. PaymentService retrieves authorization transaction
4. Validate:
   - Transaction exists and type is AUTHORIZE
   - Status is SUCCESS (authorized)
   - Authorization not expired
   - Amount to capture ≤ authorized amount
   - Not already fully captured
5. PaymentService creates new PaymentTransaction (type: CAPTURE, status: PENDING)
6. PaymentService calls AuthorizeNetGateway.capture() with auth transaction ID
7. On Success:
   - Update capture transaction (status: SUCCESS)
   - Update authorization transaction (linked reference)
   - Update Order (status: COMPLETED if fully captured)
   - Return success response
8. On Failure:
   - Update capture transaction (status: FAILED)
   - Return error response

### Partial Capture Support
- Multiple captures allowed up to authorized amount
- Track: `authorizedAmount`, `capturedAmount`, `remainingAmount`
- Order status: COMPLETED only when fully captured

### API Request Example
```json
POST /api/payments/{authorizationTransactionId}/capture
{
  "amount": 100.00,
  "currency": "USD"
}
```

---

## Flow 4: Void/Cancel (Cancel Authorization)

### Use Case
Cancel a pending authorization before it's captured. Must be done before settlement (typically within 24 hours).

### Sequence
```
Client → Controller → PaymentService → PaymentTransactionRepository
                                                ↓
                          PaymentService → AuthorizeNetGateway → Authorize.Net API
                                                                    ↓
Client ← Controller ← PaymentService ← AuthorizeNetGateway ← Response
```

### Steps
1. Client sends void request with authorization transaction ID
2. Controller validates request
3. PaymentService retrieves authorization transaction
4. Validate:
   - Transaction exists and type is AUTHORIZE
   - Status is SUCCESS (authorized)
   - Not yet captured (or partially captured)
   - Within void time window (check transaction age)
5. PaymentService creates PaymentTransaction (type: VOID, status: PENDING)
6. PaymentService calls AuthorizeNetGateway.void() with transaction ID
7. On Success:
   - Update void transaction (status: SUCCESS)
   - Update authorization transaction (status: VOIDED)
   - Update Order (status: CANCELLED)
   - Return success response
8. On Failure:
   - Update void transaction (status: FAILED)
   - Return error response

### Important Notes
- Cannot void after settlement (use refund instead)
- Cannot void if already fully captured
- Store original transaction ID for reference

---

## Flow 5: Refund (Return Funds)

### Use Case
Return funds to customer after a capture has been completed.

### Sequence
```
Client → Controller → PaymentService → PaymentTransactionRepository
                                                ↓
                                    (find capture transaction)
                                                ↓
                          PaymentService → AuthorizeNetGateway → Authorize.Net API
                                                                    ↓
Client ← Controller ← PaymentService ← AuthorizeNetGateway ← Response
```

### Steps
1. Client sends refund request with capture transaction ID
2. Controller validates request
3. PaymentService retrieves capture transaction
4. Validate:
   - Transaction exists and type is CAPTURE
   - Status is SUCCESS (captured)
   - Amount to refund ≤ captured amount
   - Not already fully refunded
5. PaymentService creates PaymentTransaction (type: REFUND, status: PENDING)
6. PaymentService calls AuthorizeNetGateway.refund() with capture transaction ID
7. On Success:
   - Update refund transaction (status: SUCCESS)
   - Update capture transaction (linked reference)
   - Update Order (status: REFUNDED if fully refunded)
   - Return success response
8. On Failure:
   - Update refund transaction (status: FAILED)
   - Return error response

### Partial Refund Support
- Multiple refunds allowed up to captured amount
- Track: `capturedAmount`, `refundedAmount`, `remainingAmount`
- Order status: REFUNDED only when fully refunded

### API Request Example
```json
POST /api/payments/{captureTransactionId}/refund
{
  "amount": 50.00,
  "currency": "USD",
  "reason": "Customer request"
}
```

---

## State Transition Rules

### PaymentTransaction State Transitions

```
AUTHORIZE:
  PENDING → SUCCESS (authorized)
  PENDING → FAILED

CAPTURE:
  PENDING → SUCCESS (captured)
  PENDING → FAILED
  (requires: authorization in SUCCESS state)

VOID:
  PENDING → SUCCESS (voided)
  PENDING → FAILED
  (requires: authorization in SUCCESS state, not captured, within time window)

REFUND:
  PENDING → SUCCESS (refunded)
  PENDING → FAILED
  (requires: capture in SUCCESS state)

PURCHASE:
  PENDING → SUCCESS (completed)
  PENDING → FAILED
```

### Order State Transitions

```
PENDING → PROCESSING → COMPLETED
   │          │            │
   │          │            └──→ REFUNDED (if fully refunded)
   │          │
   │          └──→ FAILED
   │
   └──→ CANCELLED (if voided)
```

---

## Idempotency Handling

### Problem
Prevent duplicate transactions if client retries due to network issues.

### Solution
1. Client includes `idempotencyKey` in request (UUID)
2. Service checks if transaction with this key exists
3. If exists and successful, return existing transaction
4. If exists and failed, allow retry (optional: limit retries)
5. Store idempotency key in PaymentTransaction entity

### Implementation
```java
@Column(unique = true)
private String idempotencyKey;

// In service:
Optional<PaymentTransaction> existing = 
    transactionRepository.findByIdempotencyKey(idempotencyKey);
if (existing.isPresent() && existing.get().getStatus() == SUCCESS) {
    return existing.get(); // Return existing successful transaction
}
```

---

## Error Scenarios and Handling

### Gateway Errors
- **Network Timeout**: Mark transaction as PENDING, allow retry
- **Invalid Credentials**: 401, log security event
- **Insufficient Funds**: Return specific error code, don't retry
- **Invalid Card**: Return validation error, don't retry
- **Gateway Down**: Mark as PENDING, implement retry with backoff

### Business Logic Errors
- **Invalid Amount**: Validation error (400)
- **Invalid State Transition**: Business rule violation (422)
- **Transaction Not Found**: Not found error (404)
- **Duplicate Transaction**: Conflict error (409) or return existing

### Retry Strategy
- **Non-retryable**: Validation errors, invalid card, insufficient funds
- **Retryable**: Network errors, timeouts, gateway errors (5xx)
- **Retry Logic**: Exponential backoff (1s, 2s, 4s, 8s), max 3 retries
- **Idempotent**: Use same idempotency key for retries

---

## Reconciliation Process

### Background Job
Periodically check for transactions stuck in PENDING state:

1. Query transactions with status PENDING older than X minutes
2. For each transaction, query Authorize.Net for status
3. Update transaction status based on gateway response
4. Update related Order status
5. Alert if transaction cannot be reconciled

### Implementation Considerations
- Use Spring @Scheduled for periodic job
- Use @Transactional for consistency
- Implement circuit breaker for gateway calls
- Log all reconciliation actions
