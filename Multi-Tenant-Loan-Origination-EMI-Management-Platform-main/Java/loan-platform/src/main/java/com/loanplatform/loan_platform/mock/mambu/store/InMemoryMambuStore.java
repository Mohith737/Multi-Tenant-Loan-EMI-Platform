package com.loanplatform.loan_platform.mock.mambu.store;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuBranchResponse;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuClientResponse;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanProductResponse;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryMambuStore {

    private final Map<String, MambuClientResponse> clients = new ConcurrentHashMap<>();
    private final Map<String, MambuBranchResponse> branches = new ConcurrentHashMap<>();
    private final Map<String, MambuLoanProductResponse> loanProducts = new ConcurrentHashMap<>();

    public MambuClientResponse saveClient(MambuClientResponse client) {
        clients.put(client.getId(), client);
        return client;
    }

    public MambuClientResponse getClient(String clientId) {
        return clients.get(clientId);
    }

    public MambuBranchResponse saveBranch(MambuBranchResponse branch) {
        branches.put(branch.getId(), branch);
        return branch;
    }

    public MambuBranchResponse getBranch(String branchId) {
        return branches.get(branchId);
    }

    public MambuLoanProductResponse saveLoanProduct(MambuLoanProductResponse loanProduct) {
        loanProducts.put(loanProduct.getId(), loanProduct);
        return loanProduct;
    }

    public MambuLoanProductResponse getLoanProduct(String loanProductId) {
        return loanProducts.get(loanProductId);
    }
}
