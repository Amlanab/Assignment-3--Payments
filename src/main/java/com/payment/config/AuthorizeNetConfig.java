package com.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "authorize-net")
public class AuthorizeNetConfig {
    
    private String apiLoginId;
    private String transactionKey;
    private String environment; // sandbox or production
    private String endpoint;
    
    public String getApiLoginId() {
        return apiLoginId;
    }
    
    public void setApiLoginId(String apiLoginId) {
        this.apiLoginId = apiLoginId;
    }
    
    public String getTransactionKey() {
        return transactionKey;
    }
    
    public void setTransactionKey(String transactionKey) {
        this.transactionKey = transactionKey;
    }
    
    public String getEnvironment() {
        return environment;
    }
    
    public void setEnvironment(String environment) {
        this.environment = environment;
    }
    
    public String getEndpoint() {
        return endpoint;
    }
    
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
}

