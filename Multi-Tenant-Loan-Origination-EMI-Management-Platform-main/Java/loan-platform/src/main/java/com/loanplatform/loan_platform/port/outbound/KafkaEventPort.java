package com.loanplatform.loan_platform.port.outbound;

import java.util.UUID;

public interface KafkaEventPort {

    void publish(String topic, UUID tenantId, UUID entityId, Object payload);
}
