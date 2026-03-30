package com.loanplatform.mambu.repository;

import com.loanplatform.mambu.model.loan.MambuLoanAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MambuLoanAccountRepository extends JpaRepository<MambuLoanAccount, String> {
    Optional<MambuLoanAccount> findByLoanId(String loanId);
    List<MambuLoanAccount> findByAssignedBranchKeyAndAccountState(String branchKey, String state);
    List<MambuLoanAccount> findByAssignedBranchKey(String branchKey);
    boolean existsByLoanId(String loanId);

    @Query("SELECT l FROM MambuLoanAccount l WHERE l.assignedBranchKey = :branchKey AND l.accountState IN :states")
    List<MambuLoanAccount> findByBranchKeyAndStates(String branchKey, List<String> states);
}

