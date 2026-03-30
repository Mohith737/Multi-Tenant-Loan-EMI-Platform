package com.loanplatform.mambu.model.branch;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mambu_branches")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MambuBranch {

    @Id
    @Column(name = "encoded_key", length = 64)
    private String encodedKey;

    @Column(name = "branch_id", nullable = false, unique = true, length = 100)
    private String branchId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 50)
    private String state;

    @Column(name = "email_address")
    private String emailAddress;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    private String country;
    private String city;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "creation_date", nullable = false, updatable = false)
    private LocalDateTime creationDate;

    @Column(name = "last_modified_date", nullable = false)
    private LocalDateTime lastModifiedDate;

    @PrePersist
    protected void onCreate() {
        creationDate = LocalDateTime.now();
        lastModifiedDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastModifiedDate = LocalDateTime.now();
    }
}

