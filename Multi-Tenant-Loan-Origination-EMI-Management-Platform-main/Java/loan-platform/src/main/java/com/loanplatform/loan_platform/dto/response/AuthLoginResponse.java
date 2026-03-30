package com.loanplatform.loan_platform.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "JWT login response")
public class AuthLoginResponse {

    @Schema(example = "Bearer")
    private String tokenType;

    private String accessToken;

    @Schema(example = "3600")
    private long expiresInSeconds;

    private Instant expiresAt;

    private String username;

    private List<String> roles;

    private UUID tenantId;
}
