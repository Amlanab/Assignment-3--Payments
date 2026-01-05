# Testing Strategy

This document outlines the comprehensive testing strategy for the Payment Processing Backend, including unit testing approaches, mocking strategies, and coverage goals.

## Table of Contents

1. [Testing Philosophy](#testing-philosophy)
2. [Testing Pyramid](#testing-pyramid)
3. [Unit Testing Approach](#unit-testing-approach)
4. [Mocking Strategy](#mocking-strategy)
5. [Test Structure](#test-structure)
6. [Coverage Goals](#coverage-goals)
7. [Best Practices](#best-practices)
8. [Test Data Management](#test-data-management)

---

## Testing Philosophy

### Core Principles

1. **Test-Driven Quality**: Tests validate business logic, prevent regressions, and serve as living documentation
2. **Fast Feedback**: Unit tests provide rapid feedback on code changes
3. **Isolation**: Tests are independent and can run in any order
4. **Deterministic**: Tests produce consistent, predictable results
5. **Maintainable**: Tests are readable, well-organized, and easy to update

### Testing Layers

The testing strategy follows a multi-layered approach:

- **Unit Tests**: Fast, isolated tests for individual components (services, utilities)
- **Integration Tests**: Tests for component interactions (repositories, controllers with mocked dependencies)
- **End-to-End Tests**: Full system tests with real database and external services (optional, for critical paths)

---

## Testing Pyramid

```
         /\
        /  \     E2E Tests (Few)
       /----\
      /      \   Integration Tests (Some)
     /--------\
    /          \  Unit Tests (Many)
   /------------\
```

**Unit Tests (Base - 70-80%)**:
- Fast execution (< 1 second per test)
- Test individual methods and classes in isolation
- Use mocks for all external dependencies
- High coverage of business logic

**Integration Tests (Middle - 15-20%)**:
- Moderate execution time
- Test component interactions
- Use test database (H2 or test PostgreSQL)
- Mock external services (payment gateway)

**End-to-End Tests (Top - 5-10%)**:
- Slower execution
- Test complete user flows
- Use real database and test payment gateway
- Critical paths only

---

## Unit Testing Approach

### Framework and Tools

**Primary Testing Framework**: JUnit 5 (Jupiter)
- Modern, annotation-based testing
- Parameterized tests support
- Extension model for custom functionality

**Mocking Framework**: Mockito
- Easy-to-use mocking API
- Verification of interactions
- Argument captors for complex assertions

**Assertions**: AssertJ
- Fluent assertion API
- Readable error messages
- Rich set of matchers

**Test Runner**: `@ExtendWith(MockitoExtension.class)`
- Integrates Mockito with JUnit 5
- Automatic mock injection
- Clean, annotation-based setup

### Unit Test Structure

Each unit test follows the **Arrange-Act-Assert (AAA)** pattern:

```java
@Test
void testMethodName_Scenario_ExpectedResult() {
    // Arrange: Set up test data and mocks
    when(mockRepository.findById(1L)).thenReturn(Optional.of(testEntity));
    
    // Act: Execute the method under test
    Result result = serviceUnderTest.methodToTest(input);
    
    // Act: Verify the results
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(EXPECTED_STATUS);
    verify(mockRepository).findById(1L);
}
```

### What to Test in Unit Tests

#### Service Layer Unit Tests

**PaymentService Tests**:
- ✅ Business logic and validation rules
- ✅ State transitions
- ✅ Error handling and exception throwing
- ✅ Gateway interaction orchestration
- ✅ Transaction amount calculations
- ✅ Order status updates

**Example Test Categories**:
1. **Success Scenarios**: Happy path for each operation
2. **Validation Failures**: Invalid inputs, business rule violations
3. **Gateway Failures**: Gateway errors and error handling
4. **Edge Cases**: Boundary conditions, null values, empty collections
5. **State Management**: Order status transitions, transaction state tracking

**AuthService Tests**:
- ✅ User registration and password hashing
- ✅ Authentication logic
- ✅ Token generation and validation
- ✅ Refresh token management
- ✅ Password validation

**JwtService Tests**:
- ✅ Token generation (access and refresh)
- ✅ Token validation
- ✅ Claims extraction (email, userId, role)
- ✅ Expiration checking
- ✅ Invalid token handling

### Test Naming Convention

**Format**: `testMethodName_Scenario_ExpectedResult`

**Examples**:
- `testPurchase_Success` - Successful purchase operation
- `testPurchase_OrderNotFound` - Purchase fails when order doesn't exist
- `testCapture_PartialCapture` - Partial capture operation
- `testRefund_AmountExceedsRemaining` - Refund fails when amount too high

**Benefits**:
- Clear test intent
- Easy to identify test purpose
- Groups related tests together alphabetically
- Self-documenting test names

### Test Organization

**Package Structure**: Mirrors source structure
```
src/test/java/com/payment/
├── service/
│   ├── PaymentServiceTest.java
│   ├── AuthServiceTest.java
│   └── JwtServiceTest.java
├── controller/
│   ├── PaymentControllerTest.java
│   └── AuthControllerTest.java
├── gateway/
│   └── AuthorizeNetGatewayTest.java
└── repository/
    └── PaymentTransactionRepositoryTest.java
```

**Test Class Organization**:
```java
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    
    // Mocks
    @Mock
    private PaymentGateway paymentGateway;
    
    @Mock
    private OrderRepository orderRepository;
    
    // Service under test
    @InjectMocks
    private PaymentService paymentService;
    
    // Test data
    private User testUser;
    private Order testOrder;
    
    @BeforeEach
    void setUp() {
        // Initialize test data
    }
    
    // Test methods grouped by operation
    @Nested
    class PurchaseTests {
        @Test
        void testPurchase_Success() { ... }
        
        @Test
        void testPurchase_OrderNotFound() { ... }
    }
    
    @Nested
    class CaptureTests {
        @Test
        void testCapture_Success() { ... }
    }
    
    // Helper methods
    private PaymentRequest createPaymentRequest() { ... }
}
```

### Assertions Strategy

**Use AssertJ for readable assertions**:

```java
// Basic assertions
assertThat(result).isNotNull();
assertThat(result.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));

// Exception assertions
assertThatThrownBy(() -> service.method(input))
    .isInstanceOf(PaymentValidationException.class)
    .hasMessageContaining("Order not found");

// Collection assertions
assertThat(transactions).hasSize(2);
assertThat(transactions).contains(expectedTransaction);

// Null-safe assertions
assertThat(response.getOrder()).isNotNull();
assertThat(response.getOrder().getStatus()).isEqualTo(OrderStatus.COMPLETED);
```

**Verify Interactions**:
```java
// Verify method was called
verify(paymentGateway).purchase(any(PurchaseRequest.class));

// Verify method was called with specific arguments
verify(orderRepository).save(argThat(order -> 
    order.getStatus() == OrderStatus.COMPLETED
));

// Verify method was called exactly N times
verify(transactionRepository, times(2)).save(any(PaymentTransaction.class));

// Verify method was never called
verify(paymentGateway, never()).refund(any());
```

---

## Mocking Strategy

### When to Mock

**Mock External Dependencies**:
- ✅ Payment Gateway (Authorize.Net SDK)
- ✅ Database Repositories (for service tests)
- ✅ External Services
- ✅ File System Operations
- ✅ Time/Date Services (if needed)

**Do NOT Mock**:
- ❌ Value Objects (DTOs, entities used as data containers)
- ❌ Domain Logic (simple calculations, validations in the class under test)
- ❌ The Class Under Test
- ❌ Static utility classes (prefer dependency injection)

### Mocking Patterns

#### 1. Constructor Injection with Mockito

**Pattern**: Use `@Mock` and `@InjectMocks`

```java
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    
    @Mock
    private PaymentGateway paymentGateway;
    
    @Mock
    private OrderRepository orderRepository;
    
    @Mock
    private PaymentTransactionRepository transactionRepository;
    
    @InjectMocks
    private PaymentService paymentService; // Mocks injected via constructor
    
    // Tests...
}
```

**Benefits**:
- Automatic mock injection
- Clean setup
- No manual mock creation

#### 2. Stubbing Behavior

**Return Values**:
```java
// Simple return
when(orderRepository.findById(1L))
    .thenReturn(Optional.of(testOrder));

// Return for any argument
when(transactionRepository.save(any(PaymentTransaction.class)))
    .thenAnswer(invocation -> invocation.getArgument(0));

// Return different values on subsequent calls
when(mock.next())
    .thenReturn("first")
    .thenReturn("second");

// Throw exceptions
when(orderRepository.findById(999L))
    .thenThrow(new PaymentNotFoundException("Order not found"));
```

**Argument Matchers**:
```java
// Match any argument
when(repository.save(any(Entity.class))).thenReturn(entity);

// Match specific argument
when(repository.findById(eq(1L))).thenReturn(optional);

// Match with custom matcher
when(gateway.purchase(argThat(request -> 
    request.getAmount().compareTo(new BigDecimal("100")) == 0
))).thenReturn(response);
```

**Void Methods**:
```java
// Stub void method (do nothing by default)
doNothing().when(mockRepository).delete(any());

// Throw exception from void method
doThrow(new RuntimeException("Error"))
    .when(mockRepository).delete(any());
```

#### 3. Verification Patterns

**Basic Verification**:
```java
// Verify method was called
verify(paymentGateway).purchase(any(PurchaseRequest.class));

// Verify method was called N times
verify(transactionRepository, times(2)).save(any(PaymentTransaction.class));

// Verify method was never called
verify(paymentGateway, never()).refund(any());

// Verify no more interactions
verifyNoMoreInteractions(paymentGateway);
```

**Argument Captors** (for complex verification):
```java
ArgumentCaptor<PaymentTransaction> transactionCaptor = 
    ArgumentCaptor.forClass(PaymentTransaction.class);

verify(transactionRepository).save(transactionCaptor.capture());

PaymentTransaction captured = transactionCaptor.getValue();
assertThat(captured.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
assertThat(captured.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
```

#### 4. Mocking Gateway Responses

**Pattern**: Create realistic gateway response objects

```java
PurchaseResponse gatewayResponse = PurchaseResponse.builder()
    .success(true)
    .transactionId("TXN123")
    .authorizationCode("AUTH123")
    .accountNumber("1111")
    .accountType("Visa")
    .amount(new BigDecimal("100.00"))
    .currency("USD")
    .build();

when(paymentGateway.purchase(any(PurchaseRequest.class)))
    .thenReturn(gatewayResponse);
```

**Error Responses**:
```java
PurchaseResponse errorResponse = PurchaseResponse.builder()
    .success(false)
    .errorCode("5")
    .errorMessage("Do Not Honor")
    .amount(new BigDecimal("100.00"))
    .currency("USD")
    .build();

when(paymentGateway.purchase(any(PurchaseRequest.class)))
    .thenReturn(errorResponse);
```

**Exception Throwing**:
```java
when(paymentGateway.purchase(any(PurchaseRequest.class)))
    .thenThrow(new RuntimeException("Network error"));
```

#### 5. Repository Mocking Patterns

**Find Operations**:
```java
// Return entity
when(orderRepository.findById(1L))
    .thenReturn(Optional.of(testOrder));

// Return empty (not found)
when(orderRepository.findById(999L))
    .thenReturn(Optional.empty());

// Return for any ID
when(orderRepository.findById(anyLong()))
    .thenReturn(Optional.of(testOrder));
```

**Save Operations**:
```java
// Return saved entity (typically with ID set)
when(transactionRepository.save(any(PaymentTransaction.class)))
    .thenAnswer(invocation -> {
        PaymentTransaction tx = invocation.getArgument(0);
        tx.setId(100L); // Simulate database ID assignment
        return tx;
    });

// Return entity as-is
when(repository.save(any(Entity.class)))
    .thenAnswer(invocation -> invocation.getArgument(0));
```

**Custom Query Methods**:
```java
when(transactionRepository.findByIdempotencyKey("key-123"))
    .thenReturn(Optional.empty()); // No existing transaction

when(transactionRepository.findByIdempotencyKey("existing-key"))
    .thenReturn(Optional.of(existingTransaction));
```

### Mock Lifecycle

**Reset Mocks Between Tests**:
```java
@BeforeEach
void setUp() {
    // Reset mocks if needed (usually not necessary with @Mock)
    reset(mockRepository, mockGateway);
    
    // Or reinitialize test data
    testOrder = createTestOrder();
}
```

**Verify No Unexpected Interactions**:
```java
@AfterEach
void tearDown() {
    // Optional: Verify no unexpected interactions
    verifyNoMoreInteractions(mockGateway);
}
```

---

## Test Structure

### Test File Organization

```
src/test/java/com/payment/
├── service/
│   ├── PaymentServiceTest.java
│   ├── AuthServiceTest.java
│   └── JwtServiceTest.java
├── controller/
│   ├── PaymentControllerTest.java      # @WebMvcTest
│   └── AuthControllerTest.java
├── gateway/
│   └── AuthorizeNetGatewayTest.java    # Integration test with sandbox
├── repository/
│   └── PaymentTransactionRepositoryTest.java  # @DataJpaTest
└── integration/
    └── PaymentFlowIntegrationTest.java  # @SpringBootTest
```

### Service Layer Tests

**Annotation**: `@ExtendWith(MockitoExtension.class)`

**Structure**:
```java
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    
    // 1. Mocks
    @Mock
    private PaymentGateway paymentGateway;
    
    @Mock
    private OrderRepository orderRepository;
    
    // 2. Service under test
    @InjectMocks
    private PaymentService paymentService;
    
    // 3. Test data
    private User testUser;
    private Order testOrder;
    
    // 4. Setup
    @BeforeEach
    void setUp() {
        testUser = createTestUser();
        testOrder = createTestOrder();
    }
    
    // 5. Test methods (grouped by operation)
    
    // 6. Helper methods
    private PaymentRequest createPaymentRequest() { ... }
}
```

### Controller Tests

**Annotation**: `@WebMvcTest(PaymentController.class)`

**Structure**:
```java
@WebMvcTest(PaymentController.class)
class PaymentControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private PaymentService paymentService;
    
    @Test
    void testPurchase_ValidRequest_ReturnsOk() throws Exception {
        // Arrange
        PaymentResponse response = createPaymentResponse();
        when(paymentService.purchase(any(), any())).thenReturn(response);
        
        // Act & Assert
        mockMvc.perform(post("/payments/purchase")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + validToken)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
}
```

### Repository Tests

**Annotation**: `@DataJpaTest`

**Structure**:
```java
@DataJpaTest
class PaymentTransactionRepositoryTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private PaymentTransactionRepository repository;
    
    @Test
    void testFindByIdempotencyKey_ExistingKey_ReturnsTransaction() {
        // Arrange
        PaymentTransaction transaction = createTransaction();
        entityManager.persistAndFlush(transaction);
        
        // Act
        Optional<PaymentTransaction> found = 
            repository.findByIdempotencyKey(transaction.getIdempotencyKey());
        
        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(transaction.getId());
    }
}
```

### Integration Tests

**Annotation**: `@SpringBootTest` with `@AutoConfigureMockMvc`

**Structure**:
```java
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentFlowIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void testCompletePurchaseFlow() {
        // Register user
        // Login
        // Create order
        // Process purchase
        // Verify order status
    }
}
```

---

## Coverage Goals

### Overall Coverage Targets

| Layer | Target Coverage | Priority |
|-------|----------------|----------|
| Service Layer | **80%+** | High |
| Controller Layer | **70%+** | Medium |
| Gateway Layer | **60%+** | Medium |
| Repository Layer | **70%+** | Medium |
| Utility Classes | **90%+** | High |
| **Overall Project** | **70%+** | High |

### Coverage by Component

#### PaymentService (High Priority)

**Target**: 80%+ coverage

**Critical Paths to Test**:
- ✅ All payment operations (purchase, authorize, capture, cancel, refund)
- ✅ Success scenarios for each operation
- ✅ Validation failures (order not found, invalid amounts, etc.)
- ✅ Gateway failure scenarios
- ✅ State transition logic
- ✅ Partial operations (partial capture, partial refund)
- ✅ Idempotency handling
- ✅ Error handling and exception scenarios

**Example Coverage Breakdown**:
- Purchase flow: 85%+
- Authorize flow: 80%+
- Capture flow: 85%+ (including partial captures)
- Cancel flow: 80%+
- Refund flow: 85%+ (including partial refunds)

#### AuthService

**Target**: 80%+ coverage

**Critical Paths to Test**:
- ✅ User registration
- ✅ User login
- ✅ Token refresh
- ✅ Logout
- ✅ Password hashing
- ✅ Duplicate email handling
- ✅ Invalid credentials

#### JwtService

**Target**: 90%+ coverage

**Critical Paths to Test**:
- ✅ Token generation (access and refresh)
- ✅ Token validation
- ✅ Claims extraction
- ✅ Expiration checking
- ✅ Invalid token handling
- ✅ Malformed token handling

#### Controllers

**Target**: 70%+ coverage

**Critical Paths to Test**:
- ✅ Request validation
- ✅ Authentication enforcement
- ✅ Response mapping
- ✅ Error handling
- ✅ Status code correctness

#### Gateways

**Target**: 60%+ coverage

**Critical Paths to Test**:
- ✅ Successful API calls
- ✅ Error response handling
- ✅ Request/response mapping
- ✅ Configuration handling

### Coverage Measurement

**Tool**: JaCoCo Maven Plugin

**Configuration**:
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.8</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**Generate Report**:
```bash
mvn clean test jacoco:report
```

**View Report**: `target/site/jacoco/index.html`

### Coverage Exclusions

**What to Exclude from Coverage**:
- Configuration classes (simple property bindings)
- DTOs (data containers, no logic)
- Exception classes (simple constructors)
- Entity classes (JPA mappings, getters/setters)
- Main application class

**JaCoCo Exclusion Configuration**:
```xml
<configuration>
    <excludes>
        <exclude>**/config/**</exclude>
        <exclude>**/dto/**</exclude>
        <exclude>**/exception/**</exclude>
        <exclude>**/entity/**</exclude>
        <exclude>**/PaymentApplication.class</exclude>
    </excludes>
</configuration>
```

---

## Best Practices

### 1. Test Isolation

**Principles**:
- Each test is independent
- Tests can run in any order
- No shared state between tests
- Clean up after each test

**Implementation**:
```java
@BeforeEach
void setUp() {
    // Initialize fresh test data for each test
    testUser = createTestUser();
    testOrder = createTestOrder();
}

@AfterEach
void tearDown() {
    // Optional: Clean up if needed
    reset(mocks); // Only if necessary
}
```

### 2. Test Data Builders

**Pattern**: Use builder pattern for test data creation

```java
private User createTestUser() {
    return User.builder()
        .id(1L)
        .email("test@example.com")
        .passwordHash("hashed")
        .role(UserRole.USER)
        .enabled(true)
        .build();
}

private Order createTestOrder() {
    return Order.builder()
        .id(1L)
        .user(testUser)
        .orderNumber("ORD-001")
        .status(OrderStatus.PENDING)
        .totalAmount(new BigDecimal("100.00"))
        .currency("USD")
        .build();
}
```

**Benefits**:
- Reusable test data creation
- Easy to customize for specific tests
- Readable and maintainable

### 3. Descriptive Test Names

**Good**:
```java
@Test
void testPurchase_OrderNotFound_ThrowsPaymentNotFoundException() { ... }

@Test
void testCapture_PartialCapture_UpdatesOrderStatusToPartiallyCaptured() { ... }
```

**Bad**:
```java
@Test
void testPurchase() { ... }  // Too vague

@Test
void test1() { ... }  // Meaningless
```

### 4. Arrange-Act-Assert Pattern

**Structure**:
```java
@Test
void testMethod_Scenario_ExpectedResult() {
    // Arrange: Set up test data and mocks
    when(repository.findById(1L)).thenReturn(Optional.of(entity));
    
    // Act: Execute the method
    Result result = service.method(input);
    
    // Assert: Verify the results
    assertThat(result).isNotNull();
    verify(repository).findById(1L);
}
```

### 5. One Assertion Per Test (When Practical)

**Prefer**:
```java
@Test
void testPurchase_Success_ReturnsPaymentResponse() {
    PaymentResponse response = service.purchase(request, user);
    assertThat(response).isNotNull();
}

@Test
void testPurchase_Success_UpdatesOrderStatus() {
    service.purchase(request, user);
    verify(orderRepository).save(argThat(order -> 
        order.getStatus() == OrderStatus.COMPLETED
    ));
}
```

**Acceptable** (related assertions):
```java
@Test
void testPurchase_Success_ReturnsCorrectResponse() {
    PaymentResponse response = service.purchase(request, user);
    
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
    assertThat(response.getOrder().getStatus()).isEqualTo(OrderStatus.COMPLETED);
}
```

### 6. Test Error Scenarios

**Cover All Error Paths**:
- Invalid input validation
- Business rule violations
- External service failures
- Database errors
- Exception handling

**Example**:
```java
@Test
void testPurchase_OrderNotFound_ThrowsException() { ... }

@Test
void testPurchase_GatewayFailure_ReturnsFailedTransaction() { ... }

@Test
void testCapture_AuthorizationExpired_ThrowsException() { ... }
```

### 7. Avoid Test Interdependence

**Bad**:
```java
@Test
void test1() {
    service.createOrder();  // Test 1 creates data
}

@Test
void test2() {
    Order order = service.getOrder(1L);  // Test 2 depends on test 1
}
```

**Good**:
```java
@Test
void test1() {
    Order order = createTestOrder();
    Order saved = service.createOrder(order);
    assertThat(saved.getId()).isNotNull();
}

@Test
void test2() {
    Order order = createTestOrder();
    when(repository.findById(1L)).thenReturn(Optional.of(order));
    Order found = service.getOrder(1L);
    assertThat(found).isNotNull();
}
```

### 8. Mock External Dependencies Only

**Mock**:
- Payment Gateway
- Database Repositories (in service tests)
- External APIs

**Don't Mock**:
- Value Objects (DTOs, entities)
- Simple utility methods
- The class under test

### 9. Verify Important Interactions

**Verify Critical Operations**:
```java
// Verify gateway was called
verify(paymentGateway).purchase(any(PurchaseRequest.class));

// Verify repository saves
verify(transactionRepository).save(any(PaymentTransaction.class));

// Verify order status update
verify(orderRepository).save(argThat(order -> 
    order.getStatus() == OrderStatus.COMPLETED
));
```

**Don't Over-Verify**:
- Avoid verifying every method call
- Focus on critical business logic
- Trust that simple operations work

### 10. Use Test Constants

**Define Test Constants**:
```java
class TestConstants {
    static final String TEST_EMAIL = "test@example.com";
    static final String TEST_PASSWORD = "SecurePassword123";
    static final BigDecimal TEST_AMOUNT = new BigDecimal("100.00");
    static final String TEST_ORDER_NUMBER = "ORD-001";
}
```

---

## Test Data Management

### Test Data Builders

**Pattern**: Create reusable builders for test entities

```java
class TestDataBuilder {
    
    static User.UserBuilder user() {
        return User.builder()
            .id(1L)
            .email("test@example.com")
            .role(UserRole.USER)
            .enabled(true);
    }
    
    static Order.OrderBuilder order(User user) {
        return Order.builder()
            .user(user)
            .orderNumber("ORD-001")
            .status(OrderStatus.PENDING)
            .totalAmount(new BigDecimal("100.00"))
            .currency("USD");
    }
}

// Usage
User testUser = TestDataBuilder.user().build();
Order testOrder = TestDataBuilder.order(testUser).build();
```

### Test Fixtures

**Create Helper Methods**:
```java
private PaymentRequest createPaymentRequest() {
    PaymentRequest request = new PaymentRequest();
    request.setOrderId(1L);
    request.setAmount(new BigDecimal("100.00"));
    request.setCurrency("USD");
    request.setPaymentMethod(createPaymentMethod());
    return request;
}

private PaymentMethodRequest createPaymentMethod() {
    PaymentMethodRequest method = new PaymentMethodRequest();
    method.setCardNumber("4111111111111111");
    method.setExpiryMonth("12");
    method.setExpiryYear("2025");
    method.setCvv("123");
    method.setCardholderName("John Doe");
    return method;
}
```

### Realistic Test Data

**Use Realistic Values**:
- Valid credit card numbers (test cards)
- Proper date formats
- Realistic amounts
- Valid email formats
- Proper currency codes

**Avoid**:
- Placeholder values like "test", "123"
- Invalid formats
- Unrealistic data

---

## Running Tests

### Run All Tests
```bash
mvn test
```

### Run Specific Test Class
```bash
mvn test -Dtest=PaymentServiceTest
```

### Run Specific Test Method
```bash
mvn test -Dtest=PaymentServiceTest#testPurchase_Success
```

### Run with Coverage
```bash
mvn clean test jacoco:report
```

### View Coverage Report
Open `target/site/jacoco/index.html` in a browser

---

## Continuous Integration

### CI/CD Integration

**Test Execution**:
- Run all tests on every commit
- Fail build if tests fail
- Generate coverage reports
- Enforce coverage thresholds

**Coverage Thresholds**:
```xml
<configuration>
    <rules>
        <rule>
            <limits>
                <limit>
                    <counter>LINE</counter>
                    <value>COVEREDRATIO</value>
                    <minimum>0.70</minimum>
                </limit>
            </limits>
        </rule>
    </rules>
</configuration>
```

---

This testing strategy ensures comprehensive test coverage, maintainable test code, and confidence in the payment processing system's reliability and correctness.

