package com.loanplatform.mambu.repository;

import com.loanplatform.mambu.model.client.MambuClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface MambuClientRepository extends JpaRepository<MambuClient, String> {
    Optional<MambuClient> findByClientId(String clientId);
    boolean existsByClientId(String clientId);
}

