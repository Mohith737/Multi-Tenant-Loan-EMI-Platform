package com.loanplatform.loan_platform.mapper;

import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.loan.model.NpaLoanRecord;
import com.loanplatform.loan_platform.dto.response.NpaPortfolioResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NpaMapper {

    @Mapping(target = "loanAccountId", source = "record.mambuLoanId")
    @Mapping(target = "borrowerName", expression = "java(formatBorrowerName(borrower))")
    @Mapping(target = "outstandingAmount", source = "record.outstandingAmount")
    @Mapping(target = "daysPassedDue", source = "record.maxDpd")
    @Mapping(target = "npaFlaggedAt", source = "record.npaFlaggedAt")
    @Mapping(target = "recoveryStage", expression = "java(record.getRecoveryStage().name())")
    @Mapping(target = "mambuState", source = "record.mambuState")
    NpaPortfolioResponse.NpaLoanItem toNpaLoanItem(NpaLoanRecord record, Borrower borrower);

    default String formatBorrowerName(Borrower borrower) {
        if (borrower == null) {
            return "Unknown Borrower";
        }
        String firstName = borrower.getFirstName() == null ? "" : borrower.getFirstName().trim();
        String lastName = borrower.getLastName() == null ? "" : borrower.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isBlank() ? "Unknown Borrower" : fullName;
    }
}
