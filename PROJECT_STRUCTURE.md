# Project Structure

This document describes the organization and structure of the Payment Processing Backend project. The project follows a layered architecture pattern with clear separation of concerns.

## Directory Structure

```
payment-processing-backend/
│
├── src/
│   ├── main/
│   │   ├── java/com/payment/
│   │   │   ├── PaymentApplication.java          # Main Spring Boot application class
│   │   │   │
│   │   │   ├── config/                          # Configuration classes
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   ├── JwtConfig.java
│   │   │   │   ├── AuthorizeNetConfig.java
│   │   │   │   └── JpaAuditingConfig.java
│   │   │   │
│   │   │   ├── controller/                      # REST API controllers (Presentation Layer)
│   │   │   │   ├── AuthController.java
│   │   │   │   └── PaymentController.java
│   │   │   │
│   │   │   ├── service/                         # Business logic layer
│   │   │   │   ├── AuthService.java
│   │   │   │   ├── PaymentService.java
│   │   │   │   └── JwtService.java
│   │   │   │
│   │   │   ├── repository/                      # Data access layer (Spring Data JPA)
│   │   │   │   ├── UserRepository.java
│   │   │   │   ├── OrderRepository.java
│   │   │   │   ├── PaymentTransactionRepository.java
│   │   │   │   └── RefreshTokenRepository.java
│   │   │   │
│   │   │   ├── entity/                          # JPA entities (Domain models)
│   │   │   │   ├── User.java
│   │   │   │   ├── Order.java
│   │   │   │   ├── PaymentTransaction.java
│   │   │   │   └── RefreshToken.java
│   │   │   │
│   │   │   ├── dto/                             # Data Transfer Objects
│   │   │   │   ├── request/                     # Request DTOs
│   │   │   │   │   ├── RegisterRequest.java
│   │   │   │   │   ├── LoginRequest.java
│   │   │   │   │   ├── RefreshTokenRequest.java
│   │   │   │   │   ├── PaymentRequest.java
│   │   │   │   │   ├── PaymentMethodRequest.java
│   │   │   │   │   ├── CaptureRequest.java
│   │   │   │   │   ├── CancelRequest.java
│   │   │   │   │   └── RefundRequest.java
│   │   │   │   └── response/                    # Response DTOs
│   │   │   │       ├── AuthResponse.java
│   │   │   │       ├── PaymentResponse.java
│   │   │   │       └── OrderSummary.java
│   │   │   │
│   │   │   ├── gateway/                         # Payment gateway integration layer
│   │   │   │   ├── PaymentGateway.java          # Gateway interface
│   │   │   │   ├── AuthorizeNetGateway.java     # Authorize.Net implementation
│   │   │   │   └── dto/                         # Gateway-specific DTOs
│   │   │   │       ├── PurchaseRequest.java
│   │   │   │       ├── PurchaseResponse.java
│   │   │   │       ├── AuthorizeRequest.java
│   │   │   │       ├── AuthorizeResponse.java
│   │   │   │       ├── CaptureRequest.java
│   │   │   │       ├── CaptureResponse.java
│   │   │   │       ├── VoidRequest.java
│   │   │   │       ├── VoidResponse.java
│   │   │   │       ├── RefundRequest.java
│   │   │   │       ├── RefundResponse.java
│   │   │   │       └── CreditCardInfo.java
│   │   │   │
│   │   │   ├── security/                        # Security components
│   │   │   │   ├── JwtAuthenticationFilter.java
│   │   │   │   ├── SecurityUser.java
│   │   │   │   └── UserDetailsServiceImpl.java
│   │   │   │
│   │   │   ├── exception/                       # Exception handling
│   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   ├── ErrorResponse.java
│   │   │   │   ├── PaymentException.java
│   │   │   │   ├── PaymentNotFoundException.java
│   │   │   │   └── PaymentValidationException.java
│   │   │   │
│   │   │   └── enums/                           # Enumeration types
│   │   │       ├── UserRole.java
│   │   │       ├── OrderStatus.java
│   │   │       ├── TransactionType.java
│   │   │       └── TransactionStatus.java
│   │   │
│   │   └── resources/
│   │       ├── application.yml                  # Main application configuration
│   │       ├── application-dev.yml              # Development profile
│   │       └── application-prod.yml             # Production profile
│   │
│   └── test/
│       └── java/com/payment/
│           ├── service/                         # Service layer unit tests
│           │   └── PaymentServiceTest.java
│           ├── controller/                      # Controller tests (to be added)
│           ├── gateway/                         # Gateway integration tests (to be added)
│           ├── repository/                      # Repository tests (to be added)
│           └── integration/                     # Integration tests (to be added)
│
├── pom.xml                                      # Maven project configuration
├── Dockerfile                                   # Docker image definition
├── docker-compose.yml                           # Docker Compose configuration
├── .dockerignore                                # Docker ignore patterns
├── .gitignore                                   # Git ignore patterns
├── README.md                                    # Project documentation
├── ARCHITECTURE.md                              # Architecture documentation
└── PROJECT_STRUCTURE.md                         # This file

```

## Layer-by-Layer Explanation

### 1. Root Level Files

#### `pom.xml`
Maven Project Object Model file that defines:
- Project metadata (groupId, artifactId, version)
- Dependencies (Spring Boot, PostgreSQL, JWT, Authorize.Net SDK, etc.)
- Build configuration and plugins
- Java version (17)

#### `Dockerfile`
Multi-stage Docker build configuration:
- Builds the application JAR using Maven
- Creates a lightweight runtime image with OpenJDK 17
- Exposes port 8080
- Sets the application entry point

#### `docker-compose.yml`
Orchestrates multi-container Docker application:
- Defines `db` service (PostgreSQL database)
- Defines `app` service (Spring Boot application)
- Configures networking, volumes, and environment variables
- Sets up service dependencies and health checks

#### Configuration Files
- `.dockerignore`: Specifies files to exclude from Docker builds
- `.gitignore`: Specifies files to exclude from version control
- `README.md`: Project documentation and setup instructions
- `ARCHITECTURE.md`: Detailed architecture and design decisions

---

### 2. Application Entry Point

#### `PaymentApplication.java`
Main Spring Boot application class:
- Annotated with `@SpringBootApplication`
- Contains the `main()` method that starts the application
- Enables auto-configuration and component scanning
- Located in the base package `com.payment`

---

### 3. Configuration Layer (`config/`)

**Purpose**: Centralized configuration classes for Spring beans and application settings.

#### `SecurityConfig.java`
Spring Security configuration:
- Configures `SecurityFilterChain` for HTTP security
- Sets up JWT authentication filter
- Defines public endpoints (auth, Swagger)
- Configures password encoder (BCrypt)
- Sets session management to stateless

#### `JwtConfig.java`
JWT configuration properties:
- Loads JWT settings from `application.yml`
- Provides access to JWT secret, expiration times
- Used by `JwtService` for token generation/validation

#### `AuthorizeNetConfig.java`
Authorize.Net gateway configuration:
- Loads API credentials from environment variables
- Provides configuration for sandbox/production environments
- Used by `AuthorizeNetGateway` for API initialization

#### `JpaAuditingConfig.java`
JPA Auditing configuration:
- Enables `@CreatedDate` and `@LastModifiedDate` annotations
- Automatically populates audit fields on entities

**Best Practices**:
- Keep configuration classes focused and single-purpose
- Use `@ConfigurationProperties` for type-safe configuration
- Leverage environment variables for sensitive data

---

### 4. Controller Layer (`controller/`)

**Purpose**: REST API endpoints - handles HTTP requests/responses, validation, and delegates to service layer.

#### `AuthController.java`
Authentication endpoints:
- `POST /auth/register` - User registration
- `POST /auth/login` - User login (returns JWT tokens)
- `POST /auth/refresh` - Refresh access token
- `POST /auth/logout` - Logout (revokes refresh tokens)

**Responsibilities**:
- Request validation (`@Valid`)
- Authentication principal extraction (`@AuthenticationPrincipal`)
- HTTP status code management
- Response entity creation

#### `PaymentController.java`
Payment operation endpoints:
- `POST /payments/purchase` - Process purchase (authorize + capture)
- `POST /payments/authorize` - Authorize payment (hold funds)
- `POST /payments/capture` - Capture authorized funds
- `POST /payments/cancel` - Cancel/void authorization
- `POST /payments/refund` - Refund transaction

**Responsibilities**:
- Request body validation
- JWT authentication enforcement
- Delegates business logic to `PaymentService`
- Returns appropriate HTTP responses

**Best Practices**:
- Keep controllers thin - no business logic
- Use DTOs for request/response (never expose entities)
- Leverage Spring validation annotations
- Return appropriate HTTP status codes
- Document APIs with Swagger annotations (optional)

---

### 5. Service Layer (`service/`)

**Purpose**: Business logic implementation - orchestrates operations across repositories and gateways.

#### `AuthService.java`
Authentication business logic:
- User registration with password hashing
- User authentication via Spring Security
- JWT token generation (access + refresh tokens)
- Refresh token management (creation, validation, revocation)
- User session management

**Key Operations**:
- `register()` - Creates new user, generates tokens
- `login()` - Authenticates user, issues new tokens
- `refreshToken()` - Validates refresh token, issues new access token
- `logout()` - Revokes all user refresh tokens

#### `PaymentService.java`
Payment processing business logic:
- Purchase flow (authorize + capture in one step)
- Authorize-only flow (hold funds)
- Capture flow (capture authorized funds, supports partial)
- Cancel/void flow (cancel authorization before capture)
- Refund flow (full and partial refunds)

**Key Responsibilities**:
- Transaction validation (order status, amounts, ownership)
- Idempotency handling
- Gateway communication orchestration
- Transaction and order status management
- Partial operation tracking (captures, refunds)
- Error handling and exception translation

**Transaction Management**:
- Methods annotated with `@Transactional`
- Ensures data consistency across repository operations
- Handles rollback on exceptions

#### `JwtService.java`
JWT token operations:
- Token generation (access and refresh tokens)
- Token validation
- Claims extraction (email, userId, role)
- Expiration checking

**Key Operations**:
- `generateAccessToken()` - Creates access token with user claims
- `generateRefreshToken()` - Creates refresh token
- `validateToken()` - Validates token signature and expiration
- `extractEmail()`, `extractUserId()`, `extractRole()` - Claim extraction

**Best Practices**:
- Services contain business logic, not data access details
- Use `@Transactional` for operations requiring database transactions
- Throw domain-specific exceptions (not generic ones)
- Keep services testable (dependency injection)
- Log important operations

---

### 6. Repository Layer (`repository/`)

**Purpose**: Data access abstraction using Spring Data JPA - provides database operations.

#### `UserRepository.java`
User data access:
- Extends `JpaRepository<User, Long>`
- Custom queries: `findByEmail()`, `existsByEmail()`
- Provides CRUD operations automatically

#### `OrderRepository.java`
Order data access:
- Extends `JpaRepository<Order, Long>`
- Custom query: `findByOrderNumber()`
- Handles order persistence and retrieval

#### `PaymentTransactionRepository.java`
Payment transaction data access:
- Extends `JpaRepository<PaymentTransaction, Long>`
- Custom queries:
  - `findByAuthorizeNetTransactionId()` - Find by gateway transaction ID
  - `findByIdempotencyKey()` - Find by idempotency key (for duplicate prevention)

#### `RefreshTokenRepository.java`
Refresh token data access:
- Extends `JpaRepository<RefreshToken, Long>`
- Custom queries:
  - `findByToken()` - Find token by value
  - `revokeAllUserTokens()` - Bulk update to revoke all user tokens

**Best Practices**:
- Extend `JpaRepository` for basic CRUD operations
- Use method naming conventions for simple queries
- Use `@Query` for complex queries
- Return `Optional<>` for single-result queries
- Use `@Modifying` and `@Transactional` for update/delete queries

---

### 7. Entity Layer (`entity/`)

**Purpose**: JPA entities representing database tables - domain models.

#### `User.java`
User entity:
- Fields: `id`, `email`, `passwordHash`, `role`, `enabled`, `createdAt`
- Relationships: One-to-many with `Order`, `RefreshToken`
- Constraints: Unique email, non-null fields

#### `Order.java`
Order entity:
- Fields: `id`, `user`, `orderNumber`, `status`, `totalAmount`, `currency`, `description`, `createdAt`, `updatedAt`
- Relationships: Many-to-one with `User`, One-to-many with `PaymentTransaction`
- Status tracking via `OrderStatus` enum

#### `PaymentTransaction.java`
Payment transaction entity:
- Fields: `id`, `order`, `parentTransaction`, `transactionType`, `status`, `authorizeNetTransactionId`, `amount`, `currency`, `authorizedAmount`, `capturedAmount`, `refundedAmount`, `paymentMethodEncrypted`, `lastFourDigits`, `cardBrand`, `cardExpiryMonth`, `cardExpiryYear`, `idempotencyKey`, `authorizationExpiresAt`, `errorCode`, `errorMessage`, `gatewayResponse`, `createdAt`, `updatedAt`
- Relationships: Many-to-one with `Order`, Self-referential (parent-child for capture/void/refund)
- Tracks partial operations (capturedAmount, refundedAmount)

#### `RefreshToken.java`
Refresh token entity:
- Fields: `id`, `user`, `token`, `expiresAt`, `revoked`, `createdAt`
- Relationships: Many-to-one with `User`
- Token revocation support

**Best Practices**:
- Use `@Entity` and `@Table` annotations
- Use `@Id` with `@GeneratedValue` for primary keys
- Define relationships with `@OneToMany`, `@ManyToOne`
- Use `@CreatedDate` and `@LastModifiedDate` for auditing
- Use enums for status fields
- Never expose entities directly - always use DTOs
- Use `@Builder` for flexible object creation

---

### 8. DTO Layer (`dto/`)

**Purpose**: Data Transfer Objects for request/response - separates API contracts from domain models.

#### Request DTOs (`dto/request/`)

**`RegisterRequest.java`**:
- Fields: `email`, `password`
- Validation: Email format, password length (min 8)

**`LoginRequest.java`**:
- Fields: `email`, `password`
- Validation: Email format, non-blank fields

**`PaymentRequest.java`**:
- Fields: `orderId`, `amount`, `currency`, `paymentMethod`, `description`, `idempotencyKey`
- Validation: Non-null amounts, currency format, valid payment method

**`PaymentMethodRequest.java`**:
- Fields: `cardNumber`, `expiryMonth`, `expiryYear`, `cvv`, `cardholderName`, `billingAddress`
- Validation: Card number format, expiry validation, CVV length

**`CaptureRequest.java`**:
- Fields: `authorizationTransactionId`, `amount`, `currency`, `idempotencyKey`
- Validation: Non-null transaction ID, positive amount

**`CancelRequest.java`**:
- Fields: `authorizationTransactionId`, `idempotencyKey`
- Validation: Non-null transaction ID

**`RefundRequest.java`**:
- Fields: `transactionId`, `amount`, `currency`, `reason`, `idempotencyKey`
- Validation: Non-null transaction ID, positive amount

#### Response DTOs (`dto/response/`)

**`AuthResponse.java`**:
- Fields: `accessToken`, `refreshToken`, `tokenType`, `userId`, `email`, `role`
- Used for authentication endpoints

**`PaymentResponse.java`**:
- Fields: `transactionId`, `orderId`, `transactionType`, `status`, `amount`, `currency`, `gatewayTransactionId`, `authorizationCode`, `lastFourDigits`, `cardBrand`, `createdAt`, `order`
- Includes nested `OrderSummary` for order status

**`OrderSummary.java`**:
- Fields: `id`, `orderNumber`, `status`, `totalAmount`
- Lightweight order representation in payment responses

**Best Practices**:
- Use separate DTOs for requests and responses
- Include validation annotations (`@NotNull`, `@NotBlank`, `@Email`, etc.)
- Use `@JsonInclude(JsonInclude.Include.NON_NULL)` to exclude null fields
- Keep DTOs focused on API contract, not business logic
- Use builders for complex DTOs

---

### 9. Gateway Layer (`gateway/`)

**Purpose**: Payment gateway integration abstraction - isolates external payment gateway from business logic.

#### `PaymentGateway.java`
Payment gateway interface:
- Defines contract for payment operations:
  - `purchase()` - Authorize + capture
  - `authorize()` - Authorize only
  - `capture()` - Capture authorized funds
  - `voidTransaction()` - Void/cancel authorization
  - `refund()` - Refund transaction
- Allows for easy gateway swapping (Authorize.Net, Stripe, etc.)

#### `AuthorizeNetGateway.java`
Authorize.Net implementation:
- Implements `PaymentGateway` interface
- Wraps Authorize.Net Java SDK
- Handles SDK initialization (merchant authentication, environment)
- Maps internal DTOs to SDK request types
- Maps SDK responses to internal DTOs
- Handles errors and exceptions

#### Gateway DTOs (`gateway/dto/`)
Request/Response DTOs for gateway communication:
- `PurchaseRequest/Response`
- `AuthorizeRequest/Response`
- `CaptureRequest/Response`
- `VoidRequest/Response`
- `RefundRequest/Response`
- `CreditCardInfo`

**Best Practices**:
- Use interface for gateway abstraction
- Isolate SDK-specific code in implementation
- Map between internal DTOs and gateway DTOs
- Handle gateway errors gracefully
- Log gateway interactions for debugging
- Support multiple environments (sandbox, production)

---

### 10. Security Layer (`security/`)

**Purpose**: Security-related components for authentication and authorization.

#### `JwtAuthenticationFilter.java`
JWT authentication filter:
- Extends `OncePerRequestFilter`
- Intercepts HTTP requests
- Extracts JWT from `Authorization` header
- Validates JWT using `JwtService`
- Sets authentication in `SecurityContextHolder`
- Allows requests to continue to controllers

#### `SecurityUser.java`
Custom `UserDetails` implementation:
- Wraps `User` entity
- Implements Spring Security `UserDetails` interface
- Provides user information to Spring Security
- Converts user roles to `GrantedAuthority`

#### `UserDetailsServiceImpl.java`
User details service:
- Implements Spring Security `UserDetailsService`
- Loads user by email from database
- Returns `SecurityUser` for authentication
- Used by Spring Security for authentication

**Best Practices**:
- Keep security components focused and testable
- Use stateless authentication (JWT)
- Validate tokens on every request
- Handle token expiration gracefully
- Implement proper error handling for security failures

---

### 11. Exception Layer (`exception/`)

**Purpose**: Centralized exception handling and error response formatting.

#### `GlobalExceptionHandler.java`
Global exception handler:
- Annotated with `@RestControllerAdvice`
- Catches exceptions from all controllers
- Maps exceptions to HTTP status codes
- Returns structured error responses (`ErrorResponse`)

**Exception Mappings**:
- `MethodArgumentNotValidException` → 400 (Validation errors)
- `IllegalArgumentException` → 400 (Bad request)
- `BadCredentialsException` → 401 (Unauthorized)
- `PaymentNotFoundException` → 404 (Not found)
- `PaymentValidationException` → 422 (Unprocessable entity)
- `PaymentException` → 402 (Payment required/declined)
- `Exception` → 500 (Internal server error)

#### `ErrorResponse.java`
Structured error response DTO:
- Fields: `timestamp`, `status`, `error`, `message`, `path`, `errors` (for validation), `gatewayErrorCode`, `gatewayErrorMessage`, etc.
- Provides consistent error format across API

#### Custom Exceptions
- `PaymentException` - Base exception for payment-related errors
- `PaymentNotFoundException` - When payment resource not found
- `PaymentValidationException` - When payment validation fails

**Best Practices**:
- Use `@RestControllerAdvice` for global exception handling
- Create domain-specific exceptions
- Provide meaningful error messages
- Include error codes for client handling
- Log errors appropriately
- Don't expose internal implementation details

---

### 12. Enums (`enums/`)

**Purpose**: Type-safe enumeration types for status and type fields.

#### `UserRole.java`
User roles:
- `USER` - Standard user
- `ADMIN` - Administrator (for future use)

#### `OrderStatus.java`
Order statuses:
- `PENDING` - Order created, awaiting payment
- `PROCESSING` - Payment authorized, awaiting capture
- `COMPLETED` - Payment captured, order complete
- `PARTIALLY_CAPTURED` - Partially captured
- `PARTIALLY_REFUNDED` - Partially refunded
- `REFUNDED` - Fully refunded
- `CANCELLED` - Cancelled/voided
- `FAILED` - Payment failed

#### `TransactionType.java`
Transaction types:
- `PURCHASE` - Authorize + capture
- `AUTHORIZE` - Authorization only
- `CAPTURE` - Capture authorized funds
- `VOID` - Void/cancel authorization
- `REFUND` - Refund transaction

#### `TransactionStatus.java`
Transaction statuses:
- `PENDING` - Transaction created, not yet processed
- `SUCCESS` - Transaction successful
- `FAILED` - Transaction failed
- `VOIDED` - Transaction voided
- `REFUNDED` - Transaction refunded

**Best Practices**:
- Use enums for fixed sets of values
- Use `@Enumerated(EnumType.STRING)` in JPA entities
- Keep enum names descriptive and consistent
- Consider adding methods to enums for business logic (optional)

---

### 13. Resources (`resources/`)

**Purpose**: Configuration files and static resources.

#### `application.yml`
Main application configuration:
- Spring Boot configuration (datasource, JPA, server)
- Database connection settings (PostgreSQL)
- JWT configuration
- Authorize.Net configuration
- Logging configuration
- Swagger/OpenAPI configuration

**Configuration Sources** (in order of precedence):
1. Environment variables
2. Profile-specific files (`application-{profile}.yml`)
3. `application.yml`
4. Default values

#### `application-dev.yml`
Development profile overrides:
- Can override settings for development environment
- Currently empty (for future use)

#### `application-prod.yml`
Production profile overrides:
- Can override settings for production environment
- Currently empty (for future use)

**Best Practices**:
- Use YAML for readability
- Use environment variables for sensitive data
- Use profiles for environment-specific configuration
- Document configuration properties
- Use sensible defaults

---

### 14. Test Structure (`test/`)

**Purpose**: Comprehensive test coverage at all layers.

#### Service Tests (`test/service/`)
- `PaymentServiceTest.java` - Unit tests for payment service
  - Uses Mockito for mocking dependencies
  - Tests all payment flows (purchase, authorize, capture, cancel, refund)
  - Tests success and failure scenarios
  - Tests validation and edge cases

#### Controller Tests (`test/controller/`)
- (To be added) Tests for REST controllers
- Uses `@WebMvcTest` for focused controller testing
- Tests HTTP layer, validation, status codes

#### Gateway Tests (`test/gateway/`)
- (To be added) Integration tests for payment gateway
- Tests Authorize.Net integration
- Uses test credentials for sandbox

#### Repository Tests (`test/repository/`)
- (To be added) Tests for data access layer
- Uses `@DataJpaTest` for focused repository testing
- Tests custom queries and relationships

#### Integration Tests (`test/integration/`)
- (To be added) End-to-end integration tests
- Tests complete flows across multiple layers
- Uses `@SpringBootTest` with `@AutoConfigureMockMvc`

**Best Practices**:
- Use appropriate test annotations (`@WebMvcTest`, `@DataJpaTest`, `@SpringBootTest`)
- Mock external dependencies in unit tests
- Use test databases for integration tests
- Aim for high code coverage (60%+)
- Keep tests focused and readable
- Use test builders for test data creation

---

## Architecture Principles

### Layered Architecture
The project follows a clean layered architecture:
1. **Presentation Layer** (Controllers) - HTTP request/response handling
2. **Service Layer** (Services) - Business logic orchestration
3. **Repository Layer** (Repositories) - Data access abstraction
4. **Domain Layer** (Entities) - Domain models

### Separation of Concerns
- Each layer has a clear, single responsibility
- Dependencies flow downward (controllers → services → repositories)
- No circular dependencies
- Gateway layer is isolated from business logic

### Dependency Injection
- Uses Spring's dependency injection throughout
- Constructor injection (via Lombok `@RequiredArgsConstructor`)
- Promotes testability and loose coupling

### Configuration over Code
- Externalized configuration via `application.yml`
- Environment-specific configuration via profiles
- Sensitive data via environment variables

### Security by Design
- JWT-based stateless authentication
- Password encryption (BCrypt)
- Role-based access control (prepared for future use)
- Secure credential management

---

## Module Dependencies

```
Controllers
    ↓
Services
    ↓
Repositories ↔ Entities
    ↓
Database

Services
    ↓
Gateway Interface
    ↓
Gateway Implementation (Authorize.Net)
    ↓
Authorize.Net SDK

Controllers
    ↓
Security (JWT Filter)
    ↓
SecurityUser / UserDetailsService
    ↓
Services
```

---

## Key Design Decisions

1. **Gateway Abstraction**: `PaymentGateway` interface allows switching payment providers without changing business logic
2. **DTO Pattern**: Separate request/response DTOs prevent entity exposure and maintain API stability
3. **Idempotency Keys**: Prevents duplicate transactions via client-provided idempotency keys
4. **Partial Operations**: Tracks partial captures and refunds via amount fields (`capturedAmount`, `refundedAmount`)
5. **Transaction Management**: `@Transactional` ensures data consistency in service layer
6. **Global Exception Handling**: Centralized error handling provides consistent API error responses
7. **JWT Authentication**: Stateless authentication supports scalability
8. **Audit Fields**: Automatic tracking of creation and modification timestamps

---

## Future Enhancements

Potential areas for expansion:
- Order management service/controller
- Webhook handling for payment gateway events
- Payment method tokenization
- Multi-currency support enhancements
- Payment reconciliation jobs
- Enhanced logging and monitoring
- API rate limiting
- Caching layer (Redis)
- Message queue integration for async processing
