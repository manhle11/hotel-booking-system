package com.hanbay.hotel.pricing.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.hanbay.hotel.common.exception.RequestValidationException;
import com.hanbay.hotel.pricing.service.model.UpdateHolidayCalendarStatusCommand;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;

@Getter
public class UpdateHolidayCalendarStatusRequest {

    @NotNull(message = "Trạng thái ngày lễ không được để trống") private Boolean active;

    private final Set<String> unsupportedFields = new LinkedHashSet<>();

    public UpdateHolidayCalendarStatusRequest() {}

    public UpdateHolidayCalendarStatusRequest(Boolean active) {
        this.active = active;
    }

    @JsonAnySetter
    public void rejectUnsupportedField(String fieldName, Object ignoredValue) {
        unsupportedFields.add(fieldName);
    }

    public UpdateHolidayCalendarStatusCommand toCommand() {
        rejectUnsupportedFields();
        return new UpdateHolidayCalendarStatusCommand(active);
    }

    private void rejectUnsupportedFields() {
        if (!unsupportedFields.isEmpty()) {
            String field = unsupportedFields.iterator().next();
            throw new RequestValidationException(field, "Trường dữ liệu không được hỗ trợ");
        }
    }
}
