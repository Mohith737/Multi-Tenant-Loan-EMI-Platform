package com.loanplatform.loan_platform.statemachine;

import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleEvent;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleState;
import com.loanplatform.loan_platform.port.outbound.LoanStateMachinePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanStateMachineService implements LoanStateMachinePort {

    private final LoanStateMachineConfig config;

    @Override
    public LoanLifecycleState transition(LoanLifecycleState currentState, LoanLifecycleEvent event) {
        LoanLifecycleState fromState = currentState == null ? LoanLifecycleState.APPLICATION_DRAFT : currentState;
        LoanLifecycleState toState = config.resolve(fromState, event);
        if (toState == null) {
            throw new IllegalStateException("Invalid loan transition from=" + fromState + " event=" + event);
        }
        log.debug("Loan transition from={} event={} to={}", fromState, event, toState);
        return toState;
    }
}
