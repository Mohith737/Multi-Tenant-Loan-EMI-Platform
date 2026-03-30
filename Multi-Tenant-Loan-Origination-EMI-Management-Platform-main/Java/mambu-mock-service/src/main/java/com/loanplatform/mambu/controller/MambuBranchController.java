package com.loanplatform.mambu.controller;

import com.loanplatform.mambu.config.MambuMockConfig;
import com.loanplatform.mambu.exception.MambuResourceNotFoundException;
import com.loanplatform.mambu.model.branch.MambuBranch;
import com.loanplatform.mambu.repository.MambuBranchRepository;
import com.loanplatform.mambu.util.MambuIdGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/branches")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mambu Branches", description = "Mambu Branch API Mock")
public class MambuBranchController {

    private final MambuBranchRepository branchRepository;
    private final MambuIdGenerator idGenerator;
    private final MambuMockConfig mockConfig;

    @PostMapping
    @Operation(summary = "Create a new Mambu Branch")
    public ResponseEntity<Map<String, Object>> createBranch(@RequestBody Map<String, Object> request) {
        delay();

        String branchId = (String) request.getOrDefault("id", idGenerator.generateBranchId());
        if (branchRepository.existsByBranchId(branchId)) {
            throw new IllegalArgumentException("Branch with id '" + branchId + "' already exists");
        }

        Map<String, Object> address = asMap(request.get("address"));
        MambuBranch branch = MambuBranch.builder()
                .encodedKey(idGenerator.generateEncodedKey())
                .branchId(branchId)
                .name((String) request.get("name"))
                .state((String) request.getOrDefault("state", "ACTIVE"))
                .country((String) address.get("country"))
                .city((String) address.get("city"))
                .addressLine1((String) address.get("line1"))
                .emailAddress((String) request.get("emailAddress"))
                .phoneNumber((String) request.get("phoneNumber"))
                .notes((String) request.get("notes"))
                .build();

        MambuBranch saved = branchRepository.save(branch);
        log.info("Mambu Mock — Branch created: encodedKey={}, id={}", saved.getEncodedKey(), saved.getBranchId());

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @GetMapping("/{branchId}")
    @Operation(summary = "Get Mambu Branch by ID")
    public ResponseEntity<Map<String, Object>> getBranch(@PathVariable String branchId) {
        MambuBranch branch = branchRepository.findByBranchId(branchId)
                .orElseThrow(() -> new MambuResourceNotFoundException("Branch not found: " + branchId));
        return ResponseEntity.ok(toResponse(branch));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private Map<String, Object> toResponse(MambuBranch b) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("encodedKey", b.getEncodedKey());
        response.put("id", b.getBranchId());
        response.put("name", b.getName());
        response.put("state", b.getState());
        response.put("creationDate", b.getCreationDate() + "+05:30");
        response.put("lastModifiedDate", b.getLastModifiedDate() + "+05:30");
        response.put("address", Map.of(
                "country", b.getCountry() == null ? "" : b.getCountry(),
                "city", b.getCity() == null ? "" : b.getCity()
        ));
        response.put("emailAddress", b.getEmailAddress());
        response.put("phoneNumber", b.getPhoneNumber());
        return response;
    }

    private void delay() {
        try {
            Thread.sleep(Math.max(mockConfig.getSimulateDelayMs(), 200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
