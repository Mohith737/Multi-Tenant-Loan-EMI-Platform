package com.loanplatform.loan_platform.port.outbound;

import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleEvent;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleState;

public interface LoanStateMachinePort {

    LoanLifecycleState transition(LoanLifecycleState currentState, LoanLifecycleEvent event);
}
