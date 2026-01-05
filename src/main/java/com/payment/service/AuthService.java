package com.payment.service;

import com.payment.dto.request.LoginRequest;
import com.payment.dto.request.RegisterRequest;
import com.payment.dto.response.AuthResponse;
import com.payment.entity.RefreshToken;
import com.payment.entity.User;
import com.payment.enums.UserRole;
import com.payment.repository.RefreshTokenRepository;
import com.payment.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    
    @Value("${jwt.refresh-token-expiration-ms:604800000}")
    private long refreshTokenExpirationMs;
    
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }
        
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.USER)
                .enabled(true)
                .build();
        
        user = userRepository.save(user);
        
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());
        
        saveRefreshToken(user, refreshToken);
        
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
    
    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        // Revoke existing refresh tokens
        refreshTokenRepository.revokeAllUserTokens(user.getId());
        
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());
        
        saveRefreshToken(user, refreshToken);
        
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
    
    @Transactional
    public AuthResponse refreshToken(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        
        if (refreshToken.getRevoked() || refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Refresh token is expired or revoked");
        }
        
        User user = refreshToken.getUser();
        
        // Revoke old refresh token
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
        
        // Generate new tokens
        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String newRefreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());
        
        saveRefreshToken(user, newRefreshToken);
        
        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
    
    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.revokeAllUserTokens(userId);
    }
    
    private void saveRefreshToken(User user, String token) {
        // Convert milliseconds to days for expiration
        long days = refreshTokenExpirationMs / (1000 * 60 * 60 * 24);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(days);
        
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(token)
                .expiresAt(expiresAt)
                .revoked(false)
                .build();
        
        refreshTokenRepository.save(refreshToken);
    }
}

