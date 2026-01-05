# Screenshot Guide - All Transaction Types

This guide will help you create all transaction types and capture the required screenshots.

## Prerequisites
- Application running at `http://localhost:8080`
- Authorize.Net sandbox credentials configured
- Access to database container

## Step 1: Register and Login

### Register a User
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password123",
    "role": "USER"
  }'
```

### Login to Get Access Token
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "password123"
  }'
```

**Save the `accessToken` and `userId` from the response!**

---

## Step 1.5: Create Orders in Database

Since orders must exist before making payment requests, create them directly in the database:

### Connect to Database
```bash
docker exec -it payment-db psql -U postgres -d paymentdb
```

### Create Orders
```sql
-- Get your user ID (replace 'test@example.com' with your email)
SELECT id, email FROM users WHERE email = 'test@example.com';

-- Create orders (replace USER_ID with your actual user ID from above)
INSERT INTO orders (user_id, order_number, status, total_amount, currency, description, created_at, updated_at)
VALUES 
    ((SELECT id FROM users WHERE email = 'test@example.com'), 'ORD-001', 'PENDING', 100.00, 'USD', 'Order for Purchase', NOW(), NOW()),
    ((SELECT id FROM users WHERE email = 'test@example.com'), 'ORD-002', 'PENDING', 150.00, 'USD', 'Order for Authorize', NOW(), NOW()),
    ((SELECT id FROM users WHERE email = 'test@example.com'), 'ORD-003', 'PENDING', 200.00, 'USD', 'Order for Void', NOW(), NOW());

-- Verify orders were created
SELECT id, order_number, status, total_amount FROM orders ORDER BY id;
```

**Save the order IDs!** (You'll need them for the payment requests)

---

## Step 2: Create All Transaction Types

Replace `YOUR_ACCESS_TOKEN` with the token from Step 1.

### Transaction 1: PURCHASE
```bash
curl -X POST http://localhost:8080/api/payments/purchase \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "X-Idempotency-Key: purchase-001" \
  -d '{
    "orderId": <ORDER_ID_1>,
    "amount": 100.00,
    "currency": "USD",
    "paymentMethod": {
      "cardNumber": "4111111111111111",
      "expiryMonth": "12",
      "expiryYear": "2025",
      "cvv": "123",
      "cardholderName": "John Doe"
    },
    "description": "Purchase transaction for screenshot"
  }'
```

**Save the `transactionId` and `gatewayTransactionId` from response!**

### Transaction 2: AUTHORIZE
```bash
curl -X POST http://localhost:8080/api/payments/authorize \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "X-Idempotency-Key: authorize-001" \
  -d '{
    "orderId": <ORDER_ID_2>,
    "amount": 150.00,
    "currency": "USD",
    "paymentMethod": {
      "cardNumber": "4111111111111111",
      "expiryMonth": "12",
      "expiryYear": "2025",
      "cvv": "123",
      "cardholderName": "Jane Smith"
    },
    "description": "Authorization transaction for screenshot"
  }'
```

**Save the `transactionId` (this is the authorization transaction ID for next steps)!**

### Transaction 3: CAPTURE (from Transaction 2)
```bash
curl -X POST http://localhost:8080/api/payments/capture \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "X-Idempotency-Key: capture-001" \
  -d '{
    "authorizationTransactionId": <TRANSACTION_ID_FROM_AUTHORIZE>,
    "amount": 150.00,
    "currency": "USD"
  }'
```

### Transaction 4: AUTHORIZE (for VOID - must be separate from Transaction 2)
```bash
curl -X POST http://localhost:8080/api/payments/authorize \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "X-Idempotency-Key: authorize-002" \
  -d '{
    "orderId": <ORDER_ID_3>,
    "amount": 200.00,
    "currency": "USD",
    "paymentMethod": {
      "cardNumber": "4111111111111111",
      "expiryMonth": "12",
      "expiryYear": "2025",
      "cvv": "123",
      "cardholderName": "Bob Johnson"
    },
    "description": "Authorization for void transaction"
  }'
```

**Save the `transactionId` for VOID!**

### Transaction 5: VOID (from Transaction 4)
```bash
curl -X POST http://localhost:8080/api/payments/cancel \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -d '{
    "authorizationTransactionId": <TRANSACTION_ID_FROM_AUTHORIZE_2>
  }'
```

### Transaction 6: REFUND (from Transaction 1 - PURCHASE)
```bash
curl -X POST http://localhost:8080/api/payments/refund \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "X-Idempotency-Key: refund-001" \
  -d '{
    "transactionId": <TRANSACTION_ID_FROM_PURCHASE>,
    "amount": 100.00,
    "currency": "USD",
    "reason": "Refund transaction for screenshot"
  }'
```

---

## Step 3: View Transactions in Database

### Connect to Database
```bash
docker exec -it payment-db psql -U postgres -d paymentdb
```

### Query All Transactions
```sql
SELECT 
    id,
    transaction_type,
    status,
    amount,
    currency,
    authorize_net_transaction_id,
    created_at,
    order_id,
    parent_transaction_id
FROM payment_transactions
ORDER BY created_at;
```

### Detailed View with All Fields
```sql
SELECT 
    id,
    transaction_type,
    status,
    amount,
    currency,
    authorized_amount,
    captured_amount,
    refunded_amount,
    authorize_net_transaction_id,
    last_four_digits,
    card_brand,
    error_code,
    error_message,
    created_at,
    order_id,
    parent_transaction_id
FROM payment_transactions
ORDER BY transaction_type, created_at;
```

### View with Order Information
```sql
SELECT 
    pt.id,
    pt.transaction_type,
    pt.status,
    pt.amount,
    pt.currency,
    pt.authorize_net_transaction_id,
    pt.created_at,
    o.order_number,
    o.status as order_status
FROM payment_transactions pt
JOIN orders o ON pt.order_id = o.id
ORDER BY pt.created_at;
```

### Complete View - All Transaction Types
```sql
SELECT 
    pt.id AS "Transaction ID",
    pt.transaction_type AS "Type",
    pt.status AS "Status",
    pt.amount AS "Amount",
    pt.currency AS "Currency",
    pt.authorize_net_transaction_id AS "Authorize.Net ID",
    pt.last_four_digits AS "Card Last 4",
    pt.card_brand AS "Card Brand",
    pt.parent_transaction_id AS "Parent TX ID",
    o.order_number AS "Order Number",
    pt.created_at AS "Created At"
FROM payment_transactions pt
JOIN orders o ON pt.order_id = o.id
WHERE pt.status != 'FAILED'
ORDER BY pt.transaction_type, pt.created_at;
```

**Take a screenshot of this query result showing all 5 transaction types!**

---

## Step 4: View Transactions in Authorize.Net Sandbox Portal

1. **Login to Authorize.Net Sandbox:**
   - Go to: https://sandbox.authorize.net/
   - Login with your sandbox account credentials (the same credentials you used in `.env`)

2. **Navigate to Transaction Search:**
   - Go to: **Reports** → **Transaction Search**
   - Or: **Account** → **Transaction Search**
   - Or: **Unsettled Transactions** (for recent transactions)

3. **View All Transactions:**
   - Set date range to include today's date
   - Click **Search** or **Submit**
   - You should see all transactions created through your API

4. **Identify Transaction Types:**
   - Look for the `authorize_net_transaction_id` values from your database
   - Transaction types will show as:
     - **Auth Capture** = PURCHASE transactions
     - **Auth Only** = AUTHORIZE transactions
     - **Prior Auth Capture** = CAPTURE transactions
     - **Void** = VOID transactions
     - **Refund** = REFUND transactions

5. **Transaction Details:**
   - Click on each transaction to see full details
   - You should see:
     - Transaction ID (matches `authorize_net_transaction_id` in your DB)
     - Transaction Type
     - Amount
     - Status (Approved, Declined, etc.)
     - Card details (last 4 digits, card type)
     - Date/Time
     - Response Code

6. **Take Screenshots:**
   - **Screenshot 1**: Transaction list showing all transaction types
   - **Screenshot 2**: Detailed view of at least one transaction showing the transaction ID

**Important**: Make sure the transaction IDs in Authorize.Net match the `authorize_net_transaction_id` values in your database!

---

## Summary of Transaction Types Created

You should have created:
1. ✅ **PURCHASE** - Direct purchase (authorize + capture)
2. ✅ **AUTHORIZE** - Authorization only (hold funds)
3. ✅ **CAPTURE** - Capture from authorization
4. ✅ **VOID** - Cancel/void an authorization
5. ✅ **REFUND** - Refund a purchase

---

## Quick PowerShell Script

Save this as `create-transactions.ps1`:

```powershell
# Step 1: Register and Login
$registerResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" `
    -Method POST `
    -ContentType "application/json" `
    -Body '{"email":"test@example.com","password":"password123","role":"USER"}'

$loginResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" `
    -Method POST `
    -ContentType "application/json" `
    -Body '{"email":"test@example.com","password":"password123"}'

$token = $loginResponse.accessToken
Write-Host "Token: $token"

# Step 2: Create PURCHASE
$purchaseResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/payments/purchase" `
    -Method POST `
    -Headers @{"Authorization"="Bearer $token"; "X-Idempotency-Key"="purchase-001"} `
    -ContentType "application/json" `
    -Body '{"orderId":1,"amount":100.00,"currency":"USD","paymentMethod":{"cardNumber":"4111111111111111","expiryMonth":"12","expiryYear":"2025","cvv":"123","cardholderName":"John Doe"},"description":"Purchase"}'

Write-Host "Purchase Transaction ID: $($purchaseResponse.transactionId)"
$purchaseTxId = $purchaseResponse.transactionId

# Step 3: Create AUTHORIZE
$authResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/payments/authorize" `
    -Method POST `
    -Headers @{"Authorization"="Bearer $token"; "X-Idempotency-Key"="authorize-001"} `
    -ContentType "application/json" `
    -Body '{"orderId":2,"amount":150.00,"currency":"USD","paymentMethod":{"cardNumber":"4111111111111111","expiryMonth":"12","expiryYear":"2025","cvv":"123","cardholderName":"Jane Smith"},"description":"Authorize"}'

Write-Host "Authorize Transaction ID: $($authResponse.transactionId)"
$authTxId = $authResponse.transactionId

# Step 4: Create CAPTURE
$captureResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/payments/capture" `
    -Method POST `
    -Headers @{"Authorization"="Bearer $token"; "X-Idempotency-Key"="capture-001"} `
    -ContentType "application/json" `
    -Body "{`"authorizationTransactionId`":$authTxId,`"amount`":150.00,`"currency`":`"USD`"}"

Write-Host "Capture Transaction ID: $($captureResponse.transactionId)"

# Step 5: Create AUTHORIZE for VOID
$auth2Response = Invoke-RestMethod -Uri "http://localhost:8080/api/payments/authorize" `
    -Method POST `
    -Headers @{"Authorization"="Bearer $token"; "X-Idempotency-Key"="authorize-002"} `
    -ContentType "application/json" `
    -Body '{"orderId":3,"amount":200.00,"currency":"USD","paymentMethod":{"cardNumber":"4111111111111111","expiryMonth":"12","expiryYear":"2025","cvv":"123","cardholderName":"Bob Johnson"},"description":"Authorize for void"}'

Write-Host "Authorize 2 Transaction ID: $($auth2Response.transactionId)"
$auth2TxId = $auth2Response.transactionId

# Step 6: Create VOID
$voidResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/payments/cancel" `
    -Method POST `
    -Headers @{"Authorization"="Bearer $token"} `
    -ContentType "application/json" `
    -Body "{`"authorizationTransactionId`":$auth2TxId}"

Write-Host "Void Transaction ID: $($voidResponse.transactionId)"

# Step 7: Create REFUND
$refundResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/payments/refund" `
    -Method POST `
    -Headers @{"Authorization"="Bearer $token"; "X-Idempotency-Key"="refund-001"} `
    -ContentType "application/json" `
    -Body "{`"transactionId`":$purchaseTxId,`"amount`":100.00,`"currency`":`"USD`",`"reason`":`"Refund`"}"

Write-Host "Refund Transaction ID: $($refundResponse.transactionId)"

Write-Host "`nAll transactions created! Check database and Authorize.Net portal."
```

Run it with:
```powershell
.\create-transactions.ps1
```

