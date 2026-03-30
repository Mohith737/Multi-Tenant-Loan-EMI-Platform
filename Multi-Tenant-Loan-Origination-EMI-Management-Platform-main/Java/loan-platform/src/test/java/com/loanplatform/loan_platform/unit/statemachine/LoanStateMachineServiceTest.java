package com.loanplatform.loan_platform.unit.statemachine;

import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleEvent;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleState;
import com.loanplatform.loan_platform.statemachine.LoanStateMachineConfig;
import com.loanplatform.loan_platform.statemachine.LoanStateMachineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoanStateMachineServiceTest {

    private LoanStateMachineService loanStateMachineService;

    @BeforeEach
    void setUp() {
        loanStateMachineService = new LoanStateMachineService(new LoanStateMachineConfig());
    }

    @Test
    void transition_submitFromDraft_movesToApplicationSubmitted() {
        LoanLifecycleState next = loanStateMachineService.transition(LoanLifecycleState.APPLICATION_DRAFT, LoanLifecycleEvent.SUBMIT);
        assertThat(next).isEqualTo(LoanLifecycleState.APPLICATION_SUBMITTED);
    }

    @Test
    void transition_selectOfferFromSubmitted_movesToOfferSelected() {
        LoanLifecycleState next = loanStateMachineService.transition(LoanLifecycleState.APPLICATION_SUBMITTED, LoanLifecycleEvent.SELECT_OFFER);
        assertThat(next).isEqualTo(LoanLifecycleState.OFFER_SELECTED);
    }

    @Test
    void transition_invalidTransition_throwsIllegalStateException() {
        assertThatThrownBy(() -> loanStateMachineService.transition(LoanLifecycleState.OFFER_SELECTED, LoanLifecycleEvent.SUBMIT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void transition_startUnderVerificationFromOfferSelected_movesToUnderVerification() {
        LoanLifecycleState next = loanStateMachineService.transition(LoanLifecycleState.OFFER_SELECTED, LoanLifecycleEvent.START_UNDER_VERIFICATION);
        assertThat(next).isEqualTo(LoanLifecycleState.UNDER_VERIFICATION);
    }

    @Test
    void transition_approveFromUnderVerification_movesToApproved() {
        LoanLifecycleState next = loanStateMachineService.transition(LoanLifecycleState.UNDER_VERIFICATION, LoanLifecycleEvent.APPROVE);
        assertThat(next).isEqualTo(LoanLifecycleState.APPROVED);
    }

    @Test
    void transition_rejectFromUnderVerification_movesToRejected() {
        LoanLifecycleState next = loanStateMachineService.transition(LoanLifecycleState.UNDER_VERIFICATION, LoanLifecycleEvent.REJECT);
        assertThat(next).isEqualTo(LoanLifecycleState.REJECTED);
    }

    @Test
    void transition_flagNonPerformingFromActiveRepayment_movesToNonPerforming() {
        LoanLifecycleState next = loanStateMachineService.transition(
                LoanLifecycleState.ACTIVE_REPAYMENT,
                LoanLifecycleEvent.FLAG_NON_PERFORMING
        );
        assertThat(next).isEqualTo(LoanLifecycleState.NON_PERFORMING);
    }

    @Test
    void transition_escalateToLegalFromNonPerforming_movesToLegalEscalation() {
        LoanLifecycleState next = loanStateMachineService.transition(
                LoanLifecycleState.NON_PERFORMING,
                LoanLifecycleEvent.ESCALATE_TO_LEGAL
        );
        assertThat(next).isEqualTo(LoanLifecycleState.LEGAL_ESCALATION);
    }

    @Test
    void transition_clearNpaOverrideFromLegalEscalation_movesToActiveRepayment() {
        LoanLifecycleState next = loanStateMachineService.transition(
                LoanLifecycleState.LEGAL_ESCALATION,
                LoanLifecycleEvent.CLEAR_NPA_OVERRIDE
        );
        assertThat(next).isEqualTo(LoanLifecycleState.ACTIVE_REPAYMENT);
    }
}
