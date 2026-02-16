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

comment added using Github MCP by antigravity
this change is for scrum-5