# [P5-13] Establish metric correctness and query performance budget

## Hiện trạng

Reporting đã có dashboard API, JDBC aggregate/daily queries cho revenue,
occupancy, bookings và maintenance, cùng controller/repository/service tests. Một
số query group/cast timestamp theo date và chưa có benchmark fixture, EXPLAIN plan
evidence hoặc performance budget để phát hiện regression khi dữ liệu lớn.

## Thay đổi dự kiến

Xác minh định nghĩa metric/time boundary với dữ liệu fixture, đo query plan trên
PostgreSQL, thêm index/query refactor khi có bằng chứng và thiết lập test/budget
có thể tái lập. Không tối ưu theo phỏng đoán hoặc đổi nghĩa metric để đạt tốc độ.

## Acceptance criteria

- [ ] Revenue/refund/occupancy/booking/maintenance definitions có fixture kiểm tra boundary đầu/cuối ngày `Asia/Ho_Chi_Minh`.
- [ ] Cancellation/refund/room out-of-service cases được phản ánh đúng theo business definition đã ghi.
- [ ] Có script hoặc integration fixture tạo dataset đủ lớn, kèm `EXPLAIN (ANALYZE, BUFFERS)` trước/sau cho query trọng yếu.
- [ ] Index/query change có Flyway migration tương thích và không phá migration-from-zero test.
- [ ] Performance budget được ghi theo môi trường tham chiếu và tránh assertion thời gian quá mong manh trong CI.
- [ ] API pagination/date-range validation chặn query range không hợp lý và có test security/RBAC.

## Người phụ trách

Lê Trần Hữu Mạnh — Backend/AI.

## Backend/frontend dependency

P5-14 cần metric definitions, date-range limit và response contract. Nếu thêm
metric mới, cập nhật OpenAPI trước khi frontend tích hợp.

## Test evidence

- Before: `DashboardReportControllerTest`, `DashboardMetricRepositoryIntegrationTest`, `DashboardReportServiceImplTest`.
- Required: `cd hotel-booking && ./mvnw --batch-mode clean verify`; đính kèm fixture size, query plans và migration test.
- After: _Chưa có — cập nhật trong Pull Request._

## Pull Request

_Chưa có — PR phải chứa `Closes #<issue-number>`._
