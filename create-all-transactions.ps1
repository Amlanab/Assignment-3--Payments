# Script to create all transaction types for screenshot assignment
# This will create: PURCHASE, AUTHORIZE, CAPTURE, VOID, REFUND

Write-Host "=== Creating All Transaction Types ===" -ForegroundColor Green

# Step 1: Register and Login
Write-Host "`n1. Registering user..." -ForegroundColor Yellow
try {
    $registerResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" `
        -Method POST `
        -ContentType "application/json" `
        -Body '{"email":"screenshot@example.com","password":"password123","role":"USER"}'
    Write-Host "   User registered successfully" -ForegroundColor Green
} catch {
    Write-Host "   User may already exist, continuing..." -ForegroundColor Yellow
}

Write-Host "`n2. Logging in..." -ForegroundColor Yellow
$loginResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" `
    -Method POST `
    -ContentType "application/json" `
    -Body '{"email":"screenshot@example.com","password":"password123"}'

$token = $loginResponse.accessToken
$userId = $loginResponse.userId
Write-Host "   Login successful! User ID: $userId" -ForegroundColor Green

# Step 2: Create Orders in Database
Write-Host "`n3. Creating orders in database..." -ForegroundColor Yellow
$createOrdersSQL = @"
INSERT INTO orders (user_id, order_number, status, total_amount, currency, description, created_at, updated_at)
VALUES 
    ($userId, 'ORD-001', 'PENDING', 100.00, 'USD', 'Order for Purchase', NOW(), NOW()),
    ($userId, 'ORD-002', 'PENDING', 150.00, 'USD', 'Order for Authorize', NOW(), NOW()),
    ($userId, 'ORD-003', 'PENDING', 200.00, 'USD', 'Order for Void', NOW(), NOW())
ON CONFLICT (order_number) DO NOTHING;
"@

docker exec payment-db psql -U postgres -d paymentdb -c $createOrdersSQL | Out-Null

# Get order IDs
$orderIds = docker exec payment-db psql -U postgres -d paymentdb -t -c "SELECT id FROM orders WHERE user_id = $userId ORDER BY id;"
$orderId1 = ($orderIds[0] -split '\s+')[0]
$orderId2 = ($orderIds[1] -split '\s+')[0]
$orderId3 = ($orderIds[2] -split '\s+')[0]

Write-Host "   Orders created: $orderId1, $orderId2, $orderId3" -ForegroundColor Green

# Step 3: Create PURCHASE
Write-Host "`n4. Creating PURCHASE transaction..." -ForegroundColor Yellow
$purchaseBody = @{
    orderId = [int]$orderId1
    amount = 100.00
    currency = "USD"
    paymentMethod = @{
        cardNumber = "4111111111111111"
        expiryMonth = "12"
        expiryYear = "2025"
        cvv = "123"
        cardholderName = "John Doe"
    }
    description = "Purchase transaction for screenshot"
} | ConvertTo-Json

$purchaseResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/payments/purchase" `
    -Method POST `
    -Headers @{"Authorization"="Bearer $token"; "X-Idempotency-Key"="purchase-001"} `
    -ContentType "application/json" `
    -Body $purchaseBody

Write-Host "   PURCHASE created! Transaction ID: $($purchaseResponse.transactionId), Gateway ID: $($purchaseResponse.gatewayTransactionId)" -ForegroundColor Green
$purchaseTxId = $purchaseResponse.transactionId

# Step 4: Create AUTHORIZE
Write-Host "`n5. Creating AUTHORIZE transaction..." -ForegroundColor Yellow
$authBody = @{
    orderId = [int]$orderId2
    amount = 150.00
    currency = "USD"
    paymentMethod = @{
        cardNumber = "4111111111111111"
        expiryMonth = "12"
        expiryYear = "2025"
        cvv = "123"
        cardholderName = "Jane Smith"
    }
    description = "Authorization transaction for screenshot"
} | ConvertTo-Json

$authResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/payments/authorize" `
    -Method POST `
    -Headers @{"Authorization"="Bearer $token"; "X-Idempotency-Key"="authorize-001"} `
    -ContentType "application/json" `
    -Body $authBody

Write-Host "   AUTHORIZE created! Transaction ID: $($authResponse.transactionId), Gateway ID: $($authResponse.gatewayTransactionId)" -ForegroundColor Green
$authTxId = $authResponse.transactionId

# Step 5: Create CAPTURE
Write-Host "`n6. Creating CAPTURE transaction..." -ForegroundColor Yellow
$captureBody = @{
    authorizationTransactionId = [int]$authTxId
    amount = 150.00
    currency = "USD"
} | ConvertTo-Json

$captureResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/payments/capture" `
    -Method POST `
    -Headers @{"Authorization"="Bearer $token"; "X-Idempotency-Key"="capture-001"} `
    -ContentType "application/json" `
    -Body $captureBody

Write-Host "   CAPTURE created! Transaction ID: $($captureResponse.transactionId), Gateway ID: $($captureResponse.gatewayTransactionId)" -ForegroundColor Green

# Step 6: Create AUTHORIZE for VOID
Write-Host "`n7. Creating AUTHORIZE transaction (for VOID)..." -ForegroundColor Yellow
$auth2Body = @{
    orderId = [int]$orderId3
    amount = 200.00
    currency = "USD"
    paymentMethod = @{
        cardNumber = "4111111111111111"
        expiryMonth = "12"
        expiryYear = "2025"
        cvv = "123"
        cardholderName = "Bob Johnson"
    }
    description = "Authorization for void transaction"
} | ConvertTo-Json

$auth2Response = Invoke-RestMethod -Uri "http://localhost:8080/api/payments/authorize" `
    -Method POST `
    -Headers @{"Authorization"="Bearer $token"; "X-Idempotency-Key"="authorize-002"} `
    -ContentType "application/json" `
    -Body $auth2Body

Write-Host "   AUTHORIZE 2 created! Transaction ID: $($auth2Response.transactionId), Gateway ID: $($auth2Response.gatewayTransactionId)" -ForegroundColor Green
$auth2TxId = $auth2Response.transactionId

# Step 7: Create VOID
Write-Host "`n8. Creating VOID transaction..." -ForegroundColor Yellow
$voidBody = @{
    authorizationTransactionId = [int]$auth2TxId
} | ConvertTo-Json

$voidResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/payments/cancel" `
    -Method POST `
    -Headers @{"Authorization"="Bearer $token"} `
    -ContentType "application/json" `
    -Body $voidBody

Write-Host "   VOID created! Transaction ID: $($voidResponse.transactionId), Gateway ID: $($voidResponse.gatewayTransactionId)" -ForegroundColor Green

# Step 8: Create REFUND
Write-Host "`n9. Creating REFUND transaction..." -ForegroundColor Yellow
$refundBody = @{
    transactionId = [int]$purchaseTxId
    amount = 100.00
    currency = "USD"
    reason = "Refund transaction for screenshot"
} | ConvertTo-Json

$refundResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/payments/refund" `
    -Method POST `
    -Headers @{"Authorization"="Bearer $token"; "X-Idempotency-Key"="refund-001"} `
    -ContentType "application/json" `
    -Body $refundBody

Write-Host "   REFUND created! Transaction ID: $($refundResponse.transactionId), Gateway ID: $($refundResponse.gatewayTransactionId)" -ForegroundColor Green

# Summary
Write-Host "`n=== Summary ===" -ForegroundColor Green
Write-Host "All transaction types created successfully!" -ForegroundColor Green
Write-Host "`nTransaction Types Created:" -ForegroundColor Cyan
Write-Host "  1. PURCHASE   - Transaction ID: $purchaseTxId" -ForegroundColor White
Write-Host "  2. AUTHORIZE  - Transaction ID: $authTxId" -ForegroundColor White
Write-Host "  3. CAPTURE    - Transaction ID: $($captureResponse.transactionId)" -ForegroundColor White
Write-Host "  4. VOID       - Transaction ID: $($voidResponse.transactionId)" -ForegroundColor White
Write-Host "  5. REFUND     - Transaction ID: $($refundResponse.transactionId)" -ForegroundColor White

Write-Host "`nNext Steps:" -ForegroundColor Yellow
Write-Host "  1. Run this SQL query in the database to view all transactions:" -ForegroundColor White
Write-Host "     docker exec -it payment-db psql -U postgres -d paymentdb" -ForegroundColor Gray
Write-Host "     SELECT id, transaction_type, status, amount, authorize_net_transaction_id, created_at FROM payment_transactions ORDER BY created_at;" -ForegroundColor Gray
Write-Host "`n  2. Login to Authorize.Net Sandbox portal:" -ForegroundColor White
Write-Host "     https://sandbox.authorize.net/" -ForegroundColor Gray
Write-Host "     Go to Reports > Transaction Search" -ForegroundColor Gray
Write-Host "     Look for transactions matching the authorize_net_transaction_id values" -ForegroundColor Gray

Write-Host "`nDone! Check SCREENSHOT_GUIDE.md for detailed instructions." -ForegroundColor Green

