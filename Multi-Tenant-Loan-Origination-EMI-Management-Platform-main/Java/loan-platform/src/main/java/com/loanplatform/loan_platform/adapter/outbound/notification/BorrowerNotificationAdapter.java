package com.loanplatform.loan_platform.adapter.outbound.notification;

import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.port.outbound.BorrowerNotificationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class BorrowerNotificationAdapter implements BorrowerNotificationPort {

    private final BorrowerRepository borrowerRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.notification.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.notification.email.from-address:no-reply@loanos.local}")
    private String emailFromAddress;

    @Value("${app.notification.email.override-to:}")
    private String emailOverrideTo;

    @Value("${app.notification.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${app.notification.sms.provider-url:https://example.com/fake-sms}")
    private String smsProviderUrl;

    @Value("${app.notification.sms.api-key:fake_sms_api_key}")
    private String smsApiKey;

    @Value("${app.notification.sms.sender-id:LOANOS}")
    private String smsSenderId;

    @Value("${app.notification.sms.override-to:}")
    private String smsOverrideTo;

    private final RestClient restClient = RestClient.builder().build();

    @Override
    public void onKycSubmitted(UUID borrowerId, UUID tenantId, int documentCount) {
        notifyBorrower(borrowerId, tenantId, "KYC Submitted",
                "Your KYC has been submitted successfully with " + documentCount + " document(s).");
    }

    @Override
    public void onKycVerified(UUID borrowerId, UUID tenantId) {
        notifyBorrower(borrowerId, tenantId, "KYC Verified",
                "Your KYC has been verified successfully.");
    }

    @Override
    public void onKycRejected(UUID borrowerId, UUID tenantId, String reason) {
        notifyBorrower(borrowerId, tenantId, "KYC Rejected",
                "Your KYC was rejected. Reason: " + reason);
    }

    @Override
    public void onLoanApproved(UUID borrowerId, UUID tenantId, UUID applicationId) {
        notifyBorrower(borrowerId, tenantId, "Loan Approved",
                "Your loan application " + applicationId + " is approved.");
    }

    @Override
    public void onLoanRejected(UUID borrowerId, UUID tenantId, UUID applicationId, String reason) {
        notifyBorrower(borrowerId, tenantId, "Loan Rejected",
                "Your loan application " + applicationId + " is rejected. Reason: " + reason);
    }

    @Override
    public void onLoanDisbursed(UUID borrowerId, UUID tenantId, UUID applicationId, String summaryMessage) {
        notifyBorrower(borrowerId, tenantId, "Loan Disbursed",
                "Your loan application " + applicationId + " has been disbursed. " + summaryMessage);
    }

    private void notifyBorrower(UUID borrowerId, UUID tenantId, String subject, String messageBody) {
        Borrower borrower = borrowerRepository.findByIdAndTenantId(borrowerId, tenantId).orElse(null);
        if (borrower == null) {
            log.warn("Notification skipped borrower not found borrowerId={} tenantId={}", borrowerId, tenantId);
            return;
        }

        String emailRecipient = (emailOverrideTo != null && !emailOverrideTo.isBlank())
                ? emailOverrideTo.trim()
                : borrower.getEmail();
        if (emailEnabled) {
            sendEmail(emailRecipient, subject, messageBody);
        } else {
            log.info("Email notification disabled borrowerId={} tenantId={} email={} subject={} body={}",
                    borrowerId, tenantId, emailRecipient, subject, messageBody);
        }

        String smsRecipient = (smsOverrideTo != null && !smsOverrideTo.isBlank())
                ? smsOverrideTo.trim()
                : borrower.getMobile();
        if (smsEnabled) {
            sendSms(smsRecipient, messageBody);
        } else {
            log.info("SMS notification disabled borrowerId={} tenantId={} mobile={} body={}",
                    borrowerId, tenantId, smsRecipient, messageBody);
        }
    }

    private void sendEmail(String toEmail, String subject, String body) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Email notification skipped due to blank recipient subject={}", subject);
            return;
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("JavaMailSender unavailable. Email skipped to={} subject={}", toEmail, subject);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(emailFromAddress);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email notification sent to={} subject={}", toEmail, subject);
        } catch (Exception ex) {
            log.error("Email notification failed to={} subject={}", toEmail, subject, ex);
        }
    }

    private void sendSms(String mobile, String body) {
        if (mobile == null || mobile.isBlank()) {
            log.warn("SMS notification skipped due to blank recipient");
            return;
        }
        try {
            restClient.post()
                    .uri(smsProviderUrl)
                    .header("X-API-KEY", smsApiKey)
                    .body(Map.of(
                            "to", mobile,
                            "senderId", smsSenderId,
                            "message", body
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.info("SMS notification sent to={}", mobile);
        } catch (Exception ex) {
            log.error("SMS notification failed to={}", mobile, ex);
        }
    }
}
