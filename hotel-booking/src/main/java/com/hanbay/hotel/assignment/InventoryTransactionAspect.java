package com.hanbay.hotel.assignment;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(1)
@RequiredArgsConstructor
public class InventoryTransactionAspect {
    private final InventoryWriteGuard guard;

    @Around("execution(public * com.hanbay.hotel..*(..))"
            + " && (within(com.hanbay.hotel.booking..*) || within(com.hanbay.hotel.room..*)"
            + " || within(com.hanbay.hotel.maintenance..*) || within(com.hanbay.hotel.housekeeping..*)"
            + " || within(com.hanbay.hotel.payment..*) || within(com.hanbay.hotel.roommap..*))"
            + " && (@annotation(org.springframework.transaction.annotation.Transactional)"
            + " || @within(org.springframework.transaction.annotation.Transactional))")
    public Object serializeInventory(ProceedingJoinPoint invocation) throws Throwable {
        guard.enlist();
        return invocation.proceed();
    }
}
