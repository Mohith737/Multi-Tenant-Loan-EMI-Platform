package com.loanplatform.mambu.repository;

import com.loanplatform.mambu.model.loanproduct.MambuLoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MambuLoanProductRepository extends JpaRepository<MambuLoanProduct, String> {
    Optional<MambuLoanProduct> findByProductId(String productId);
    List<MambuLoanProduct> findByForBranchKey(String branchKey);
    boolean existsByProductId(String productId);
}

