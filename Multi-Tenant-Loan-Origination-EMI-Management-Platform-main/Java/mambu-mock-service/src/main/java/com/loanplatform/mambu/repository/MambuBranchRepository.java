package com.loanplatform.mambu.repository;

import com.loanplatform.mambu.model.branch.MambuBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface MambuBranchRepository extends JpaRepository<MambuBranch, String> {
    Optional<MambuBranch> findByBranchId(String branchId);
    boolean existsByBranchId(String branchId);
}

