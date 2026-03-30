package com.loanplatform.loan_platform.integration.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanplatform.loan_platform.domain.tenant.model.Tenant;
import com.loanplatform.loan_platform.domain.tenant.model.TenantStatus;
import com.loanplatform.loan_platform.domain.tenant.repository.TenantRepository;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Test
    void login_withTenantAdminCredentials_returnsJwt() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String response = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "tenant_admin",
                                          "password": "REDACTED_SEE_ENV",
                                          "tenantId": "%s"
                                        }
                                        """.formatted(tenantId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.roles[0]").value("TENANT_ADMIN"))
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode body = objectMapper.readTree(response);
        String jwt = body.path("accessToken").asText();
        assertThat(jwt).contains(".");
    }

    @Test
    void login_withTenantAdminToken_canReachTenantScopedEndpoint() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String loginResponse = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "tenant_admin",
                                          "password": "REDACTED_SEE_ENV",
                                          "tenantId": "%s"
                                        }
                                        """.formatted(tenantId))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String jwt = objectMapper.readTree(loginResponse).path("accessToken").asText();

        mockMvc.perform(
                        get("/api/v1/loan-products")
                                .header("Authorization", "Bearer " + jwt)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TENANT_NOT_FOUND"));
    }

    @Test
    void adminLogin_withTenantAdminCredentials_returnsForbidden() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/admin/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "tenant_admin",
                                          "password": "REDACTED_SEE_ENV"
                                        }
                                        """)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void login_withRegisteredBorrower_returnsBorrowerToken() throws Exception {
        UUID tenantId = createActiveTenant();
        String email = "existing.borrower+" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "firstName": "Asha",
                                          "lastName": "Patel",
                                          "email": "%s",
                                          "password": "REDACTED_SEE_ENV",
                                          "mobile": "+919876543210",
                                          "dateOfBirth": "1992-08-10",
                                          "gender": "FEMALE",
                                          "panNumber": "ABCDE1234F",
                                          "aadhaarNumber": "987654321234",
                                          "tenantId": "%s",
                                          "address": {
                                            "line1": "221B Baker Street",
                                            "city": "Mumbai",
                                            "state": "Maharashtra",
                                            "pincode": "400001"
                                          }
                                        }
                                        """.formatted(email, tenantId))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        String loginResponse = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "%s",
                                          "password": "REDACTED_SEE_ENV"
                                        }
                                        """.formatted(email))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("BORROWER"))
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String jwt = objectMapper.readTree(loginResponse).path("accessToken").asText();
        String borrowerId = SignedJWT.parse(jwt).getJWTClaimsSet().getStringClaim("borrowerId");
        assertThat(borrowerId).isNotBlank();
    }

    @Test
    void submitKyc_withoutJwt_returnsUnauthorized() throws Exception {
        mockMvc.perform(
                        post("/api/v1/kyc/submit")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "documents": [
                                            {
                                              "documentType": "PAN_CARD",
                                              "documentNumber": "ABCDE1234F",
                                              "documentReference": "s3://kyc/pan.pdf"
                                            },
                                            {
                                              "documentType": "AADHAAR",
                                              "documentNumber": "123412341234",
                                              "documentReference": "s3://kyc/aadhaar.pdf"
                                            }
                                          ]
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    private UUID createActiveTenant() {
        Tenant tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .name("Auth Tenant")
                .domain("auth-" + UUID.randomUUID() + ".example.com")
                .status(TenantStatus.ACTIVE)
                .planTier("STANDARD")
                .mambuBranchId("branch-auth")
                .mambuBranchEncodedKey("branch_key_auth")
                .build();
        return tenantRepository.save(tenant).getId();
    }
}


