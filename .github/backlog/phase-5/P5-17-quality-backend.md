# [P5-17] Add backend coverage gates and CI test evidence

## Hiện trạng

Backend baseline có 608 tests chạy thành công trên Java 17 với PostgreSQL
Testcontainers, nhưng `pom.xml` chưa có JaCoCo coverage report/gate. CI chạy Maven
verify nhưng chưa xuất test/coverage artifact rõ ràng, và Testcontainers tests có
thể bị skip khi Docker không sẵn sàng mà người đọc nhầm thành pass.

## Thay đổi dự kiến

Thêm coverage reporting/gate có baseline hợp lý, phân loại unit/integration/
security/concurrency tests, làm CI evidence dễ kiểm tra và fail rõ khi required
integration tests bị skip. Không đặt threshold tùy ý khiến đội viết test hình thức.

## Acceptance criteria

- [ ] JaCoCo report được tạo trong `verify`; baseline line/branch coverage theo package được ghi trước khi đặt threshold.
- [ ] Gate ưu tiên service/domain/security quan trọng và có policy exclude generated/config code được giải thích.
- [ ] Required PostgreSQL/Testcontainers suite fail rõ khi môi trường CI không chạy được, không silently report pass.
- [ ] CI upload Surefire và coverage reports khi pass/fail, với retention hợp lý và không chứa secret.
- [ ] Tài liệu chỉ rõ lệnh unit/full integration và cách đọc skipped count.
- [ ] Full suite, formatting và migration compatibility vẫn đạt trên Java 17.

## Người phụ trách

Lê Trần Hữu Mạnh — Backend/AI.

## Backend/frontend dependency

Độc lập về code frontend. P5-07/P5-09/P5-11/P5-15 cung cấp các test quan trọng
được tính trong gate; phối hợp để tránh merge threshold trước test bắt buộc.

## Test evidence

- Before: 608/608 tests pass với Docker, chưa có JaCoCo gate; 49 tests từng bị skip khi không có Docker.
- Required: `cd hotel-booking && ./mvnw --batch-mode clean verify`; đính kèm total/package coverage và CI artifact links.
- After: _Chưa có — cập nhật trong Pull Request._

## Pull Request

_Chưa có — PR phải chứa `Closes #<issue-number>`._
