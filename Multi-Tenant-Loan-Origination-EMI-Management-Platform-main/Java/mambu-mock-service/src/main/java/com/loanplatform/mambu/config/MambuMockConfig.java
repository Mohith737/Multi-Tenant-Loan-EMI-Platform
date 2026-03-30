package com.loanplatform.mambu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "mambu.mock")
@Data
public class MambuMockConfig {
    private int simulateDelayMs = 150;
    private int errorRatePercent = 0;
    private String defaultCurrency = "INR";
    private int encodedKeyLength = 24;
}

