# Payment Processing Backend

A production-ready payment processing backend built with Spring Boot that integrates with Authorize.Net for payment processing. This service provides RESTful APIs for handling payment transactions including purchases, authorizations, captures, voids, and refunds.

## Project Overview
This payment processing backend implements a comprehensive payment system with the following capabilities:

- **Payment Operations**: Purchase, Authorize, Capture, Void (Cancel), and Refund transactions
- **Security**: JWT-based authentication and authorization
- **Payment Gateway Integration**: Authorize.Net sandbox integration with a clean abstraction layer
- **Idempotency**: Prevents duplicate transactions using idempotency keys
- **Order Management**: Tracks order status and payment transactions
- **Partial Operations**: Supports partial captures and refunds
- **Comprehensive Error Handling**: Structured error responses for all failure scenarios

## Tech Stack
- **Framework**: Spring Boot 3.2.0
- **Language**: Java 17
- **Build Tool**: Maven
- **Database**: PostgreSQL 15
- **Security**: Spring Security with JWT (JJWT 0.12.3)
- **Payment Gateway**: Authorize.Net Java SDK 2.0.4
- **API Documentation**: SpringDoc OpenAPI (Swagger)
- **Containerization**: Docker & Docker Compose
- **Additional Libraries**:
  - Lombok (reduces boilerplate code)
  - Spring Data JPA (data persistence)
  - Bean Validation (request validation)

## Prerequisites
- Docker and Docker Compose installed
- Authorize.Net sandbox account credentials
- (Optional) Maven 3.8+ and JDK 17+ for local development

## Quick Start with Docker Compose

### 1. Clone the Repository
```bash
git clone <repository-url>
cd assignment\ 3
```

### 2. Configure Environment Variables
Create a `.env` file in the project root (or export environment variables):

```bash
# Authorize.Net Sandbox Credentials
AUTHORIZE_NET_API_LOGIN_ID=your_api_login_id
AUTHORIZE_NET_TRANSACTION_KEY=your_transaction_key

# Database Configuration (optional, defaults provided)
DB_PASSWORD=postgres

# JWT Secret (optional, but recommended for production)
JWT_SECRET=your-256-bit-secret-key-change-in-production-minimum-32-characters
```

### 3. Start the Services
```bash
docker-compose up -d
```

This will start:
- PostgreSQL database on port `5432`
- Spring Boot application on port `8080`

### 4. Verify the Services
Check if the services are running:

```bash
docker-compose ps
```

View application logs:

```bash
docker-compose logs -f app
```

### 5. Access the Application
- **API Base URL**: `http://localhost:8080/api`
- **Swagger UI**: `http://localhost:8080/api/swagger-ui.html`
- **API Docs**: `http://localhost:8080/api/api-docs`

### 6. Stop the Services
```bash
docker-compose down
```

To remove volumes (database data):

```bash
docker-compose down -v
```

## Authorize.Net Sandbox Configuration

### Getting Sandbox Credentials
1. **Create an Account**: Sign up for a free Authorize.Net sandbox account at [developer.authorize.net](https://developer.authorize.net/)

2. **Get API Credentials**:
   - Log in to the Authorize.Net Merchant Interface
   - Navigate to **Account** → **Settings** → **Security Settings** → **API Credentials & Keys**
   - Create a new Transaction Key if you don't have one
   - Copy your **API Login ID** and **Transaction Key**

### Configuration Options
#### Option 1: Environment Variables (Recommended)

Set environment variables before running Docker Compose:

```bash
export AUTHORIZE_NET_API_LOGIN_ID=your_api_login_id
export AUTHORIZE_NET_TRANSACTION_KEY=your_transaction_key
docker-compose up -d
```

Or use a `.env` file (see Quick Start section).

#### Option 2: Docker Compose Environment

Edit `docker-compose.yml` and add credentials directly:

```yaml
environment:
  AUTHORIZE_NET_API_LOGIN_ID: your_api_login_id
  AUTHORIZE_NET_TRANSACTION_KEY: your_transaction_key
```

#### Option 3: Application Configuration

Edit `src/main/resources/application.yml`:

```yaml
authorize-net:
  api-login-id: your_api_login_id
  transaction-key: your_transaction_key
  environment: sandbox
```

### Test Card Numbers
Authorize.Net sandbox provides test card numbers for testing:

| Card Number | Card Type | Description |
|------------|-----------|-------------|
| 4111111111111111 | Visa | Approved transaction |
| 4000000000000002 | Visa | Declined transaction |
| 5424000000000015 | MasterCard | Approved transaction |
| 370000000000002 | American Express | Approved transaction |

**Expiry Date**: Any future date (e.g., 12/2025)  
**CVV**: Any 3-4 digit number (e.g., 123)  
**Billing Address**: Any valid address

## Example API Flows

### 1. Authentication Flow
#### Register a New User

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "SecurePassword123"
  }'
```

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "user@example.com",
  "role": "USER"
}
```

#### Login

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "SecurePassword123"
  }'
```

#### Refresh Token

```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "your_refresh_token_here"
  }'
```

### 2. Payment Flow: Purchase (Authorize + Capture in one step)
```bash
curl -X POST http://localhost:8080/api/payments/purchase \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "X-Idempotency-Key: unique-key-12345" \
  -d '{
    "orderId": 1,
    "amount": 100.00,
    "currency": "USD",
    "paymentMethod": {
      "cardNumber": "4111111111111111",
      "expiryMonth": "12",
      "expiryYear": "2025",
      "cvv": "123",
      "cardholderName": "John Doe"
    },
    "description": "Purchase order #12345"
  }'
```

**Response:**
```json
{
  "transactionId": 1,
  "orderId": 1,
  "transactionType": "PURCHASE",
  "status": "SUCCESS",
  "amount": 100.00,
  "currency": "USD",
  "gatewayTransactionId": "60000000001",
  "authorizationCode": "ABC123",
  "lastFourDigits": "1111",
  "cardBrand": "Visa",
  "createdAt": "2024-01-15T10:30:00",
  "order": {
    "id": 1,
    "orderNumber": "ORD-001",
    "status": "COMPLETED",
    "totalAmount": 100.00
  }
}
```

### 3. Payment Flow: Authorize Only
```bash
curl -X POST http://localhost:8080/api/payments/authorize \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "X-Idempotency-Key: unique-key-67890" \
  -d '{
    "orderId": 2,
    "amount": 150.00,
    "currency": "USD",
    "paymentMethod": {
      "cardNumber": "4111111111111111",
      "expiryMonth": "12",
      "expiryYear": "2025",
      "cvv": "123",
      "cardholderName": "John Doe"
    },
    "description": "Authorization for order #12346"
  }'
```

**Response:**
```json
{
  "transactionId": 2,
  "orderId": 2,
  "transactionType": "AUTHORIZE",
  "status": "SUCCESS",
  "amount": 150.00,
  "currency": "USD",
  "gatewayTransactionId": "60000000002",
  "authorizationCode": "XYZ789",
  "lastFourDigits": "1111",
  "cardBrand": "Visa",
  "createdAt": "2024-01-15T10:35:00",
  "order": {
    "id": 2,
    "orderNumber": "ORD-002",
    "status": "PROCESSING",
    "totalAmount": 150.00
  }
}
```

### 4. Payment Flow: Capture (After Authorization)
```bash
curl -X POST http://localhost:8080/api/payments/capture \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "X-Idempotency-Key: unique-key-capture-001" \
  -d '{
    "authorizationTransactionId": 2,
    "amount": 150.00,
    "currency": "USD"
  }'
```

**Response:**
```json
{
  "transactionId": 3,
  "orderId": 2,
  "transactionType": "CAPTURE",
  "status": "SUCCESS",
  "amount": 150.00,
  "currency": "USD",
  "gatewayTransactionId": "60000000003",
  "createdAt": "2024-01-15T10:40:00",
  "order": {
    "id": 2,
    "orderNumber": "ORD-002",
    "status": "COMPLETED",
    "totalAmount": 150.00
  }
}
```

### 5. Payment Flow: Cancel/Void (Before Capture)
```bash
curl -X POST http://localhost:8080/api/payments/cancel \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "X-Idempotency-Key: unique-key-cancel-001" \
  -d '{
    "authorizationTransactionId": 2
  }'
```

**Response:**
```json
{
  "transactionId": 4,
  "orderId": 2,
  "transactionType": "VOID",
  "status": "SUCCESS",
  "amount": 0.00,
  "currency": "USD",
  "gatewayTransactionId": "60000000004",
  "createdAt": "2024-01-15T10:45:00",
  "order": {
    "id": 2,
    "orderNumber": "ORD-002",
    "status": "CANCELLED",
    "totalAmount": 150.00
  }
}
```

### 6. Payment Flow: Refund (Full Refund)
```bash
curl -X POST http://localhost:8080/api/payments/refund \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "X-Idempotency-Key: unique-key-refund-001" \
  -d '{
    "transactionId": 1,
    "amount": 100.00,
    "currency": "USD",
    "reason": "Customer requested refund"
  }'
```

**Response:**
```json
{
  "transactionId": 5,
  "orderId": 1,
  "transactionType": "REFUND",
  "status": "SUCCESS",
  "amount": 100.00,
  "currency": "USD",
  "gatewayTransactionId": "60000000005",
  "createdAt": "2024-01-15T11:00:00",
  "order": {
    "id": 1,
    "orderNumber": "ORD-001",
    "status": "REFUNDED",
    "totalAmount": 100.00
  }
}
```

### 7. Payment Flow: Partial Refund
```bash
curl -X POST http://localhost:8080/api/payments/refund \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "X-Idempotency-Key: unique-key-refund-002" \
  -d '{
    "transactionId": 1,
    "amount": 50.00,
    "currency": "USD",
    "reason": "Partial refund for damaged items"
  }'
```

**Response:**
```json
{
  "transactionId": 6,
  "orderId": 1,
  "transactionType": "REFUND",
  "status": "SUCCESS",
  "amount": 50.00,
  "currency": "USD",
  "gatewayTransactionId": "60000000006",
  "createdAt": "2024-01-15T11:05:00",
  "order": {
    "id": 1,
    "orderNumber": "ORD-001",
    "status": "PARTIALLY_REFUNDED",
    "totalAmount": 100.00
  }
}
```

## API Endpoints Summary

### Authentication Endpoints
- `POST /api/auth/register` - Register a new user
- `POST /api/auth/login` - Login and get access token
- `POST /api/auth/refresh` - Refresh access token
- `POST /api/auth/logout` - Logout (revoke refresh tokens)

### Payment Endpoints
- `POST /api/payments/purchase` - Process a purchase (authorize + capture)
- `POST /api/payments/authorize` - Authorize a payment (hold funds)
- `POST /api/payments/capture` - Capture previously authorized funds
- `POST /api/payments/cancel` - Cancel/void an authorization
- `POST /api/payments/refund` - Refund a captured or purchased transaction

All payment endpoints require:
- `Authorization: Bearer <access_token>` header
- `X-Idempotency-Key: <unique-key>` header (optional but recommended)

## Error Responses
The API returns structured error responses:

```json
{
  "timestamp": "2024-01-15T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Order not found: 999"
}
```

Common HTTP status codes:
- `200` - Success
- `201` - Created
- `400` - Bad Request (validation errors)
- `401` - Unauthorized (missing/invalid token)
- `402` - Payment Required (payment declined)
- `404` - Not Found
- `422` - Unprocessable Entity (business logic validation failed)
- `500` - Internal Server Error

## Local Development

### Prerequisites
- JDK 17+
- Maven 3.8+
- PostgreSQL 15+ (or use Docker)

### Setup
1. **Configure Database**: Update `application.yml` with your database credentials

2. **Configure Authorize.Net**: Set environment variables or update `application.yml`

3. **Run the Application**:
   ```bash
   mvn spring-boot:run
   ```

4. **Run Tests**:
   ```bash
   mvn test
   ```

5. **Build JAR**:
   ```bash
   mvn clean package
   ```

## Project Structure
```
src/
├── main/
│   ├── java/com/payment/
│   │   ├── config/          # Configuration classes
│   │   ├── controller/      # REST controllers
│   │   ├── service/         # Business logic
│   │   ├── repository/      # Data access layer
│   │   ├── entity/          # JPA entities
│   │   ├── dto/             # Data Transfer Objects
│   │   ├── gateway/         # Payment gateway integration
│   │   ├── security/        # Security configuration
│   │   └── exception/       # Exception handling
│   └── resources/
│       └── application.yml  # Application configuration
└── test/                    # Unit and integration tests
```

## Security Considerations
- **JWT Tokens**: Access tokens expire in 15 minutes (configurable)
- **Refresh Tokens**: Valid for 7 days (configurable)
- **Password Encryption**: BCrypt hashing
- **HTTPS**: Use HTTPS in production
- **Environment Variables**: Never commit credentials to version control
- **JWT Secret**: Use a strong, randomly generated secret in production (minimum 32 characters)

## Database Schema
The application uses the following main tables:

- `users` - User accounts
- `orders` - Order information
- `payment_transactions` - Payment transaction records
- `refresh_tokens` - Refresh token storage

See `ARCHITECTURE.md` for detailed database schema documentation.

## Contributing
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass
6. Submit a pull request

## License
[Specify your license here]

## Support
For issues and questions, please open an issue in the repository.
