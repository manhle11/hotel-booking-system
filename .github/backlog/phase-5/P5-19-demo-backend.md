# [P5-19] Make local demo data deterministic and resettable

## Hiện trạng

Backend có local-profile data initializers cho room types/rooms/amenities và các
module khác. Một số seed chỉ chạy khi table trống, dùng random UUID hoặc reflection,
nên kết quả có thể phụ thuộc database đã có và khó dùng làm repeatable E2E/demo.
Chưa có một lệnh reset/seed an toàn, có scope riêng cho dữ liệu demo.

## Thay đổi dự kiến

Refactor local/demo seed thành dataset deterministic, idempotent và có version;
cung cấp reset procedure chỉ cho profile/database demo; chuẩn bị đầy đủ các state
cần trình diễn mà không chứa PII/credential thật.

## Acceptance criteria

- [ ] Dataset có stable IDs/codes và đủ user roles, rooms, bookings, payments, operations, reporting và AI examples cho demo script.
- [ ] Seed chạy hai lần cho cùng kết quả, không duplicate và không phụ thuộc table phải hoàn toàn trống.
- [ ] Không dùng reflection để gán entity field khi có factory/builder/fixture rõ ràng hơn.
- [ ] Reset command từ chối chạy ngoài local/demo/test profile và không xóa Docker volume hay database không đúng tên/marker.
- [ ] Demo credentials là giá trị local/example được ghi rõ, không tái dùng secret thật; log không in password/token.
- [ ] Có smoke/integration test cho seed-from-empty, reseed và representative state counts.

## Người phụ trách

Lê Trần Hữu Mạnh — Backend/AI.

## Backend/frontend dependency

P5-18 dùng dataset cho E2E; P5-20 dùng các stable codes/scenarios cho demo flow.
Phối hợp expected states với Payment/Operations/Reporting/AI owners trước khi khóa
dataset version.

## Test evidence

- Before: local initializers hiện có; baseline không khởi động full application stack bằng demo credentials.
- Required: seed empty → verify → reseed → verify cùng counts/checksums; chạy backend full tests và Docker smoke theo hướng dẫn an toàn.
- After: _Chưa có — cập nhật trong Pull Request._

## Pull Request

_Chưa có — PR phải chứa `Closes #<issue-number>`._
