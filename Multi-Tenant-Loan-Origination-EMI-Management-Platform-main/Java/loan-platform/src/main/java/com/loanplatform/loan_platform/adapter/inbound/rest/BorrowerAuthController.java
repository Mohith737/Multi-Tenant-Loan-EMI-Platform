package com.loanplatform.loan_platform.adapter.inbound.rest;

import com.loanplatform.loan_platform.domain.borrower.service.BorrowerService;
import com.loanplatform.loan_platform.dto.request.BorrowerRegistrationRequest;
import com.loanplatform.loan_platform.dto.response.BorrowerRegistrationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
public class BorrowerAuthController {

    private final BorrowerService borrowerService;

    @PostMapping("/register")
    @PreAuthorize("permitAll()")
    public ResponseEntity<BorrowerRegistrationResponse> register(@Valid @RequestBody BorrowerRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(borrowerService.register(request));
    }
}
