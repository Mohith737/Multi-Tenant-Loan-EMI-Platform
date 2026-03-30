package com.loanplatform.loan_platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NpaOverrideResponse {

    private String loanAccountId;
    private boolean npaLockCleared;
    private Instant overriddenAt;
}
