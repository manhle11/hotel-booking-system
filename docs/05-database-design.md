# Database design as-built

Flyway trong `hotel-booking/src/main/resources/db/migration` là nguồn sự thật của database.

## Identity

- `users`: email, phone, password hash, full name, status và audit fields.
- `roles`: `ADMIN`, `MANAGER`, `RECEPTIONIST`, `HOUSEKEEPER`.
- `user_roles`: quan hệ staff role; tài khoản khách không có bản ghi tại đây.
- `refresh_tokens`: user, token hash, created/expired/revoked timestamps.

## Room và pricing

- `room_types`: code, base price, `capacity_adults`, `capacity_children`, `max_guests`, status.
- `room_type_images`: ảnh theo thứ tự.
- `rooms`: phòng vật lý và trạng thái.
- `price_rules`: rule phụ thu/khuyến mãi theo khoảng ngày và priority.

## Booking

- `bookings`: code, booked_by nullable, room type, ngày DATE, adult/child count, status, total; `expired_at`, `checked_in_at`, `checked_out_at` dạng TIMESTAMPTZ sau migration V202611031800.
- `booking_guests`: thông tin khách snapshot.
- `booking_nights`: giá snapshot từng đêm.
- `booking_night_price_rules`: các rule đã áp dụng cho từng đêm.
- `booking_status_histories`: lịch sử trạng thái.
- `add_on_services`: catalog dịch vụ active và đơn giá hiện tại.
- `booking_add_ons`: `service_id`, `quantity`, `unit_price`, `total_price`; đây là snapshot không thay đổi khi catalog đổi giá.

Booking status hiện có: `PENDING`, `CONFIRMED`, `CANCELLED`, `CHECKED_IN`, `CHECKED_OUT`, `EXPIRED`.

## Business constraints

- `check_out_date > check_in_date`.
- Adults, children và tổng khách phải nằm trong ba capacity của room type.
- Child `<= 12` miễn phí tiền phòng trong MVP nhưng vẫn chiếm occupancy.
- Add-on quantity từ 1 đến 20 trên API; tổng add-on là `unit_price × quantity`.
- Một service chỉ xuất hiện một lần trong một booking.
- `add_on_services.code` là mã nghiệp vụ duy nhất và không thay đổi sau khi tạo.
- Dịch vụ không còn cung cấp được chuyển `active = false`, không xóa vật lý nếu đã được booking tham chiếu.
- Thay đổi tên, mô tả, giá hoặc trạng thái catalog không làm thay đổi `unit_price` và `total_price` đã snapshot trong `booking_add_ons`.
- Tính từng đêm [nhận,trả); PENDING có expired_at > now và CONFIRMED/CHECKED_IN chiếm tồn; lấy nhu cầu đỉnh. CHECKED_IN quá hạn vẫn chặn tồn về sau theo policy, không tự kết thúc.
- Housekeeping MVP dùng trực tiếp state machine của `rooms`: `DIRTY -> CLEANING -> AVAILABLE`.
  Không tạo bảng task/phân công vì chức năng đó không nằm trong yêu cầu hiện tại.

## Reporting

Định nghĩa query và công thức cho Manager dashboard được mô tả tại
[financial reporting and maintenance](financial-reporting-and-maintenance.md). Reporting sử dụng
`payment_transactions` cho doanh thu, `booking_nights` cho occupied room nights và
`bookings.created_at` cho thống kê booking.
# Policy RAG vector storage

Migration `V202608301600` bật extension `vector`, tạo `policy_vector_store` theo schema tương thích
Spring AI (`id text`, `content text`, `metadata json`, `embedding vector(1536)`) và HNSW cosine index.
Dimension 1536 khớp production model `text-embedding-3-small`; thay model/dimension cần migration mới.
`policy_knowledge_ingestion` lưu SHA-256 checksum, số chunk và thời điểm ingest để re-ingestion
idempotent. Đây là demo policy corpus, không phải dữ liệu chính sách của khách sạn đang vận hành.
Migration `V202609041200` adds a complete-or-null bank-transfer destination to the existing `payment_refunds` table. OTP, PIN, CVV and banking credentials are not collected.

## Migration mốc lưu trú thực tế

`V202611031800__record_actual_stay_times.sql` thêm checked_in_at/checked_out_at,
chuyển expired_at từ timestamp legacy theo Asia/Ho_Chi_Minh sang TIMESTAMPTZ,
thêm `idx_active_booking_nights` trên room_type_id/check_in_date/check_out_date cho active statuses.
Không điền giờ giả cho booking cũ. DATE ở khác instant nhận/trả thực tế;
[quy tắc hiện tại](23-stay-time-and-inventory.md) mô tả biên giờ và tồn.
