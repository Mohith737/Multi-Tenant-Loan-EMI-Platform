package com.loanplatform.mambu.repository;

import com.loanplatform.mambu.model.repayment.MambuTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MambuTransactionRepository extends JpaRepository<MambuTransaction, String> {
    List<MambuTransaction> findByParentAccountKeyOrderByCreationDateDesc(String accountKey);
    Optional<MambuTransaction> findByExternalId(String externalId);
    boolean existsByExternalId(String externalId);
}

