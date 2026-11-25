# AI Room Recommendation

AI Concierge dùng một cuộc trò chuyện tự nhiên: hỏi bổ sung, tìm phòng, so sánh, tìm hiểu và chọn phòng ngay trong chat. Thường giới thiệu một hoặc hai **room type**, tối đa ba khi khách cần; mỗi phương án cho một phòng. Không còn tab form lọc riêng hoặc điểm/trọng số trên giao diện. Không giữ chỗ, tạo booking, đổi phòng vật lý hay áp dụng lịch gán phòng trong lúc tư vấn.

## Luồng xử lý

1. Chat tiếp tục dùng `POST /api/v1/ai/chat/stream`, yêu cầu đăng nhập như trước. Spring AI nhận structured tool arguments với OpenAI strict mode. `recommend_rooms` nhận `Patch(values, clearFields)`; null không thay đổi, danh sách rỗng thay bằng rỗng, `clearFields` xóa một trường. Khi chỉ sửa số tiền, đơn vị và tính bắt buộc của ngân sách trước đó được giữ lại.
2. Nhu cầu lưu tại Redis theo user + conversation, có version, TTL một giờ. Lịch sử gửi tới model dưới vai user/assistant riêng biệt, không gộp thành một chuỗi user. Mỗi lượt chỉ hỏi một hoặc hai ý quan trọng, không yêu cầu ngân sách khi khách chưa có. Đổi ngày nhận hoặc số trẻ em sẽ xóa tuổi cũ nếu request không cung cấp tuổi mới. Không tự đoán người lớn/trẻ em, tuổi hoặc giường. Thiếu dữ liệu trả câu hỏi bổ sung trước khi truy vấn inventory.
3. `AvailableRoomTypeSearchService` lọc ACTIVE, sức chứa, ngày và tồn theo kỳ; engine đọc đủ các trang, bổ sung amenities bằng truy vấn batch, dùng giá đã được `PricingService` tính trong kết quả search.
4. Java loại các phương án vi phạm yêu cầu bắt buộc, rồi `RoomAssignmentFeasibilityService` giả lập thêm một nhu cầu PENDING trong bộ nhớ và gọi solver hiện có với `optimize=false`. Snapshot được đọc trong transaction read-only repeatable-read. Mỗi lần solve tối đa 0,5 giây, tổng khoảng hai giây cho tập ứng viên. UNKNOWN không bị diễn giải thành hết phòng.
5. Engine xếp hạng và lấy tối đa ba phương án. `recommend_rooms(patch, maxOptions)` mặc định lấy hai. Các tool phòng dùng `returnDirect=true`: sau khi hiểu ý định/gọi tool, model không tự viết thêm câu trả lời từ dữ liệu tool. Bước viết lời tư vấn riêng dùng JSON `paragraphs[].segments[]` gồm văn nói hoặc `factKey`; enum factKey chỉ chứa các key của kết quả hiện tại. Bước này nhận mô tả key và hướng dẫn theo ý định, không nhận giá trị giá/phòng để chép nhầm vào văn nói. Java chuyển segment thành tham chiếu như `{{room.1.name}}`, `{{room.1.price}}`, `{{comparison.price}}`. Backend thay các tham chiếu bằng dữ kiện Java; tham chiếu sai, giá/số liệu hoặc một số khẳng định không được phép ngoài tham chiếu sẽ dùng câu trả lời dự phòng. Model không sửa thẻ phòng, giá hay thứ hạng. Câu trả lời được kiểm tra xong mới phát theo đoạn, không phát token chưa xác minh. Nếu provider lỗi hoặc vượt thời gian sau khi có kết quả Java, vẫn trả kết quả an toàn; truy vấn mới lỗi không dùng lại kết quả cũ.
6. `inspect_recommended_room`, `compare_recommended_rooms`, `select_recommended_room` nhận thứ tự/tên phòng trong danh sách đã hiển thị. Redis lưu thứ tự, phòng đang nhắc tới và câu hỏi còn thiếu. Resolve ID trước khi refresh, không suy thứ tự từ bảng xếp hạng mới. Mỗi lần xem/so sánh/chọn đều kiểm lại bằng engine; tra phòng được tham chiếu trước khi cắt top ba. Không rõ “phòng này” thì hỏi lại. Khi đổi nhu cầu, xóa tham chiếu cũ; kết quả có version cũ không ghi đè context mới.
   Với câu hỏi phòng/so sánh rõ ràng, backend giới hạn bộ tool phòng và đặt `tool_choice=required`; nếu không có evidence trong lượt hiện tại thì không phát câu trả lời về phòng. Model vẫn tự chọn thao tác và phân tích dữ liệu, Java không trích nhu cầu bằng regex.
7. SSE phát `status` ngay khi bắt đầu và khi chạy tool; `delta` chứa đoạn hội thoại đã kiểm tra, `recommendations` chứa thẻ phòng xác minh, `suggestions` chứa tối đa ba câu nhắn tiếp. Thẻ hiển thị ảnh, giường, tiền phòng cả kỳ, tiện nghi và lý do ngắn. Các nút tìm hiểu/chọn và câu gợi ý gửi tin nhắn vào cùng phiên chat.
8. Chỉ `select_recommended_room` phát action checkout sau khi kiểm tra lại. Thẻ tìm phòng không điều hướng đặt trực tiếp. Ngày, số người và tuổi trẻ em theo action backend sang checkout; transaction đặt phòng hiện tại vẫn kiểm tra lại dữ liệu, giá và tồn khi khách xác nhận.

RAG không tham gia lọc/xếp hạng; policy tool và pgvector hiện tại giữ vai trò tra cứu chính sách.

## API engine

`POST /api/v1/room-recommendations` yêu cầu đăng nhập, áp dụng giới hạn request theo tài khoản cùng cấu hình `ai.policy-rag.rate-limit-per-minute`. Endpoint engine được giữ cho tích hợp/kiểm thử có cấu trúc và không cần OpenAI; widget chỉ dùng chat.

Ví dụ request:

```json
{
  "checkIn": "2026-12-01",
  "checkOut": "2026-12-03",
  "adults": 2,
  "children": 0,
  "budget": {"amount": 6000000, "period": "TOTAL_STAY", "required": true},
  "requiredBeds": [{"bedType": "SINGLE", "quantity": 2}],
  "bedMatch": "AT_LEAST",
  "requiredAmenities": ["WIFI"],
  "economy": true,
  "preferredAreaM2": 36,
  "preferredView": "SEA"
}
```

Response theo envelope `ApiResponse`, với `data.status`:

| Trạng thái | Ý nghĩa |
| --- | --- |
| `NEEDS_CLARIFICATION` | Thiếu/mơ hồ dữ liệu, cần tuổi trẻ em, yêu cầu nhiều phòng hoặc yêu cầu chưa có dữ liệu xác minh |
| `RECOMMENDED` | Có 1–3 loại phòng đã qua các điều kiện bắt buộc và kiểm tra lịch |
| `NO_MATCH` | Không có phương án hợp lệ; có mã nguyên nhân và gợi ý điều chỉnh, không tự nới điều kiện |
| `TEMPORARILY_UNAVAILABLE` | Còn ứng viên nhưng chưa thể xác minh lịch; không khẳng định hết phòng |

Response bao gồm `criteria`, `evaluatedAt`, `currency=VND`, `priceScope=ROOM_ONLY`, `scoringVersion=v1`, `recommendations`, `questions`, `rejections`, `suggestions`, `warnings`. Mỗi recommendation có `room` (giá và nightly breakdown của API search), `amenities`, `score`, `scoreBreakdown`, `reasons`, `unmetPreferences`. Dữ liệu đầu vào sai trả 400 qua domain exception hiện có.

## Ràng buộc bắt buộc

- Khoảng ở `[checkIn, checkOut)`, 1–365 đêm, theo đồng hồ/cutoff khách sạn. Một người lớn trở lên; tuổi trẻ em phải phù hợp ngưỡng `adultFromAge` tại ngày nhận phòng.
- Sức chứa người lớn, trẻ em và tổng khách kiểm tra đồng thời. Giường không quyết định capacity.
- `EXACT` đối chiếu toàn bộ cấu hình; `AT_LEAST` cho phép thêm giường/loại giường. Không suy giường từ chuỗi legacy hay ảnh. Twin = hai SINGLE. Giường chưa khai báo không thỏa điều kiện bắt buộc về giường.
- Amenities đối chiếu nhãn catalog thông qua dictionary alias rõ ràng trong `RecommendationAmenities`; chưa thay schema. Tên chưa nằm trong dictionary chỉ khớp chính nhãn đã chuẩn hóa, không suy diễn từ mô tả. Wi-Fi/TV/điều hòa là tiện nghi, không phải add-on. Có minibar không đồng nghĩa đồ uống miễn phí.
- `null` ở đặc điểm phòng nghĩa là chưa biết, không thỏa bắt buộc có/không có. `requiredView`, `requiredBalcony`, `requiredHighFloor`, `minAreaM2` được kiểm tra trước scoring.
- Ngân sách VND từ 0 đến 1.000 tỷ, chỉ tiền phòng: `TOTAL_STAY` so tổng; `PER_NIGHT` kiểm từng đêm; `AVERAGE_PER_NIGHT` chỉ dùng khi khách thực sự chọn trung bình. Không lấy base price hay nhân đơn giá thay giá động.
- Tồn dương theo đêm chưa đủ: cần xếp được một phòng liên tục với booking đang khóa/đang ở. Tất cả kiểm tra này chỉ là snapshot; transaction đặt phòng vẫn là thẩm quyền cuối.

## Điểm v1

`score = 100 × Σ(weight × fit) / Σ(weight)`, chỉ đưa những sở thích được khai báo vào mẫu số, làm tròn một chữ số thập phân.

| Thành phần | Trọng số | Fit trong [0,1] |
| --- | ---: | --- |
| Giá | 35 | Tiết kiệm: giá thấp nhất của tập hợp lệ / giá phương án. Ngân sách mềm: min(1, ngân sách / giá); nếu khai báo cả hai thì nhân hai hệ số. Giá bằng 0 xử lý riêng. |
| Giường ưu tiên | 20 | Trung bình tỷ lệ số lượng giường ưu tiên được đáp ứng, chặn tối đa 1 |
| Amenities ưu tiên | 20 | Số tiện nghi ưu tiên đáp ứng / số tiện nghi ưu tiên, loại trùng |
| Diện tích | 15 | min(1, diện tích / mục tiêu); chỉ nói rộng thì lấy diện tích lớn nhất trong tập hợp lệ làm mục tiêu |
| Đặc điểm/nhóm | 10 | Tỷ lệ hướng nhìn, ban công, tầng cao, nhóm phòng ưu tiên được đáp ứng |

Yêu cầu bắt buộc không cộng điểm. Không có sở thích: `score=null`, xếp theo tổng giá. Bằng điểm: giá tăng dần, rồi mã room type. Điểm không phải xác suất hài lòng. Ngân sách mềm bị vượt được ghi rõ tại `unmetPreferences`.

## Phạm vi và vận hành

- Chưa tối ưu tổ hợp nhiều phòng. Khi `roomCount > 1`, hỏi cách chia khách và hướng khách đến luồng đặt nhiều phòng.
- Yên tĩnh tuyệt đối, giường phụ, đặc điểm chưa có dữ liệu và ngân sách kèm dịch vụ được đưa vào `unsupportedRequirements` để hỏi làm rõ; không tự bỏ yêu cầu hoặc bịa xác nhận.
- Redis fallback có giới hạn 1.000 context và TTL, chỉ phù hợp một instance ứng dụng. Hội thoại được scoped theo tài khoản; đăng xuất/đổi tài khoản sẽ dựng lại widget để xóa thẻ cũ khỏi UI.
- Không migration mới, không đổi schema/seed, không nâng Spring AI/OpenAI model và không thêm một assistant độc lập.

## Kiểm thử

Backend có unit test cho lọc, scoring, ngân sách từng đêm, null/unknown, alias, phân trang, context SET/CLEAR/tuổi, structured tool execution và fallback SSE. Integration test dùng PostgreSQL Testcontainers riêng xác minh API đăng nhập, giá/tồn thật và số booking/allocation/plan không thay đổi. Hồi quy gồm search, pricing, stay inventory và assignment.

Frontend kiểm tra SSE payload, trạng thái xử lý, câu gợi ý, thẻ kết quả và truyền tuổi sang checkout. Không hiển thị điểm/trọng số; chọn thẻ gửi tin nhắn thay vì mở checkout trực tiếp. Browser test dùng mọi API giả lập, không tạo booking và không gọi OpenAI:

```sh
# client: chạy Vite trên cổng riêng, sau đó chạy bài kiểm tra
npm run dev -- --host 127.0.0.1 --port 5175 --strictPort
node scripts/room-recommendation-e2e.mjs
```

Có thể đặt `PLAYWRIGHT_CHROMIUM_EXECUTABLE` nếu máy đã cài browser ở đường dẫn riêng. Browser test xác minh năm lượt chat, so sánh, “phòng thứ hai”, chọn rõ ràng, desktop/mobile và checkout handoff. Các test mặc định không gọi OpenAI. `RoomConciergeLiveTest` chỉ chạy khi bật `-Dai.concierge.live=true`, dùng model thật với catalog giả lập và không kết nối database khách sạn. Test này kiểm tra một kịch bản hội thoại tiếng Việt, không thay thế đánh giá rộng trên nhiều cách diễn đạt.

Kiểm thử hồi quy cần bao gồm search, pricing, capacity, ngân sách từng đêm, assignment và checkout. Với test model thật, kiểm tra cả việc gọi đúng tool trong lượt hiện tại; một câu trả lời nghe hợp lý từ lịch sử chưa chứng minh dữ kiện đã được refresh.
