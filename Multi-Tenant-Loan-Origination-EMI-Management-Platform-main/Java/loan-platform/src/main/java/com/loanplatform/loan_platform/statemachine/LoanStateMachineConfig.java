package com.loanplatform.loan_platform.statemachine;

import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleEvent;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleState;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class LoanStateMachineConfig {

    private final Map<LoanLifecycleState, Map<LoanLifecycleEvent, LoanLifecycleState>> transitions =
            new EnumMap<>(LoanLifecycleState.class);

    public LoanStateMachineConfig() {
        register(LoanLifecycleState.APPLICATION_DRAFT, LoanLifecycleEvent.SUBMIT, LoanLifecycleState.APPLICATION_SUBMITTED);
        register(LoanLifecycleState.APPLICATION_SUBMITTED, LoanLifecycleEvent.SELECT_OFFER, LoanLifecycleState.OFFER_SELECTED);
        register(LoanLifecycleState.APPLICATION_SUBMITTED, LoanLifecycleEvent.EXPIRE_OFFERS, LoanLifecycleState.OFFER_EXPIRED);
        register(LoanLifecycleState.APPLICATION_SUBMITTED, LoanLifecycleEvent.START_UNDER_VERIFICATION, LoanLifecycleState.UNDER_VERIFICATION);
        register(LoanLifecycleState.OFFER_SELECTED, LoanLifecycleEvent.START_UNDER_VERIFICATION, LoanLifecycleState.UNDER_VERIFICATION);
        register(LoanLifecycleState.UNDER_VERIFICATION, LoanLifecycleEvent.APPROVE, LoanLifecycleState.APPROVED);
        register(LoanLifecycleState.UNDER_VERIFICATION, LoanLifecycleEvent.REJECT, LoanLifecycleState.REJECTED);
        register(LoanLifecycleState.APPROVED, LoanLifecycleEvent.DISBURSE, LoanLifecycleState.DISBURSED);
        register(LoanLifecycleState.DISBURSED, LoanLifecycleEvent.ACTIVATE_REPAYMENT, LoanLifecycleState.ACTIVE_REPAYMENT);
        register(LoanLifecycleState.ACTIVE_REPAYMENT, LoanLifecycleEvent.EMI_COLLECTED, LoanLifecycleState.ACTIVE_REPAYMENT);
        register(LoanLifecycleState.ACTIVE_REPAYMENT, LoanLifecycleEvent.EMI_RETRY_EXHAUSTED, LoanLifecycleState.ACTIVE_REPAYMENT);
        register(LoanLifecycleState.ACTIVE_REPAYMENT, LoanLifecycleEvent.FLAG_NON_PERFORMING, LoanLifecycleState.NON_PERFORMING);
        register(LoanLifecycleState.NON_PERFORMING, LoanLifecycleEvent.ESCALATE_TO_LEGAL, LoanLifecycleState.LEGAL_ESCALATION);
        register(LoanLifecycleState.NON_PERFORMING, LoanLifecycleEvent.CLEAR_NPA_OVERRIDE, LoanLifecycleState.ACTIVE_REPAYMENT);
        register(LoanLifecycleState.LEGAL_ESCALATION, LoanLifecycleEvent.CLEAR_NPA_OVERRIDE, LoanLifecycleState.ACTIVE_REPAYMENT);
        register(LoanLifecycleState.ACTIVE_REPAYMENT, LoanLifecycleEvent.PREPAY_PARTIAL_CONFIRMED, LoanLifecycleState.ACTIVE_REPAYMENT);
        register(LoanLifecycleState.ACTIVE_REPAYMENT, LoanLifecycleEvent.FORECLOSURE_CONFIRMED, LoanLifecycleState.FORECLOSED);
        register(LoanLifecycleState.FORECLOSED, LoanLifecycleEvent.GENERATE_NOC, LoanLifecycleState.FORECLOSED);
    }

    public LoanLifecycleState resolve(LoanLifecycleState fromState, LoanLifecycleEvent event) {
        Map<LoanLifecycleEvent, LoanLifecycleState> byEvent = transitions.get(fromState);
        if (byEvent == null) {
            return null;
        }
        return byEvent.get(event);
    }

    private void register(LoanLifecycleState fromState, LoanLifecycleEvent event, LoanLifecycleState toState) {
        transitions.computeIfAbsent(fromState, key -> new EnumMap<>(LoanLifecycleEvent.class)).put(event, toState);
    }
}
