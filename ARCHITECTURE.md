# Payment Processing Backend - Architecture Documentation

## Table of Contents

1. [Overview](#overview)
2. [Payment Flows Overview](#payment-flows-overview)
3. [Authorize.Net Integration](#authorizenet-integration)
4. [Database Schema](#database-schema)
5. [Entity Relationships](#entity-relationships)
6. [System Architecture](#system-architecture)
7. [Security Architecture](#security-architecture)

---

## Overview

The Payment Processing Backend is a Spring Boot application that provides RESTful APIs for processing payments through Authorize.Net. The system supports multiple payment operations including purchases, authorizations, captures, voids, and refunds.

### Key Characteristics

- **Layered Architecture**: Clean separation between presentation, business logic, and data access layers
- **Gateway Abstraction**: Payment gateway integration abstracted through interfaces for flexibility
- **State Management**: Comprehensive state tracking for orders and transactions
- **Security**: JWT-based authentication with refresh tokens
- **Transaction Safety**: ACID transactions with idempotency support
- **Error Handling**: Structured error responses with appropriate HTTP status codes

---

## Payment Flows Overview

The system supports five main payment operations, each with specific use cases and state transitions.

### 1. Purchase Flow (Authorize + Capture)

**Purpose**: Complete a payment transaction in a single step - authorize and capture funds immediately.

**Flow Steps**:
1. Client sends purchase request with payment method and order details
2. System validates order (status, amount, ownership)
3. System creates `PaymentTransaction` with type `PURCHASE` and status `PENDING`
4. System calls Authorize.Net API to authorize and capture in one transaction
5. On success:
   - Transaction status → `SUCCESS`
   - Order status → `COMPLETED`
   - Transaction stores gateway transaction ID
6. On failure:
   - Transaction status → `FAILED`
   - Order status → `FAILED`
   - Error details stored in transaction

**State Transitions**:
```
Order: PENDING → COMPLETED (success) or FAILED (failure)
Transaction: PENDING → SUCCESS or FAILED
```

**Use Cases**:
- Immediate payment processing (e-commerce checkout)
- One-step payment confirmation
- Real-time payment verification

### 2. Authorize-Only Flow

**Purpose**: Hold funds on a payment method without capturing them immediately. Useful for order verification before shipment.

**Flow Steps**:
1. Client sends authorize request
2. System validates order and creates authorization transaction
3. System calls Authorize.Net API to authorize only (no capture)
4. On success:
   - Transaction status → `SUCCESS`
   - Transaction stores authorized amount and expiration date
   - Order status → `PROCESSING`
5. Funds are held but not captured

**State Transitions**:
```
Order: PENDING → PROCESSING
Transaction: PENDING → SUCCESS (AUTHORIZE type)
```

**Use Cases**:
- Pre-authorization for order verification
- Delayed capture scenarios
- Fraud verification before capturing

**Important**: Authorizations expire (typically 30 days). They must be captured or voided before expiration.

### 3. Capture Flow

**Purpose**: Capture previously authorized funds. Supports full or partial capture.

**Flow Steps**:
1. Client sends capture request with authorization transaction ID and amount
2. System validates:
   - Authorization transaction exists and is successful
   - Authorization has not expired
   - Capture amount does not exceed remaining authorized amount
   - Authorization has not been fully captured
3. System creates new `PaymentTransaction` with type `CAPTURE`
4. System calls Authorize.Net API to capture authorized funds
5. On success:
   - Capture transaction status → `SUCCESS`
   - Parent authorization transaction's `capturedAmount` is updated
   - Order status:
     - `COMPLETED` if fully captured
     - `PARTIALLY_CAPTURED` if partially captured

**State Transitions**:
```
Order: PROCESSING → COMPLETED (full capture) or PARTIALLY_CAPTURED (partial)
Authorization Transaction: capturedAmount updated
Capture Transaction: PENDING → SUCCESS
```

**Use Cases**:
- Capturing authorized funds after order verification
- Partial shipment scenarios (capture as items ship)
- Multi-step payment processing

### 4. Cancel/Void Flow

**Purpose**: Cancel an authorization before it is captured. Releases held funds.

**Flow Steps**:
1. Client sends cancel request with authorization transaction ID
2. System validates:
   - Authorization transaction exists and is successful
   - Authorization has not been captured (capturedAmount = 0)
   - Authorization is within void window (typically 24 hours)
3. System creates new `PaymentTransaction` with type `VOID`
4. System calls Authorize.Net API to void the authorization
5. On success:
   - Void transaction status → `SUCCESS`
   - Parent authorization transaction status → `VOIDED`
   - Order status → `CANCELLED`
   - Funds are released

**State Transitions**:
```
Order: PROCESSING → CANCELLED
Authorization Transaction: SUCCESS → VOIDED
Void Transaction: PENDING → SUCCESS
```

**Use Cases**:
- Order cancellation before capture
- Customer cancellation requests
- Inventory unavailability after authorization

**Constraints**:
- Can only void uncaptured authorizations
- Must be within 24-hour window (Authorize.Net limitation)
- Cannot void after capture (must use refund instead)

### 5. Refund Flow

**Purpose**: Refund funds from a previously captured or purchased transaction. Supports full and partial refunds.

**Flow Steps**:
1. Client sends refund request with original transaction ID and amount
2. System validates:
   - Original transaction exists and is type `PURCHASE` or `CAPTURE`
   - Original transaction status is `SUCCESS`
   - Refund amount does not exceed remaining refundable amount
   - Currency matches original transaction
3. System creates new `PaymentTransaction` with type `REFUND`
4. System calls Authorize.Net API to process refund
5. On success:
   - Refund transaction status → `SUCCESS`
   - Parent transaction's `refundedAmount` is updated
   - Order status:
     - `REFUNDED` if fully refunded
     - `PARTIALLY_REFUNDED` if partially refunded

**State Transitions**:
```
Order: COMPLETED → REFUNDED (full) or PARTIALLY_REFUNDED (partial)
Original Transaction: refundedAmount updated
Refund Transaction: PENDING → SUCCESS
```

**Use Cases**:
- Customer returns and refunds
- Partial refunds for partial returns
- Payment errors requiring refunds

**Constraints**:
- Can only refund `PURCHASE` or `CAPTURE` transactions
- Cannot refund more than the captured amount
- Multiple refunds allowed (partial refunds)

### Payment Flow State Machine

```
┌─────────┐
│ PENDING │ (Order created)
└────┬────┘
     │
     ├─── Purchase ───→ ┌──────────┐
     │                  │ COMPLETED│
     │                  └──────────┘
     │
     ├─── Authorize ───→ ┌───────────┐
     │                   │ PROCESSING│
     │                   └─────┬─────┘
     │                         │
     │                         ├─── Capture ───→ ┌──────────┐
     │                         │                 │ COMPLETED│
     │                         │                 └──────────┘
     │                         │
     │                         ├─── Cancel ───→ ┌──────────┐
     │                                         │ CANCELLED│
     │                                         └──────────┘
     │
     │                         └─── Capture (partial) ───→ ┌──────────────────┐
     │                                                      │PARTIALLY_CAPTURED│
     │                                                      └──────────────────┘
     │
     └─── Any Operation Failed ───→ ┌────────┐
                                    │ FAILED │
                                    └────────┘

Refund Transitions:
┌──────────┐                    ┌──────────┐
│ COMPLETED│ ─── Refund ───→    │ REFUNDED │ (full)
└──────────┘                    └──────────┘

┌──────────┐                    ┌──────────────────┐
│ COMPLETED│ ─── Refund ───→    │PARTIALLY_REFUNDED│ (partial)
└──────────┘                    └──────────────────┘
```

---

## Authorize.Net Integration

### Integration Architecture

The system uses a gateway abstraction pattern to isolate Authorize.Net SDK from business logic:

```
PaymentService
    ↓
PaymentGateway (Interface)
    ↓
AuthorizeNetGateway (Implementation)
    ↓
Authorize.Net Java SDK
    ↓
Authorize.Net API
```

### Gateway Interface

The `PaymentGateway` interface defines the contract for payment operations:

```java
public interface PaymentGateway {
    PurchaseResponse purchase(PurchaseRequest request);
    AuthorizeResponse authorize(AuthorizeRequest request);
    CaptureResponse capture(CaptureRequest request);
    VoidResponse voidTransaction(VoidRequest request);
    RefundResponse refund(RefundRequest request);
}
```

This abstraction allows:
- Easy testing with mock implementations
- Future gateway switching (Stripe, PayPal, etc.)
- Clear separation of concerns

### AuthorizeNetGateway Implementation

The `AuthorizeNetGateway` class implements the `PaymentGateway` interface using the Authorize.Net Java SDK.

#### Initialization

For each API call, the gateway:
1. Sets merchant authentication (API Login ID + Transaction Key)
2. Sets environment (sandbox or production)
3. Configures SDK with credentials

```java
MerchantAuthenticationType merchantAuth = new MerchantAuthenticationType();
merchantAuth.setName(apiLoginId);
merchantAuth.setTransactionKey(transactionKey);
ApiOperationBase.setMerchantAuthentication(merchantAuth);
ApiOperationBase.setEnvironment(environment);
```

#### Transaction Types

**1. Purchase (AUTH_CAPTURE_TRANSACTION)**
- Single API call that authorizes and captures
- Transaction type: `AUTH_CAPTURE_TRANSACTION`
- Includes credit card details and order information

**2. Authorize (AUTH_ONLY_TRANSACTION)**
- Authorizes funds without capture
- Transaction type: `AUTH_ONLY_TRANSACTION`
- Returns authorization code and transaction ID

**3. Capture (PRIOR_AUTH_CAPTURE_TRANSACTION)**
- Captures previously authorized funds
- Transaction type: `PRIOR_AUTH_CAPTURE_TRANSACTION`
- Requires reference transaction ID from authorization

**4. Void (VOID_TRANSACTION)**
- Cancels an authorization before capture
- Transaction type: `VOID_TRANSACTION`
- Requires reference transaction ID
- Must be within 24-hour window

**5. Refund (REFUND_TRANSACTION)**
- Refunds a captured transaction
- Transaction type: `REFUND_TRANSACTION`
- Requires reference transaction ID
- Can be full or partial refund

### Request/Response Mapping

#### Request Mapping

Internal DTOs → Gateway DTOs → SDK Request Types

Example (Purchase):
```
PaymentRequest (internal)
  ↓
PurchaseRequest (gateway DTO)
  ↓
TransactionRequestType (SDK)
```

#### Response Mapping

SDK Response → Gateway DTO → Internal Response

Example:
```
CreateTransactionResponse (SDK)
  ↓
PurchaseResponse (gateway DTO)
  ↓
PaymentResponse (internal)
```

### Error Handling

The gateway handles various error scenarios:

1. **Gateway Errors**: SDK exceptions, network failures
2. **Transaction Declines**: Card declined, insufficient funds
3. **Validation Errors**: Invalid card, expired authorization
4. **System Errors**: Timeouts, API errors

Error responses include:
- Error codes
- Error messages
- Response codes
- Gateway-specific error details

### Configuration

Authorize.Net configuration is externalized:

```yaml
authorize-net:
  api-login-id: ${AUTHORIZE_NET_API_LOGIN_ID}
  transaction-key: ${AUTHORIZE_NET_TRANSACTION_KEY}
  environment: ${AUTHORIZE_NET_ENVIRONMENT:sandbox}
  endpoint: ${AUTHORIZE_NET_ENDPOINT}
```

**Environment Variables**:
- `AUTHORIZE_NET_API_LOGIN_ID`: API Login ID from Authorize.Net
- `AUTHORIZE_NET_TRANSACTION_KEY`: Transaction Key from Authorize.Net
- `AUTHORIZE_NET_ENVIRONMENT`: `sandbox` or `production`

### SDK Usage Patterns

1. **Initialize SDK**: Set merchant authentication and environment
2. **Build Request**: Create transaction request with type and data
3. **Execute Transaction**: Call SDK controller
4. **Process Response**: Extract transaction ID, status, errors
5. **Map to Internal Format**: Convert SDK response to internal DTO

### Transaction Reference IDs

Authorize.Net uses transaction IDs to reference previous transactions:

- **Authorization ID**: Used for capture and void operations
- **Capture ID**: Used for refunds
- **Purchase ID**: Used for refunds

The system stores these IDs in `PaymentTransaction.authorizeNetTransactionId` for:
- Linking related transactions (capture → authorization)
- Reference in subsequent operations (void, refund)
- Reconciliation and auditing

### Sandbox Testing

The system supports Authorize.Net sandbox for testing:

**Test Card Numbers**:
- `4111111111111111` - Visa (approved)
- `4000000000000002` - Visa (declined)
- `5424000000000015` - MasterCard (approved)

**Test Scenarios**:
- Successful transactions
- Declined transactions
- Error responses
- Partial operations

---

## Database Schema

### Overview

The database schema consists of four main tables:

1. `users` - User accounts and authentication
2. `orders` - Order information and status
3. `payment_transactions` - Payment transaction records
4. `refresh_tokens` - JWT refresh token storage

### Table Definitions

#### 1. users Table

Stores user account information and authentication credentials.

```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
```

**Fields**:
- `id`: Primary key (auto-increment)
- `email`: User email address (unique)
- `password_hash`: BCrypt-hashed password
- `role`: User role (`USER`, `ADMIN`)
- `enabled`: Account enabled flag
- `created_at`: Account creation timestamp

**Constraints**:
- Email must be unique
- Email, password_hash, role are required

**Relationships**:
- One-to-many with `orders`
- One-to-many with `refresh_tokens`

#### 2. orders Table

Stores order information and tracks order status.

```sql
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    order_number VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    total_amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_order_number ON orders(order_number);
CREATE INDEX idx_orders_status ON orders(status);
```

**Fields**:
- `id`: Primary key (auto-increment)
- `user_id`: Foreign key to `users` table
- `order_number`: Unique order identifier
- `status`: Order status (enum: PENDING, PROCESSING, COMPLETED, etc.)
- `total_amount`: Order total amount (decimal precision)
- `currency`: Currency code (ISO 4217, e.g., USD)
- `description`: Order description
- `created_at`: Order creation timestamp
- `updated_at`: Last update timestamp (auto-updated)

**Constraints**:
- `user_id` must reference valid user
- `order_number` must be unique
- Status, total_amount, currency are required

**Relationships**:
- Many-to-one with `users`
- One-to-many with `payment_transactions`

#### 3. payment_transactions Table

Stores all payment transaction records with comprehensive tracking.

```sql
CREATE TABLE payment_transactions (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    parent_transaction_id BIGINT REFERENCES payment_transactions(id),
    transaction_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    authorize_net_transaction_id VARCHAR(50) UNIQUE,
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    authorized_amount DECIMAL(19,4),
    captured_amount DECIMAL(19,4),
    refunded_amount DECIMAL(19,4),
    payment_method_encrypted VARCHAR(500),
    last_four_digits VARCHAR(4),
    card_brand VARCHAR(20),
    card_expiry_month INTEGER,
    card_expiry_year INTEGER,
    idempotency_key VARCHAR(36) UNIQUE,
    authorization_expires_at TIMESTAMP,
    error_code VARCHAR(50),
    error_message TEXT,
    gateway_response JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payment_transactions_order_id ON payment_transactions(order_id);
CREATE INDEX idx_payment_transactions_parent_id ON payment_transactions(parent_transaction_id);
CREATE INDEX idx_payment_transactions_type ON payment_transactions(transaction_type);
CREATE INDEX idx_payment_transactions_status ON payment_transactions(status);
CREATE INDEX idx_payment_transactions_gateway_id ON payment_transactions(authorize_net_transaction_id);
CREATE INDEX idx_payment_transactions_idempotency ON payment_transactions(idempotency_key);
```

**Fields**:

**Identity & Relationships**:
- `id`: Primary key (auto-increment)
- `order_id`: Foreign key to `orders` table
- `parent_transaction_id`: Self-referential foreign key for linked transactions (capture/void/refund → authorization/purchase)

**Transaction Classification**:
- `transaction_type`: Type of transaction (PURCHASE, AUTHORIZE, CAPTURE, VOID, REFUND)
- `status`: Transaction status (PENDING, SUCCESS, FAILED, VOIDED, REFUNDED)

**Gateway Integration**:
- `authorize_net_transaction_id`: Authorize.Net transaction ID (unique)
- `gateway_response`: Raw gateway response (JSONB for flexibility)

**Amount Tracking**:
- `amount`: Transaction amount
- `currency`: Currency code
- `authorized_amount`: Amount authorized (for AUTHORIZE transactions)
- `captured_amount`: Amount captured (accumulated for AUTHORIZE, set for PURCHASE)
- `refunded_amount`: Amount refunded (accumulated for refunds)

**Payment Method**:
- `payment_method_encrypted`: Encrypted payment method details (optional)
- `last_four_digits`: Last 4 digits of card number
- `card_brand`: Card brand (Visa, MasterCard, etc.)
- `card_expiry_month`: Card expiry month
- `card_expiry_year`: Card expiry year

**Operational**:
- `idempotency_key`: Unique key for idempotency (prevents duplicate transactions)
- `authorization_expires_at`: Expiration timestamp for authorizations
- `error_code`: Gateway error code (if failed)
- `error_message`: Error message (if failed)

**Audit**:
- `created_at`: Transaction creation timestamp
- `updated_at`: Last update timestamp

**Constraints**:
- `order_id` must reference valid order
- `parent_transaction_id` can reference another transaction (self-referential)
- `authorize_net_transaction_id` is unique (if set)
- `idempotency_key` is unique (if set)
- Transaction type and status are required

**Relationships**:
- Many-to-one with `orders`
- Self-referential (parent-child relationship)
- Parent transaction: AUTHORIZE or PURCHASE
- Child transactions: CAPTURE, VOID, REFUND

#### 4. refresh_tokens Table

Stores JWT refresh tokens for token refresh functionality.

```sql
CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(500) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
```

**Fields**:
- `id`: Primary key (auto-increment)
- `user_id`: Foreign key to `users` table
- `token`: Refresh token value (unique)
- `expires_at`: Token expiration timestamp
- `revoked`: Revocation flag (for logout)
- `created_at`: Token creation timestamp

**Constraints**:
- `user_id` must reference valid user
- Token must be unique
- Expiration timestamp is required

**Relationships**:
- Many-to-one with `users`

### Indexes

Indexes are created for:
- Foreign keys (for join performance)
- Unique constraints (email, order_number, transaction IDs)
- Frequently queried fields (status, transaction_type)
- Search fields (idempotency_key, token)

### Data Types

- **BIGSERIAL/BIGINT**: Large integer IDs (64-bit)
- **VARCHAR**: Variable-length strings with length limits
- **DECIMAL(19,4)**: Monetary amounts (19 digits total, 4 decimal places)
- **TIMESTAMP**: Date and time values
- **BOOLEAN**: True/false values
- **TEXT**: Unbounded text fields
- **JSONB**: JSON data (PostgreSQL-specific, for gateway responses)

### Constraints

**Primary Keys**: All tables have `id` as primary key (auto-increment)

**Foreign Keys**: 
- `orders.user_id` → `users.id`
- `payment_transactions.order_id` → `orders.id`
- `payment_transactions.parent_transaction_id` → `payment_transactions.id`
- `refresh_tokens.user_id` → `users.id`

**Unique Constraints**:
- `users.email`
- `orders.order_number`
- `payment_transactions.authorize_net_transaction_id`
- `payment_transactions.idempotency_key`
- `refresh_tokens.token`

**Check Constraints** (enforced at application level):
- Amounts must be positive
- Currency codes must be valid
- Status values must be from enum
- Transaction types must be from enum

### Audit Fields

All tables include audit fields:
- `created_at`: Set on record creation
- `updated_at`: Set on record creation and updates (via JPA auditing)

---

## Entity Relationships

### Relationship Diagram

```
┌─────────────┐
│    users    │
│─────────────│
│ id (PK)     │
│ email       │◄──────┐
│ password    │       │
│ role        │       │
│ enabled     │       │
│ created_at  │       │
└─────────────┘       │
       │              │
       │ 1            │ N
       │              │
       │ N            │
       │              │
┌──────▼──────────┐   │   ┌──────────────────┐
│     orders      │   │   │  refresh_tokens  │
│─────────────────│   │   │──────────────────│
│ id (PK)         │   │   │ id (PK)          │
│ user_id (FK)    ├───┘   │ user_id (FK)     ├───┘
│ order_number    │       │ token            │
│ status          │       │ expires_at       │
│ total_amount    │       │ revoked          │
│ currency        │       │ created_at       │
│ description     │       └──────────────────┘
│ created_at      │
│ updated_at      │
└──────┬──────────┘
       │
       │ 1
       │
       │ N
       │
┌──────▼──────────────────────────┐
│   payment_transactions          │
│─────────────────────────────────│
│ id (PK)                         │
│ order_id (FK) ──────────────────┘
│ parent_transaction_id (FK) ─────┐
│ transaction_type                │ │ (self-referential)
│ status                          │ │
│ authorize_net_transaction_id    │ │
│ amount                          │ │
│ authorized_amount               │ │
│ captured_amount                 │ │
│ refunded_amount                 │ │
│ ... (other fields)              │ │
└─────────────────────────────────┘ │
                                    │
                                    │
                                    └───────┐
                                            │
                                    ┌───────┘
                                    │
                                    │
```

### Detailed Relationships

#### 1. User → Orders (One-to-Many)

**Relationship**: One user can have many orders

**JPA Mapping**:
```java
// User entity
@OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
private List<Order> orders;

// Order entity
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id", nullable = false)
private User user;
```

**Characteristics**:
- **Direction**: Bidirectional (navigation from both sides)
- **Fetch Type**: LAZY (orders loaded on demand)
- **Cascade**: ALL (cascading operations)
- **Join Column**: `user_id` in `orders` table

**Business Rules**:
- Every order must belong to a user
- Deleting a user cascades to delete all their orders
- Users can query their orders through the relationship

#### 2. User → Refresh Tokens (One-to-Many)

**Relationship**: One user can have many refresh tokens (for multiple devices/sessions)

**JPA Mapping**:
```java
// User entity
@OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
private List<RefreshToken> refreshTokens;

// RefreshToken entity
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id", nullable = false)
private User user;
```

**Characteristics**:
- **Direction**: Bidirectional
- **Fetch Type**: LAZY
- **Cascade**: ALL
- **Join Column**: `user_id` in `refresh_tokens` table

**Business Rules**:
- Users can have multiple active refresh tokens (different devices)
- Tokens are revoked on logout or expiration
- Deleting a user cascades to delete all refresh tokens

#### 3. Order → Payment Transactions (One-to-Many)

**Relationship**: One order can have many payment transactions

**JPA Mapping**:
```java
// Order entity
@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
private List<PaymentTransaction> transactions;

// PaymentTransaction entity
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "order_id", nullable = false)
private Order order;
```

**Characteristics**:
- **Direction**: Bidirectional
- **Fetch Type**: LAZY
- **Cascade**: ALL with orphan removal
- **Join Column**: `order_id` in `payment_transactions` table

**Business Rules**:
- Every payment transaction must belong to an order
- Orders can have multiple transactions (authorize, capture, refund, etc.)
- Transaction history is maintained for auditing
- Deleting an order cascades to delete all transactions

**Transaction Types per Order**:
- Initial: AUTHORIZE or PURCHASE
- Follow-up: CAPTURE (from AUTHORIZE), VOID (from AUTHORIZE), REFUND (from PURCHASE/CAPTURE)

#### 4. Payment Transaction → Payment Transaction (Self-Referential)

**Relationship**: Parent-child relationship for transaction linking

**JPA Mapping**:
```java
// PaymentTransaction entity
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "parent_transaction_id")
private PaymentTransaction parentTransaction;

@OneToMany(mappedBy = "parentTransaction", cascade = CascadeType.ALL)
private List<PaymentTransaction> childTransactions;
```

**Characteristics**:
- **Direction**: Bidirectional (self-referential)
- **Fetch Type**: LAZY
- **Cascade**: ALL
- **Join Column**: `parent_transaction_id` in `payment_transactions` table (self-reference)
- **Optional**: Parent transaction can be null (for initial transactions)

**Transaction Hierarchy**:

```
AUTHORIZE Transaction (parent)
    ├── CAPTURE Transaction (child)
    └── VOID Transaction (child)

PURCHASE Transaction (parent, no children initially)
    └── REFUND Transaction (child)

CAPTURE Transaction (parent)
    └── REFUND Transaction (child)
```

**Business Rules**:
- Initial transactions (AUTHORIZE, PURCHASE) have no parent
- CAPTURE transactions reference AUTHORIZE transaction as parent
- VOID transactions reference AUTHORIZE transaction as parent
- REFUND transactions reference PURCHASE or CAPTURE transaction as parent
- Multiple children allowed (e.g., multiple captures from one authorization)
- Transaction chain maintains audit trail

**Examples**:

1. **Authorize → Capture Flow**:
   ```
   AUTHORIZE (id=1, parent=null)
       └── CAPTURE (id=2, parent=1)
   ```

2. **Purchase → Refund Flow**:
   ```
   PURCHASE (id=1, parent=null)
       └── REFUND (id=2, parent=1)
   ```

3. **Authorize → Multiple Captures**:
   ```
   AUTHORIZE (id=1, parent=null)
       ├── CAPTURE (id=2, parent=1, amount=50.00)
       └── CAPTURE (id=3, parent=1, amount=50.00)
   ```

4. **Purchase → Multiple Refunds**:
   ```
   PURCHASE (id=1, parent=null)
       ├── REFUND (id=2, parent=1, amount=30.00)
       └── REFUND (id=3, parent=1, amount=20.00)
   ```

### Relationship Summary

| Relationship | Type | Parent | Child | Join Column | Cascade |
|-------------|------|--------|-------|-------------|---------|
| User → Orders | One-to-Many | users | orders | user_id | ALL |
| User → Refresh Tokens | One-to-Many | users | refresh_tokens | user_id | ALL |
| Order → Payment Transactions | One-to-Many | orders | payment_transactions | order_id | ALL |
| Payment Transaction → Payment Transaction | Self-Referential | payment_transactions | payment_transactions | parent_transaction_id | ALL |

### Fetch Strategies

**LAZY Loading (Default)**:
- Relationships are loaded on-demand
- Improves initial query performance
- Prevents N+1 query problems with proper fetching

**EAGER Loading (Avoid)**:
- Not used in this design
- Can cause performance issues
- LAZY + explicit fetching is preferred

**Fetch Joins** (when needed):
```java
@Query("SELECT o FROM Order o JOIN FETCH o.transactions WHERE o.id = :id")
Optional<Order> findByIdWithTransactions(@Param("id") Long id);
```

### Cascade Operations

**CascadeType.ALL**:
- All operations cascade (PERSIST, MERGE, REMOVE, REFRESH, DETACH)
- Deleting parent automatically deletes children
- Saving parent automatically saves children

**Orphan Removal**:
- Used for Order → PaymentTransactions
- Removing transaction from order's collection deletes it from database
- Ensures data consistency

### Relationship Navigation

**Bidirectional Navigation**:
- Entities can navigate in both directions
- `user.getOrders()` - Get all orders for a user
- `order.getUser()` - Get user who owns the order
- `order.getTransactions()` - Get all transactions for an order
- `transaction.getOrder()` - Get order for a transaction
- `transaction.getParentTransaction()` - Get parent transaction
- `transaction.getChildTransactions()` - Get child transactions

**Unidirectional Queries**:
- Repository methods provide efficient querying
- `findByUserId()` - Query orders by user ID
- `findByOrderId()` - Query transactions by order ID
- Avoids loading entire object graph

---

## System Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Client Applications                    │
│              (Web, Mobile, External APIs)                 │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │ HTTPS/REST
                        │
┌───────────────────────▼─────────────────────────────────┐
│                   REST API Layer                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │AuthController│  │PaymentController│  │(Future)      │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└───────────────────────┬─────────────────────────────────┘
                        │
                        │
┌───────────────────────▼─────────────────────────────────┐
│                  Service Layer                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ AuthService  │  │PaymentService│  │  JwtService  │  │
│  └──────────────┘  └──────┬───────┘  └──────────────┘  │
└───────────────────────────┼─────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
┌───────▼────────┐  ┌───────▼────────┐  ┌───────▼────────┐
│   Repository   │  │    Gateway     │  │    Security    │
│     Layer      │  │     Layer      │  │     Layer      │
│                │  │                │  │                │
│ UserRepository │  │ PaymentGateway │  │  JWT Filter    │
│ OrderRepository│  │AuthorizeNetGate│  │ UserDetails    │
│TransactionRepo │  │     way        │  │   Service      │
│RefreshTokenRepo│  │                │  │                │
└───────┬────────┘  └───────┬────────┘  └────────────────┘
        │                   │
        │                   │
┌───────▼────────┐  ┌───────▼────────┐
│   PostgreSQL   │  │ Authorize.Net  │
│    Database    │  │      API       │
└────────────────┘  └────────────────┘
```

### Component Interactions

**Request Flow** (Payment Purchase):
1. Client → PaymentController (HTTP request)
2. PaymentController → PaymentService (business logic)
3. PaymentService → OrderRepository (validate order)
4. PaymentService → PaymentGateway (process payment)
5. PaymentGateway → Authorize.Net API (external call)
6. PaymentService → PaymentTransactionRepository (save transaction)
7. PaymentService → OrderRepository (update order)
8. PaymentService → PaymentController (response)
9. PaymentController → Client (HTTP response)

**Authentication Flow**:
1. Client → AuthController (login request)
2. AuthController → AuthService (authentication)
3. AuthService → UserRepository (load user)
4. AuthService → PasswordEncoder (verify password)
5. AuthService → JwtService (generate tokens)
6. AuthService → RefreshTokenRepository (save refresh token)
7. AuthService → AuthController (tokens)
8. AuthController → Client (HTTP response with tokens)

### Layer Responsibilities

**Presentation Layer (Controllers)**:
- HTTP request/response handling
- Request validation
- Authentication/authorization enforcement
- Status code management

**Service Layer**:
- Business logic implementation
- Transaction orchestration
- State management
- Exception handling
- Data transformation

**Repository Layer**:
- Data access abstraction
- Query execution
- Entity persistence
- Database interaction

**Gateway Layer**:
- External API integration
- Request/response mapping
- Error handling
- Retry logic

**Security Layer**:
- Authentication filtering
- Token validation
- User details loading
- Authorization

---

## Security Architecture

### Authentication Flow

1. **Registration/Login**: User provides credentials
2. **Password Verification**: BCrypt hash comparison
3. **Token Generation**: JWT access token + refresh token
4. **Token Storage**: Refresh token stored in database
5. **Token Validation**: JWT filter validates tokens on requests
6. **Token Refresh**: Refresh token used to get new access token

### JWT Token Structure

**Access Token**:
- Claims: userId, email, role
- Expiration: 15 minutes (configurable)
- Stored: Client-side only
- Usage: Authorization header

**Refresh Token**:
- Stored: Database + client-side
- Expiration: 7 days (configurable)
- Usage: Token refresh endpoint
- Revocable: Can be revoked on logout

### Authorization

- Role-based access control (prepared)
- Resource ownership validation
- Transaction authorization checks

### Data Security

- Password hashing: BCrypt
- Sensitive data: Encrypted at rest (prepared)
- Payment data: Minimal storage (last 4 digits only)
- API credentials: Environment variables

---

This architecture provides a comprehensive, production-ready foundation for payment processing with clear separation of concerns, robust error handling, and secure transaction processing.
