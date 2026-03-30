package com.loanplatform.loan_platform.unit.loan;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanSimulationRequest;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanSimulationResponse;
import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.domain.loan.model.EligibilityDecision;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleEvent;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleState;
import com.loanplatform.loan_platform.domain.loan.model.LoanOffer;
import com.loanplatform.loan_platform.domain.loan.service.EligibilityEngine;
import com.loanplatform.loan_platform.domain.loan.service.LoanApplicationService;
import com.loanplatform.loan_platform.domain.loan.service.LoanOfferSelectionService;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanOfferRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanStateHistoryRepository;
import com.loanplatform.loan_platform.domain.tenant.model.LoanProduct;
import com.loanplatform.loan_platform.domain.tenant.model.LoanProductConfigSnapshot;
import com.loanplatform.loan_platform.domain.tenant.repository.LoanProductRepository;
import com.loanplatform.loan_platform.dto.request.LoanApplicationRequest;
import com.loanplatform.loan_platform.dto.response.LoanApplicationResponse;
import com.loanplatform.loan_platform.dto.response.LoanOfferResponse;
import com.loanplatform.loan_platform.exception.MambuSimulationException;
import com.loanplatform.loan_platform.mapper.LoanApplicationMapper;
import com.loanplatform.loan_platform.port.outbound.KafkaEventPort;
import com.loanplatform.loan_platform.port.outbound.LoanStateMachinePort;
import com.loanplatform.loan_platform.port.outbound.MambuPort;
import com.loanplatform.loan_platform.statemachine.actions.SubmitApplicationAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanApplicationServiceTest {

    @Mock
    private BorrowerRepository borrowerRepository;

    @Mock
    private LoanProductRepository loanProductRepository;

    @Mock
    private LoanApplicationRepository loanApplicationRepository;

    @Mock
    private LoanOfferRepository loanOfferRepository;

    @Mock
    private LoanStateHistoryRepository loanStateHistoryRepository;

    @Mock
    private EligibilityEngine eligibilityEngine;

    @Mock
    private LoanOfferSelectionService loanOfferSelectionService;

    @Mock
    private MambuPort mambuPort;

    @Mock
    private LoanApplicationMapper loanApplicationMapper;

    @Mock
    private LoanStateMachinePort loanStateMachinePort;

    @Mock
    private KafkaEventPort kafkaEventPort;

    @Mock
    private SubmitApplicationAction submitApplicationAction;

    @InjectMocks
    private LoanApplicationService loanApplicationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(loanApplicationService, "offerValidityHours", 48L);
    }

    @Test
    void submitApplication_eligibleAndSimulationSuccess_persistsApplicationAndOffers() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();

        Borrower borrower = Borrower.builder()
                .id(borrowerId)
                .tenantId(tenantId)
                .mambuClientKey("cli_key_001")
                .build();

        LoanProduct loanProduct = LoanProduct.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .mambuProductKey("LP_KEY")
                .loanProductConfig(LoanProductConfigSnapshot.builder()
                        .minLoanAmount(BigDecimal.valueOf(10000))
                        .maxLoanAmount(BigDecimal.valueOf(800000))
                        .minTenureMonths(6)
                        .maxTenureMonths(60)
                        .annualInterestRate(BigDecimal.valueOf(14.5))
                        .processingFeePercent(BigDecimal.valueOf(1.5))
                        .build())
                .build();

        EligibilityDecision decision = EligibilityDecision.builder()
                .eligible(true)
                .riskCategory("LOW")
                .creditScore(780)
                .dtiRatio(BigDecimal.valueOf(32))
                .monthlyIncome(BigDecimal.valueOf(120000))
                .employmentType("SALARIED")
                .maxApprovedAmount(BigDecimal.valueOf(800000))
                .build();

        LoanApplicationRequest request = LoanApplicationRequest.builder()
                .loanProductKey("LP_KEY")
                .requestedAmount(BigDecimal.valueOf(500000))
                .requestedTenureMonths(36)
                .loanPurpose("HOME_RENOVATION")
                .build();

        when(borrowerRepository.findByIdAndTenantId(borrowerId, tenantId)).thenReturn(Optional.of(borrower));
        when(loanProductRepository.findByTenantIdAndMambuProductKey(tenantId, "LP_KEY")).thenReturn(Optional.of(loanProduct));
        when(eligibilityEngine.evaluate(tenantId, borrowerId, loanProduct, request.getRequestedAmount(), request.getRequestedTenureMonths()))
                .thenReturn(decision);
        when(loanStateMachinePort.transition(LoanLifecycleState.APPLICATION_DRAFT, LoanLifecycleEvent.SUBMIT))
                .thenReturn(LoanLifecycleState.APPLICATION_SUBMITTED);

        when(mambuPort.simulateLoan(any(MambuLoanSimulationRequest.class))).thenReturn(simulationResponse("17217.77", "119839.72", "14.5", "619839.72"));

        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(loanOfferRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(loanApplicationMapper.toLoanApplicationResponse(any(LoanApplication.class))).thenReturn(
                LoanApplicationResponse.builder().status("APPLICATION_SUBMITTED").loanState("APPLICATION_SUBMITTED").build()
        );
        when(loanApplicationMapper.toLoanOfferResponses(any())).thenReturn(List.of(
                LoanOfferResponse.builder().offerId(UUID.randomUUID()).build(),
                LoanOfferResponse.builder().offerId(UUID.randomUUID()).build(),
                LoanOfferResponse.builder().offerId(UUID.randomUUID()).build()
        ));

        LoanApplicationResponse response = loanApplicationService.submitApplication(tenantId, borrowerId, request);

        assertThat(response.getStatus()).isEqualTo("APPLICATION_SUBMITTED");
        assertThat(response.getOffers()).hasSize(3);

        ArgumentCaptor<MambuLoanSimulationRequest> simulationCaptor = ArgumentCaptor.forClass(MambuLoanSimulationRequest.class);
        verify(mambuPort, times(3)).simulateLoan(simulationCaptor.capture());
        assertThat(simulationCaptor.getAllValues()).extracting(MambuLoanSimulationRequest::getRepaymentInstallments)
                .containsExactly(36, 24, 48);

        verify(loanApplicationRepository, times(1)).save(any(LoanApplication.class));
        verify(loanOfferRepository, times(1)).saveAll(any(List.class));
    }

    @Test
    void submitApplication_mambuSimulationFails_throwsMambuSimulationException() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();

        Borrower borrower = Borrower.builder()
                .id(borrowerId)
                .tenantId(tenantId)
                .mambuClientKey("cli_key_001")
                .build();

        LoanProduct loanProduct = LoanProduct.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .mambuProductKey("LP_KEY")
                .loanProductConfig(LoanProductConfigSnapshot.builder()
                        .minLoanAmount(BigDecimal.valueOf(10000))
                        .maxLoanAmount(BigDecimal.valueOf(800000))
                        .minTenureMonths(6)
                        .maxTenureMonths(60)
                        .annualInterestRate(BigDecimal.valueOf(14.5))
                        .processingFeePercent(BigDecimal.valueOf(1.5))
                        .build())
                .build();

        EligibilityDecision decision = EligibilityDecision.builder()
                .eligible(true)
                .riskCategory("LOW")
                .creditScore(780)
                .dtiRatio(BigDecimal.valueOf(32))
                .monthlyIncome(BigDecimal.valueOf(120000))
                .employmentType("SALARIED")
                .maxApprovedAmount(BigDecimal.valueOf(800000))
                .build();

        LoanApplicationRequest request = LoanApplicationRequest.builder()
                .loanProductKey("LP_KEY")
                .requestedAmount(BigDecimal.valueOf(500000))
                .requestedTenureMonths(36)
                .loanPurpose("HOME_RENOVATION")
                .build();

        when(borrowerRepository.findByIdAndTenantId(borrowerId, tenantId)).thenReturn(Optional.of(borrower));
        when(loanProductRepository.findByTenantIdAndMambuProductKey(tenantId, "LP_KEY")).thenReturn(Optional.of(loanProduct));
        when(eligibilityEngine.evaluate(tenantId, borrowerId, loanProduct, request.getRequestedAmount(), request.getRequestedTenureMonths()))
                .thenReturn(decision);

        when(mambuPort.simulateLoan(any(MambuLoanSimulationRequest.class)))
                .thenThrow(new RuntimeException("simulate down"));

        assertThatThrownBy(() -> loanApplicationService.submitApplication(tenantId, borrowerId, request))
                .isInstanceOf(MambuSimulationException.class);

        verify(loanApplicationRepository, times(0)).save(any(LoanApplication.class));
        verify(loanOfferRepository, times(0)).saveAll(any(List.class));
    }

    private MambuLoanSimulationResponse simulationResponse(String emi, String interest, String apr, String total) {
        return MambuLoanSimulationResponse.builder()
                .periodicPayment(new BigDecimal(emi))
                .totalInterestCharged(new BigDecimal(interest))
                .annualPercentageRate(new BigDecimal(apr))
                .totalAmountRepaid(new BigDecimal(total))
                .build();
    }
}
