package com.hanbay.hotel.room.recommendation;

import static com.hanbay.hotel.room.recommendation.RecommendationModels.*;

import com.hanbay.hotel.booking.policy.HotelStayPolicy;
import com.hanbay.hotel.room.exception.RoomValidationException;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomRequirementValidator {
    private final HotelStayPolicy policy;

    public List<String> questions(Criteria c) {
        if (c == null) throw new RoomValidationException("Nhu cầu tư vấn không được để trống.");
        List<String> questions = new ArrayList<>();
        if (c.checkIn() == null) questions.add("Bạn muốn nhận phòng ngày nào?");
        if (c.checkOut() == null) questions.add("Bạn muốn trả phòng ngày nào?");
        if (c.adults() == null) questions.add("Có bao nhiêu người lớn?");
        if (c.children() == null) questions.add("Có bao nhiêu trẻ em (nhập 0 nếu không có)?");
        if (c.adults() != null && (c.adults() < 1 || c.adults() > 100)) invalid("Số người lớn phải từ 1 đến 100.");
        if (c.children() != null && (c.children() < 0 || c.children() > 100)) invalid("Số trẻ em phải từ 0 đến 100.");
        if (c.checkIn() != null && (c.checkIn().isBefore(policy.today()) || policy.bookingClosed(c.checkIn())))
            invalid("Ngày nhận phòng đã qua hoặc đã ngừng nhận đặt phòng cùng ngày.");
        if (c.checkIn() != null && c.checkOut() != null) {
            long nights = ChronoUnit.DAYS.between(c.checkIn(), c.checkOut());
            if (nights < 1 || nights > 365) invalid("Kỳ lưu trú phải từ 1 đến 365 đêm.");
        }
        if (c.children() != null && c.childAges() != null) policy.validateChildAges(c.children(), c.childAges());
        if (c.children() != null && c.children() > 0 && c.childAges() == null)
            questions.add("Vui lòng cho biết tuổi tại ngày nhận phòng của từng trẻ em để kiểm tra sức chứa.");
        if (c.budget() != null) {
            if (c.budget().amount() == null
                    || c.budget().amount() < 0
                    || c.budget().amount() > 1_000_000_000_000L)
                invalid("Ngân sách tiền phòng phải từ 0 đến 1.000 tỷ VND.");
            if (c.budget().period() == null) questions.add("Ngân sách này tính theo đêm hay cho toàn kỳ lưu trú?");
            if (c.budget().required() == null) questions.add("Ngân sách là giới hạn bắt buộc hay mức ưu tiên?");
        }
        validateBeds(c.requiredBeds());
        validateBeds(c.preferredBeds());
        validateArea(c.minAreaM2());
        validateArea(c.preferredAreaM2());
        for (List<String> values : List.of(
                list(c.requiredAmenities()),
                list(c.preferredAmenities()),
                list(c.preferredCategories()),
                list(c.unsupportedRequirements()))) {
            if (values.size() > 30 || values.stream().anyMatch(s -> s == null || s.isBlank() || s.length() > 150))
                invalid("Danh sách tiêu chí không hợp lệ hoặc quá dài.");
        }
        if (c.roomCount() != null && c.roomCount() < 1) invalid("Số phòng phải lớn hơn 0.");
        if (c.roomCount() != null && c.roomCount() > 1)
            questions.add(
                    "Tư vấn hiện xếp hạng từng phòng. Bạn muốn chia người lớn/trẻ em vào từng phòng như thế nào? Có thể tiếp tục tại trang đặt nhiều phòng.");
        if (!list(c.unsupportedRequirements()).isEmpty())
            questions.add(
                    "Khách sạn chưa có dữ liệu xác minh cho yêu cầu: " + String.join(", ", c.unsupportedRequirements())
                            + ". Bạn có muốn bỏ yêu cầu này để tiếp tục tìm phòng?");
        return questions;
    }

    private void validateBeds(List<Bed> beds) {
        var seen = new HashSet<>();
        if (list(beds).size() > 4) invalid("Cấu hình giường không hợp lệ.");
        for (Bed bed : list(beds)) {
            if (bed == null
                    || bed.bedType() == null
                    || bed.quantity() == null
                    || bed.quantity() < 1
                    || bed.quantity() > 20
                    || !seen.add(bed.bedType()))
                invalid("Loại giường phải rõ ràng, không trùng, số lượng từ 1 đến 20.");
        }
    }

    private void validateArea(BigDecimal area) {
        if (area != null && (area.signum() <= 0 || area.compareTo(BigDecimal.valueOf(10000)) > 0))
            invalid("Diện tích yêu cầu không hợp lệ.");
    }

    private void invalid(String message) {
        throw new RoomValidationException(message);
    }
}
