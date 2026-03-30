package com.loanplatform.mambu.repository;

import com.loanplatform.mambu.model.loan.MambuInstallment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MambuInstallmentRepository extends JpaRepository<MambuInstallment, Long> {
    List<MambuInstallment> findByLoanEncodedKeyOrderByInstallmentNumberAsc(String loanEncodedKey);
    Optional<MambuInstallment> findByLoanEncodedKeyAndInstallmentNumber(String loanEncodedKey, int number);
    void deleteByLoanEncodedKey(String loanEncodedKey);
}

