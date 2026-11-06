package com.hanbay.hotel.booking.scheduler;

import com.hanbay.hotel.booking.repository.BookingOrderRepository;
import com.hanbay.hotel.booking.service.BookingOrderService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingOrderExpirationScheduler {
    private final BookingOrderRepository repository;
    private final BookingOrderService service;
    private final Clock clock;

    @Scheduled(cron = "${booking.expiration.cron}")
    public void expireOrders() {
        // Each order has its own transaction and follows the same lock order as payment/cancellation.
        for (var id : repository.findExpiredIds(clock.instant())) {
            try {
                service.expire(id);
            } catch (RuntimeException exception) {
                log.error("Không thể xử lý hết hạn đơn {}", id, exception);
            }
        }
    }
}
