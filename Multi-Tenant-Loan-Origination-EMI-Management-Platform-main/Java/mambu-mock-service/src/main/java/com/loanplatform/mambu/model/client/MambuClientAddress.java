package com.loanplatform.mambu.model.client;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mambu_client_addresses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MambuClientAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_encoded_key", nullable = false, length = 64)
    private String clientEncodedKey;

    private String line1;
    private String line2;
    private String city;
    private String region;
    private String postcode;
    private String country;

    @Column(name = "index_in_list")
    @Builder.Default
    private Integer indexInList = 0;
}

