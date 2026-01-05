package com.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {
    
    private String secret;
    private long accessTokenExpirationMs = 900000; // 15 minutes
    private long refreshTokenExpirationMs = 604800000; // 7 days
    
    public String getSecret() {
        return secret;
    }
    
    public void setSecret(String secret) {
        this.secret = secret;
    }
    
    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }
    
    public void setAccessTokenExpirationMs(long accessTokenExpirationMs) {
        this.accessTokenExpirationMs = accessTokenExpirationMs;
    }
    
    public long getRefreshTokenExpirationMs() {
        return refreshTokenExpirationMs;
    }
    
    public void setRefreshTokenExpirationMs(long refreshTokenExpirationMs) {
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }
}

