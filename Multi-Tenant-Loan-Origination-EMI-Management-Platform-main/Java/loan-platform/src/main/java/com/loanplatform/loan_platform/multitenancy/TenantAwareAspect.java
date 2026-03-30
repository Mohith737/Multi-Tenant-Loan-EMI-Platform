package com.loanplatform.loan_platform.multitenancy;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class TenantAwareAspect {

    @Around("@annotation(com.loanplatform.loan_platform.multitenancy.TenantAware)")
    public Object aroundTenantAware(ProceedingJoinPoint joinPoint) throws Throwable {
        log.debug("Executing tenant-aware method: {}", joinPoint.getSignature().toShortString());
        return joinPoint.proceed();
    }
}
