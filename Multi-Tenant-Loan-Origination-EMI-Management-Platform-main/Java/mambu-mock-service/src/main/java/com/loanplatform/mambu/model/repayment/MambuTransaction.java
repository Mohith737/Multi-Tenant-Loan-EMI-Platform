package com.loanplatform.mambu.model.repayment;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "mambu_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MambuTransaction {

    @Id
    @Column(name = "encoded_key", length = 64)
    private String encodedKey;

    @Column(name = "transaction_id", nullable = false, unique = true, length = 100)
    private String transactionId;

    @Column(nullable = false, length = 100)
    private String type;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "parent_account_key", nullable = false, length = 64)
    private String parentAccountKey;

    @Column(name = "parent_account_id", nullable = false, length = 100)
    private String parentAccountId;

    @Column(name = "external_id")
    private String externalId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "channel_id", length = 100)
    @Builder.Default
    private String channelId = "ONLINE_PAYMENT";

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(name = "creation_date", nullable = false, updatable = false)
    private LocalDateTime creationDate;

    @PrePersist
    protected void onCreate() {
        creationDate = LocalDateTime.now();
    }
}

