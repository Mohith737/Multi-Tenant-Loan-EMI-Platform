package com.loanplatform.mambu.util;

import org.springframework.stereotype.Component;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MambuIdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicLong COUNTER = new AtomicLong(1);

    /**
     * Generates a 24-character hex encoded key — identical format to real Mambu encodedKeys
     * e.g. "8a818e8a7f2b3c4d5e6f7a8b"
     */
    public String generateEncodedKey() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Generates a human-readable Mambu-style ID for a resource
     * e.g. branch_001, cli_001, loan_001
     */
    public String generateId(String prefix) {
        long count = COUNTER.getAndIncrement();
        long timestamp = Instant.now().toEpochMilli() % 100000;
        return prefix + "_" + String.format("%05d", timestamp) + String.format("%03d", count);
    }

    public String generateBranchId()      { return generateId("branch"); }
    public String generateProductId()     { return generateId("lp"); }
    public String generateClientId()      { return generateId("cli"); }
    public String generateLoanId()        { return generateId("loan"); }
    public String generateTransactionId() { return generateId("txn"); }
}

