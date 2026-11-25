package com.hanbay.hotel.ai.service;

import static com.hanbay.hotel.room.recommendation.RecommendationModels.*;

import com.hanbay.hotel.ai.dto.AiUiAction;
import com.hanbay.hotel.common.exception.DomainException;
import com.hanbay.hotel.room.recommendation.RoomRecommendationService;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** Request-local tools: resolve persisted references before refreshing prices and availability. */
public final class RoomRecommendationTool {
    public record Selection(@ToolParam(required = false) Integer position, @ToolParam(required = false) String name) {}

    public record Evidence(
            String intent,
            Status status,
            List<String> questions,
            Map<String, String> facts,
            List<RoomRecommendationConversationStore.RoomReference> rooms,
            String guidance) {}

    private final RoomRecommendationService service;
    private final RoomRecommendationConversationStore store;
    private final UUID user;
    private final UUID conversation;
    private final Consumer<String> progress;
    private Result result;
    private String validationQuestion;
    private String intent = "SEARCH";
    private int calls;
    private AiUiAction action;
    private final Map<String, String> facts = new LinkedHashMap<>();
    private List<String> questions = List.of();
    private boolean touched;

    public RoomRecommendationTool(
            RoomRecommendationService service,
            RoomRecommendationConversationStore store,
            UUID user,
            UUID conversation) {
        this(service, store, user, conversation, ignored -> {});
    }

    public RoomRecommendationTool(
            RoomRecommendationService service,
            RoomRecommendationConversationStore store,
            UUID user,
            UUID conversation,
            Consumer<String> progress) {
        this.service = service;
        this.store = store;
        this.user = user;
        this.conversation = conversation;
        this.progress = progress;
    }

    @Tool(
            returnDirect = true,
            name = "recommend_rooms",
            description =
                    "Hiểu/cập nhật nhu cầu và tìm phòng. Chỉ gửi phần khách vừa cung cấp; null giữ nguyên, [] xóa danh sách, clearFields xóa trường. Chưa biết ngày/số khách/tuổi để null, tool trả câu hỏi; không đoán. Ngân sách không bắt buộc phải có; có ngân sách thì phân biệt bắt buộc/ưu tiên, từng đêm/toàn kỳ. Giường SINGLE,DOUBLE,QUEEN,KING; Twin=2 SINGLE. Tiện nghi WIFI,TV,AIR_CONDITIONING,MINIBAR,WORK_DESK hoặc tên catalog. Giường phụ/cách âm tuyệt đối chưa hỗ trợ: unsupportedRequirements. Không tự đổi điều kiện bắt buộc. Thường giới thiệu 1-2 phòng, tối đa 3 khi cần. Kết quả là evidence, không phải lời thoại.")
    public synchronized Object recommend(
            @ToolParam(description = "Thay đổi nhu cầu từ lời khách, giữ nguyên các trường khác") Patch patch,
            @ToolParam(required = false, description = "Số phương án từ 1 đến 3, mặc định 2") Integer maxOptions) {
        if (!begin("SEARCH", "Mình đang kiểm tra các lựa chọn theo nhu cầu của bạn…")) return evidence();
        try {
            var context = store.merge(user, conversation, patch);
            result = withRooms(
                    service.recommend(context.criteria()),
                    maxOptions == null ? 2 : Math.max(1, Math.min(3, maxOptions)));
            store.remember(user, conversation, context.version(), result, null);
            buildFacts();
        } catch (DomainException exception) {
            clarify(exception.getMessage());
        }
        return evidence();
    }

    public Object recommend(Patch patch) {
        return recommend(patch, 2);
    }

    @Tool(
            returnDirect = true,
            name = "inspect_recommended_room",
            description =
                    "Xem chi tiết phòng đã giới thiệu. position là thứ tự 1-based trong danh sách vừa hiển thị; name là tên từ hội thoại. Cả hai null nghĩa là phòng đã chọn hoặc phòng duy nhất. Nhiều phòng chưa rõ thì hỏi lại. Kiểm lại dữ liệu thật.")
    public synchronized Object inspect(@ToolParam(description = "Phòng khách muốn tìm hiểu") Selection selection) {
        return focus(selection, false);
    }

    @Tool(
            returnDirect = true,
            name = "select_recommended_room",
            description =
                    "Chỉ dùng khi khách nói chọn/đặt/lấy một phòng đã giới thiệu. Resolve theo thứ tự/tên/phòng đang chọn, kiểm lại giá, sức chứa và tồn rồi chuẩn bị nút checkout. Không tạo booking/giữ chỗ.")
    public synchronized Object select(
            @ToolParam(description = "Phòng khách thể hiện ý định chọn") Selection selection) {
        return focus(selection, true);
    }

    private Object focus(Selection selection, boolean choose) {
        if (!begin(choose ? "SELECT" : "DETAILS", "Mình đang kiểm tra lại thông tin và giá của phòng này…"))
            return evidence();
        try {
            var context = store.get(user, conversation);
            UUID id = resolve(selection, context);
            if (id == null) return evidence();
            Result fresh = service.recommendForRooms(context.criteria(), List.of(id));
            var matched = fresh.recommendations().stream()
                    .filter(r -> r.room().id().equals(id))
                    .toList();
            if (fresh.status() != Status.RECOMMENDED || matched.isEmpty()) {
                result = fresh;
                store.remember(user, conversation, context.version(), fresh, null);
                clarify(
                        fresh.status() == Status.TEMPORARILY_UNAVAILABLE
                                ? "Mình chưa xác minh được lịch của phòng này lúc này. Bạn muốn mình kiểm tra lại sau một chút chứ?"
                                : "Phòng vừa nhắc đến hiện không còn nằm trong các phương án đáp ứng nhu cầu. Bạn muốn mình tìm lựa chọn khác chứ?");
                return evidence();
            }
            result = withRooms(fresh, matched);
            store.rememberSelection(user, conversation, context.version(), id);
            if (choose) {
                var c = fresh.criteria();
                action = new AiUiAction(
                        AiUiAction.Type.CONTINUE_BOOKING,
                        "Tiếp tục đặt phòng",
                        new AiUiAction.Params(
                                id, c.checkIn(), c.checkOut(), c.adults(), c.children(), null, null, c.childAges()));
            }
            buildFacts();
            if (choose)
                facts.put(
                        "nextStep",
                        "Bạn có thể tiếp tục nhập thông tin đặt phòng bằng nút bên dưới; phòng chưa được giữ chỗ");
        } catch (DomainException exception) {
            clarify(exception.getMessage());
        }
        return evidence();
    }

    @Tool(
            returnDirect = true,
            name = "compare_recommended_rooms",
            description =
                    "So sánh phòng vừa giới thiệu theo dữ liệu thật. selections là 2-3 tham chiếu theo thứ tự/tên; null hoặc [] so sánh hai phòng đầu. Chênh lệch giá/diện tích do Java tính, không tự tính. Giữ nguyên nhu cầu.")
    public synchronized Object compare(
            @ToolParam(required = false, description = "Hai hoặc ba phòng cần so sánh") List<Selection> selections) {
        if (!begin("COMPARE", "Mình đang đối chiếu các phòng theo cùng kỳ lưu trú…")) return evidence();
        try {
            var context = store.get(user, conversation);
            if (context.rooms().size() < 2) {
                clarify(
                        "Mình cần ít nhất hai phương án đã kiểm tra để so sánh. Bạn muốn tìm thêm phòng theo nhu cầu hiện tại không?");
                return evidence();
            }
            List<Selection> requested = selections == null || selections.isEmpty()
                    ? List.of(new Selection(1, null), new Selection(2, null))
                    : selections;
            if (requested.size() < 2 || requested.size() > 3) {
                clarify("Bạn muốn so sánh hai hay ba phòng nào trong các phương án vừa xem?");
                return evidence();
            }
            List<UUID> ids = new ArrayList<>();
            for (Selection ref : requested) {
                UUID id = resolve(ref, context);
                if (id == null) return evidence();
                ids.add(id);
            }
            if (new HashSet<>(ids).size() != ids.size()) {
                clarify("Bạn hãy chọn hai phòng khác nhau để mình so sánh nhé.");
                return evidence();
            }
            Result fresh = service.recommendForRooms(context.criteria(), ids);
            Map<UUID, RecommendedRoom> available = new HashMap<>();
            fresh.recommendations().forEach(r -> available.put(r.room().id(), r));
            if (!available.keySet().containsAll(ids)) {
                result = fresh;
                store.remember(user, conversation, context.version(), fresh, null);
                clarify(
                        "Một phương án vừa thay đổi hoặc không còn đáp ứng nhu cầu. Mình cần tìm lại trước khi so sánh.");
                return evidence();
            }
            result = withRooms(fresh, ids.stream().map(available::get).toList());
            store.remember(user, conversation, context.version(), result, null);
            buildFacts();
            var a = result.recommendations().get(0).room();
            var b = result.recommendations().get(1).room();
            long difference = Math.abs(Math.subtractExact(a.roomTotal(), b.roomTotal()));
            facts.put(
                    "comparison.price",
                    difference == 0
                            ? "hai phương án có cùng tổng tiền phòng"
                            : (a.roomTotal() < b.roomTotal() ? a.name() : b.name()) + " tiết kiệm hơn "
                                    + money(difference) + " cho cả kỳ");
            if (a.areaM2() != null && b.areaM2() != null) {
                BigDecimal delta = a.areaM2().subtract(b.areaM2());
                facts.put(
                        "comparison.area",
                        delta.signum() == 0
                                ? "hai phương án có cùng diện tích"
                                : (delta.signum() > 0 ? a.name() : b.name()) + " rộng hơn "
                                        + delta.abs().stripTrailingZeros().toPlainString() + " m²");
            }
        } catch (DomainException exception) {
            clarify(exception.getMessage());
        }
        return evidence();
    }

    private boolean begin(String mode, String status) {
        if (++calls > 4) return false;
        touched = true;
        result = null;
        action = null;
        validationQuestion = null;
        questions = List.of();
        facts.clear();
        intent = mode;
        progress.accept(status);
        return true;
    }

    private UUID resolve(Selection ref, RoomRecommendationConversationStore.Context context) {
        if (ref != null && ref.position() != null) {
            int index = ref.position() - 1;
            if (index >= 0 && index < context.rooms().size()) {
                var room = context.rooms().get(index);
                if (ref.name() == null || ref.name().equalsIgnoreCase(room.name())) return room.id();
            }
        } else if (ref != null && ref.name() != null) {
            var matches = context.rooms().stream()
                    .filter(r -> r.name().equalsIgnoreCase(ref.name().trim()))
                    .toList();
            if (matches.size() == 1) return matches.get(0).id();
        } else if (context.selectedRoomTypeId() != null) return context.selectedRoomTypeId();
        else if (context.rooms().size() == 1) return context.rooms().get(0).id();
        clarify(
                context.rooms().isEmpty()
                        ? "Bạn cho mình biết ngày ở và số khách để tìm phòng trước nhé."
                        : "Bạn đang muốn nói đến phòng nào trong các phương án vừa xem?");
        return null;
    }

    private Result withRooms(Result source, int count) {
        return withRooms(source, source.recommendations().stream().limit(count).toList());
    }

    private Result withRooms(Result source, List<RecommendedRoom> rooms) {
        return new Result(
                source.status(),
                source.criteria(),
                source.evaluatedAt(),
                source.currency(),
                source.priceScope(),
                source.scoringVersion(),
                rooms,
                source.questions(),
                source.rejections(),
                source.suggestions(),
                source.warnings());
    }

    private void clarify(String question) {
        validationQuestion = question;
        questions = List.of(question);
        facts.put("question", question);
        action = null;
    }

    private void buildFacts() {
        if (result == null) return;
        var c = result.criteria();
        if (c.checkIn() != null && c.checkOut() != null)
            facts.put("stay", "nhận ngày " + date(c.checkIn()) + ", trả ngày " + date(c.checkOut()));
        if (c.adults() != null && c.children() != null)
            facts.put(
                    "guests",
                    c.adults() + " người lớn và " + c.children() + " trẻ em"
                            + (c.childAges() == null || c.childAges().isEmpty()
                                    ? ""
                                    : " (tuổi tại ngày nhận: " + c.childAges() + ")"));
        if (result.status() == Status.NEEDS_CLARIFICATION) {
            List<String> next = new ArrayList<>();
            if (c.checkIn() == null && c.checkOut() == null) next.add("Bạn dự định nhận và trả phòng ngày nào?");
            if (c.adults() == null && c.children() == null) next.add("Có bao nhiêu người lớn và trẻ em đi cùng bạn?");
            for (String q : result.questions()) {
                if (c.checkIn() == null
                        && c.checkOut() == null
                        && (q.equals("Bạn muốn nhận phòng ngày nào?") || q.equals("Bạn muốn trả phòng ngày nào?")))
                    continue;
                if (c.adults() == null
                        && c.children() == null
                        && (q.startsWith("Có bao nhiêu người lớn") || q.startsWith("Có bao nhiêu trẻ em"))) continue;
                next.add(q);
            }
            questions = next.stream().limit(2).toList();
        }
        for (int i = 0; i < questions.size(); i++) facts.put("question." + (i + 1), questions.get(i));
        for (int i = 0; i < result.recommendations().size(); i++) {
            var rec = result.recommendations().get(i);
            var r = rec.room();
            String key = "room." + (i + 1) + ".";
            facts.put(key + "name", r.name());
            facts.put(key + "price", money(r.roomTotal()) + " tiền phòng cho " + r.totalNights() + " đêm");
            facts.put(
                    key + "beds",
                    r.beds().isEmpty()
                            ? "cấu hình giường chưa được xác minh"
                            : r.beds().stream()
                                    .map(b -> b.quantity() + " giường "
                                            + switch (b.bedType()) {
                                                case SINGLE -> "đơn";
                                                case DOUBLE -> "đôi";
                                                case QUEEN -> "Queen";
                                                case KING -> "King";
                                            })
                                    .collect(java.util.stream.Collectors.joining(" và ")));
            if (r.areaM2() != null)
                facts.put(
                        key + "area",
                        "diện tích " + r.areaM2().stripTrailingZeros().toPlainString() + " m²");
            if (r.viewType() != null)
                facts.put(
                        key + "view",
                        "hướng "
                                + switch (r.viewType()) {
                                    case SEA -> "biển";
                                    case CITY -> "thành phố";
                                    case COURTYARD -> "sân trong";
                                });
            facts.put(
                    key + "amenities",
                    rec.amenities().isEmpty()
                            ? "chưa có tiện nghi được khai báo"
                            : "các tiện nghi được khai báo: "
                                    + rec.amenities().stream()
                                            .map(a -> a.name())
                                            .collect(java.util.stream.Collectors.joining(", ")));
            facts.put(key + "capacity", "đáp ứng sức chứa đã khai báo cho " + facts.get("guests"));
            facts.put(key + "availability", "đã kiểm tra khả năng đáp ứng toàn kỳ; kết quả chưa giữ chỗ");
            if (!rec.unmetPreferences().isEmpty())
                facts.put(key + "tradeoff", String.join(" ", rec.unmetPreferences()));
            for (int j = 0; j < rec.reasons().size(); j++)
                facts.put(key + "reason." + j, rec.reasons().get(j));
        }
        if (result.status() == Status.NO_MATCH)
            facts.put(
                    "noMatch",
                    result.rejections().stream()
                            .map(Rejection::message)
                            .collect(java.util.stream.Collectors.joining(" ")));
        if (!result.suggestions().isEmpty()) facts.put("alternatives", String.join(" ", result.suggestions()));
    }

    public Evidence evidence() {
        return new Evidence(
                intent,
                result == null ? Status.NEEDS_CLARIFICATION : result.status(),
                questions,
                Map.copyOf(facts),
                result == null
                        ? List.of()
                        : result.recommendations().stream()
                                .map(r -> new RoomRecommendationConversationStore.RoomReference(
                                        r.room().id(), r.room().name()))
                                .toList(),
                "Viết lời tư vấn tự nhiên bằng paragraphs. Chèn dữ kiện qua {{tên_fact}}; không tự viết giá/số liệu/đặc điểm ngoài facts. Hỏi tối đa hai ý quan trọng, không liệt kê form. Không nói đã đặt/giữ chỗ. Khi SELECT nút checkout do backend phát.");
    }

    public Result result() {
        return result;
    }

    public Result cards() {
        return validationQuestion == null
                        && result != null
                        && result.status() == Status.RECOMMENDED
                        && (intent.equals("SEARCH") || intent.equals("COMPARE"))
                ? result
                : null;
    }

    public boolean touched() {
        return touched;
    }

    public String validationQuestion() {
        return validationQuestion;
    }

    public Map<String, String> facts() {
        return Map.copyOf(facts);
    }

    public AiUiAction action() {
        return action;
    }

    public List<String> suggestions() {
        if (result == null || validationQuestion != null || result.status() != Status.RECOMMENDED || action != null)
            return List.of();
        if (intent.equals("DETAILS")) return List.of("Tôi chọn phòng này", "Có phương án tiết kiệm hơn không?");
        return result.recommendations().size() > 1
                ? List.of("So sánh hai phòng đầu giúp tôi", "Có phương án tiết kiệm hơn không?")
                : List.of("Cho tôi tìm hiểu phòng này", "Tôi chọn phòng này");
    }

    public String answer() {
        if (validationQuestion != null) return validationQuestion;
        if (result == null) return "";
        if (result.status() == Status.NEEDS_CLARIFICATION)
            return String.join(
                    "\n",
                    questions.isEmpty() ? result.questions().stream().limit(2).toList() : questions);
        if (result.status() == Status.TEMPORARILY_UNAVAILABLE)
            return "Mình chưa xác minh được lịch phòng lúc này. Bạn thử lại sau một chút nhé.";
        if (result.status() == Status.NO_MATCH)
            return "Mình chưa tìm được phòng đáp ứng đầy đủ nhu cầu. " + facts.getOrDefault("noMatch", "") + " "
                    + facts.getOrDefault("alternatives", "");
        if (intent.equals("COMPARE"))
            return facts.get("room.1.name") + " và " + facts.get("room.2.name") + ": " + facts.get("comparison.price")
                    + ". " + facts.getOrDefault("comparison.area", "") + ". Bạn muốn tìm hiểu phòng nào thêm?";
        if (intent.equals("DETAILS"))
            return facts.get("room.1.name") + ": " + facts.get("room.1.beds") + "; " + facts.get("room.1.amenities")
                    + ". " + facts.getOrDefault("room.1.area", "") + ". Bạn muốn chọn phòng này chứ?";
        var first = result.recommendations().get(0);
        return (action == null ? "Mình gợi ý " : "Bạn đang chọn ")
                + first.room().name() + ": " + facts.get("room.1.beds") + ", " + facts.get("room.1.price") + ". "
                + (action == null
                        ? "Bạn muốn tìm hiểu thêm hay so sánh các lựa chọn?"
                        : "Bạn có thể tiếp tục nhập thông tin đặt phòng bên dưới.");
    }

    public List<String> requiredReferences() {
        if (validationQuestion != null) return List.of("question");
        if (result == null) return List.of();
        if (result.status() == Status.NEEDS_CLARIFICATION)
            return facts.containsKey("question.1") ? List.of("question.1") : List.of();
        if (result.status() == Status.NO_MATCH) return facts.containsKey("noMatch") ? List.of("noMatch") : List.of();
        if (result.status() != Status.RECOMMENDED) return List.of();
        List<String> keys = new ArrayList<>(
                switch (intent) {
                    case "COMPARE" -> facts.containsKey("comparison.area")
                            ? List.of("room.1.name", "room.2.name", "comparison.price", "comparison.area")
                            : List.of("room.1.name", "room.2.name", "comparison.price");
                    case "DETAILS" -> List.of("room.1.name", "room.1.beds", "room.1.amenities");
                    case "SELECT" -> List.of("room.1.name", "room.1.price", "nextStep");
                    default -> List.of("room.1.name", "room.1.price");
                });
        if (intent.equals("SEARCH"))
            for (int i = 2; i <= result.recommendations().size(); i++) {
                keys.add("room." + i + ".name");
                keys.add("room." + i + ".price");
            }
        facts.keySet().stream().filter(k -> k.endsWith(".tradeoff")).forEach(keys::add);
        return List.copyOf(keys);
    }

    public Map<String, Object> compositionContext() {
        Map<String, String> descriptions = new TreeMap<>();
        for (String key : facts.keySet()) {
            String kind = key.replaceFirst("^room\\.[0-9]+\\.", "");
            descriptions.put(
                    key,
                    switch (kind) {
                        case "name" -> "Tên phòng đã kiểm tra";
                        case "price" -> "Tổng tiền phòng và số đêm, đã tính bằng Java";
                        case "beds" -> "Số lượng và loại giường cố định";
                        case "area" -> "Diện tích thực";
                        case "view" -> "Hướng nhìn thực";
                        case "amenities" -> "Câu mô tả tình trạng dữ liệu tiện nghi; đọc trực tiếp, không thêm cụm có tiện nghi như";
                        case "nextStep" -> "Hướng dẫn tiếp tục checkout sau khi khách đã chọn phòng, không hỏi lựa chọn lại";
                        case "capacity" -> "Xác nhận đủ sức chứa cho đoàn khách";
                        case "availability" -> "Khả năng đáp ứng toàn kỳ, chưa giữ chỗ";
                        case "tradeoff" -> "Sở thích chưa đáp ứng, cần nói rõ";
                        case "comparison.price" -> "Phòng tiết kiệm hơn và số tiền chênh lệch cho cả kỳ";
                        case "comparison.area" -> "Phòng rộng hơn và diện tích chênh lệch";
                        case "noMatch" -> "Nguyên nhân không có phương án hợp lệ";
                        case "alternatives" -> "Những điều chỉnh khách có thể cân nhắc";
                        case "stay" -> "Ngày nhận/trả khách đã cung cấp";
                        case "guests" -> "Số khách và tuổi đã cung cấp";
                        default -> key.startsWith("question")
                                ? "Câu hỏi bổ sung cần gửi khách, không cần tự viết câu hỏi khác"
                                : "Lý do phù hợp đã được backend xác nhận";
                    });
        }
        String instruction =
                switch (intent) {
                    case "DETAILS" -> "Khách hỏi tìm hiểu phòng đang nhắc tới. Bắt đầu bằng tên phòng, mô tả dữ kiện. Không giới thiệu lại danh sách hoặc hỏi chọn phòng nào.";
                    case "SELECT" -> "Khách đã chọn phòng. Xác nhận tên và giá, dùng nextStep. Không hỏi lại khách muốn tìm hiểu/chọn lựa chọn nào.";
                    case "COMPARE" -> "Khách cần so sánh. Dùng comparison.price, comparison.area và nêu đánh đổi. Không lặp giới thiệu hai phòng như một tìm kiếm mới.";
                    default -> "Khách cần tư vấn. Giới thiệu ngắn tên và giá từng lựa chọn, chọn lý do phù hợp và nói tradeoff nếu có, kết thúc bằng lời mời tìm hiểu/so sánh.";
                };
        return Map.of(
                "instruction",
                instruction,
                "intent",
                intent,
                "status",
                result == null ? Status.NEEDS_CLARIFICATION : result.status(),
                "facts",
                descriptions,
                "requiredReferences",
                requiredReferences());
    }

    public String historyAnswer() {
        return answer();
    }

    private static String date(java.time.LocalDate value) {
        return value.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private static String money(long amount) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount) + " VND";
    }
}
