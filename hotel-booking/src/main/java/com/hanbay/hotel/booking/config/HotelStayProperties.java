package com.hanbay.hotel.booking.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.time.ZoneId;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "hotel.stay")
public class HotelStayProperties {
    @NotBlank private String zone = "Asia/Ho_Chi_Minh";

    @NotNull private LocalTime checkInTime = LocalTime.of(14, 0);

    @NotNull private LocalTime checkOutTime = LocalTime.NOON;

    @NotNull private LocalTime sameDayBookingCutoff = LocalTime.of(23, 59, 59);

    @NotNull private LocalTime lateArrivalCutoff = LocalTime.of(6, 0);

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }

    @AssertTrue(message = "Múi giờ hoặc thứ tự giờ nhận/trả phòng không hợp lệ") public boolean isValidSchedule() {
        try {
            zoneId();
            return checkOutTime != null
                    && checkInTime != null
                    && lateArrivalCutoff != null
                    && checkOutTime.isBefore(checkInTime)
                    && lateArrivalCutoff.isBefore(checkOutTime);
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
