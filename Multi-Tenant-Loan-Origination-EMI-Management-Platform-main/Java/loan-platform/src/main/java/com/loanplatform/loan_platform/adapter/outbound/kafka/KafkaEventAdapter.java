package com.loanplatform.loan_platform.adapter.outbound.kafka;

import com.loanplatform.loan_platform.port.outbound.KafkaEventPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class KafkaEventAdapter implements KafkaEventPort {

    @Override
    public void publish(String topic, UUID tenantId, UUID entityId, Object payload) {
        log.debug("Publishing event topic={} tenantId={} entityId={} payload={}", topic, tenantId, entityId, payload);
    }
}
