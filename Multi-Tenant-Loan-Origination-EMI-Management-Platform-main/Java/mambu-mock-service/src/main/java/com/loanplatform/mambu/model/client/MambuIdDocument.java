package com.loanplatform.mambu.model.client;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "mambu_client_id_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MambuIdDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_encoded_key", nullable = false, length = 64)
    private String clientEncodedKey;

    @Column(name = "document_type", nullable = false, length = 100)
    private String documentType;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "issuing_authority")
    private String issuingAuthority;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;
}

