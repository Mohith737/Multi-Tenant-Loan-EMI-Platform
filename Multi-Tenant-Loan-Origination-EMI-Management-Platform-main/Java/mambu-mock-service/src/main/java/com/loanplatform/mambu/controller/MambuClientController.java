package com.loanplatform.mambu.controller;

import com.loanplatform.mambu.exception.MambuResourceNotFoundException;
import com.loanplatform.mambu.model.client.MambuClient;
import com.loanplatform.mambu.model.client.MambuClientAddress;
import com.loanplatform.mambu.model.client.MambuIdDocument;
import com.loanplatform.mambu.repository.MambuClientRepository;
import com.loanplatform.mambu.util.MambuIdGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/clients")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mambu Clients", description = "Mambu Client API Mock")
public class MambuClientController {

    private final MambuClientRepository clientRepository;
    private final MambuIdGenerator idGenerator;

    @PostMapping
    @Operation(summary = "Create a Mambu Client (Borrower)")
    public ResponseEntity<Map<String, Object>> createClient(@RequestBody Map<String, Object> request) {
        log.info("Mambu Mock — POST /api/v2/clients: {} {}", request.get("firstName"), request.get("lastName"));

        String encodedKey = idGenerator.generateEncodedKey();
        String clientId = idGenerator.generateClientId();

        MambuClient client = MambuClient.builder()
                .encodedKey(encodedKey)
                .clientId(clientId)
                .firstName((String) request.get("firstName"))
                .lastName((String) request.get("lastName"))
                .emailAddress((String) request.get("emailAddress"))
                .mobilePhone((String) request.get("mobilePhone"))
                .gender((String) request.get("gender"))
                .state(stringOrDefault(request.get("state"), "ACTIVE"))
                .assignedBranchKey((String) request.get("assignedBranchKey"))
                .notes((String) request.get("notes"))
                .idDocuments(new ArrayList<>())
                .addresses(new ArrayList<>())
                .build();

        if (request.get("birthDate") != null) {
            client.setBirthDate(LocalDate.parse((String) request.get("birthDate")));
        }

        // Parse ID documents
        if (request.get("idDocuments") instanceof List<?> docs) {
            for (Object d : docs) {
                if (d instanceof Map<?, ?> doc) {
                    client.getIdDocuments().add(MambuIdDocument.builder()
                            .clientEncodedKey(encodedKey)
                            .documentType((String) doc.get("documentType"))
                            .documentId((String) doc.get("documentId"))
                            .issuingAuthority((String) doc.get("issuingAuthority"))
                            .build());
                }
            }
        }

        // Parse addresses
        if (request.get("addresses") instanceof List<?> addrs) {
            for (Object a : addrs) {
                if (a instanceof Map<?, ?> addr) {
                    client.getAddresses().add(MambuClientAddress.builder()
                            .clientEncodedKey(encodedKey)
                            .line1((String) addr.get("line1"))
                            .city((String) addr.get("city"))
                            .region((String) addr.get("region"))
                            .postcode((String) addr.get("postcode"))
                            .country((String) addr.get("country"))
                            .indexInList(addr.get("indexInList") instanceof Number n ? n.intValue() : 0)
                            .build());
                }
            }
        }

        clientRepository.save(client);
        log.info("Mambu Mock — Client created: encodedKey={}, id={}", encodedKey, clientId);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(client));
    }

    @GetMapping("/{clientId}")
    @Operation(summary = "Get Mambu Client by ID")
    public ResponseEntity<Map<String, Object>> getClient(@PathVariable String clientId) {
        MambuClient client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new MambuResourceNotFoundException("Client not found: " + clientId));
        return ResponseEntity.ok(toResponse(client));
    }

    @PatchMapping("/{clientId}")
    @Operation(summary = "Update Mambu Client")
    public ResponseEntity<Map<String, Object>> updateClient(
            @PathVariable String clientId,
            @RequestBody Map<String, Object> request) {

        MambuClient client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new MambuResourceNotFoundException("Client not found: " + clientId));

        if (request.containsKey("state")) client.setState((String) request.get("state"));
        if (request.containsKey("emailAddress")) client.setEmailAddress((String) request.get("emailAddress"));
        if (request.containsKey("mobilePhone")) client.setMobilePhone((String) request.get("mobilePhone"));

        clientRepository.save(client);
        return ResponseEntity.ok(toResponse(client));
    }

    private Map<String, Object> toResponse(MambuClient c) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("encodedKey", c.getEncodedKey());
        res.put("id", c.getClientId());
        res.put("firstName", c.getFirstName());
        res.put("lastName", c.getLastName());
        res.put("fullName", c.getFirstName() + (c.getLastName() != null ? " " + c.getLastName() : ""));
        res.put("emailAddress", c.getEmailAddress());
        res.put("mobilePhone", c.getMobilePhone());
        res.put("birthDate", c.getBirthDate() != null ? c.getBirthDate().toString() : null);
        res.put("gender", c.getGender());
        res.put("state", c.getState());
        res.put("assignedBranchKey", c.getAssignedBranchKey());
        res.put("creationDate", c.getCreationDate() + "+05:30");
        res.put("lastModifiedDate", c.getLastModifiedDate() + "+05:30");
        res.put("clientRole", Map.of("encodedKey", "8a818e8d00000001"));

        // ID documents
        List<Map<String, Object>> docs = new ArrayList<>();
        if (c.getIdDocuments() != null) {
            for (MambuIdDocument doc : c.getIdDocuments()) {
                docs.add(Map.of(
                        "documentType", doc.getDocumentType(),
                        "documentId", doc.getDocumentId(),
                        "issuingAuthority", doc.getIssuingAuthority() != null ? doc.getIssuingAuthority() : ""
                ));
            }
        }
        res.put("idDocuments", docs);

        // Addresses
        List<Map<String, Object>> addresses = new ArrayList<>();
        if (c.getAddresses() != null) {
            for (MambuClientAddress addr : c.getAddresses()) {
                addresses.add(Map.of(
                        "line1", addr.getLine1() != null ? addr.getLine1() : "",
                        "city", addr.getCity() != null ? addr.getCity() : "",
                        "region", addr.getRegion() != null ? addr.getRegion() : "",
                        "postcode", addr.getPostcode() != null ? addr.getPostcode() : "",
                        "country", addr.getCountry() != null ? addr.getCountry() : "",
                        "indexInList", addr.getIndexInList()
                ));
            }
        }
        res.put("addresses", addresses);
        return res;
    }

    private String stringOrDefault(Object value, String fallback) {
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return fallback;
    }
}
