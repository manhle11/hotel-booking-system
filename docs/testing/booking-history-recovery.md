# Kiểm tra lịch sử đặt phòng và liên kết đơn khách vãng lai

## Token đăng nhập hết hạn

1. Đăng nhập bằng tài khoản khách, chọn phòng và nhập thông tin liên hệ.
2. Chờ access token hết hạn trong khi refresh token còn hiệu lực, rồi gửi yêu cầu giữ phòng.
3. Request tạo đơn đầu tiên phải trả `401 AUTH_UNAUTHORIZED` trước khi gọi service tạo đơn. Frontend refresh và gửi lại cùng nội dung, `Idempotency-Key` và `X-Booking-Access-Token`.
4. Đơn được tạo một lần, `booked_by` của đơn và mọi booking con phải bằng tài khoản đã đăng nhập; đơn xuất hiện trong `/account/bookings`.
5. Nếu refresh thất bại, hiển thị lỗi đăng nhập; request thất bại không tạo đơn khách vãng lai.
6. Request hoàn toàn không có access token vẫn được phép đặt phòng dạng khách vãng lai.

## Lưu đơn khách vãng lai vào lịch sử

1. Khi chưa đăng nhập, tạo đơn từ hai phòng trở lên. Có thể thanh toán trước khi lưu vào tài khoản.
2. Trên cùng tab, mở lại trang đơn, bấm **Đăng nhập để lưu đơn**. Sau đăng nhập, phải trở lại đúng trang đơn.
3. Bấm **Lưu đơn vào lịch sử**. Request `POST /api/v1/booking-orders/{id}/claim` phải có JWT và `X-Booking-Access-Token` của đơn.
4. Đơn và toàn bộ booking con có cùng `booked_by`; số tiền, payment và trạng thái phòng giữ nguyên. Thông báo lưu biến mất và lịch sử được tải lại.
5. Tải lại trang hoặc đăng nhập lại trên trình duyệt khác: tài khoản xem được đơn mà không cần token khách vãng lai.
6. Gửi lặp request claim của cùng tài khoản và token phải thành công, không tạo đơn hoặc giao dịch mới.

## Quyền truy cập

- Chưa đăng nhập: claim trả `401`.
- Đã đăng nhập nhưng thiếu header token: trả `400`; token sai: trả `404`, không liên kết đơn hoặc phòng nào.
- Chỉ có email liên hệ trùng tài khoản: không đủ để claim.
- Đơn đã thuộc tài khoản khác: token của đơn cũng không cho phép chuyển quyền sở hữu; claim trả `404`.
- Nút lưu chỉ hiện khi backend trả `claimable = true`; lỗi claim phải giữ thông báo và nút để khách thử lại.

Kiểm thử tự động: `BookingOrderApiTest`, `BookingOrderIntegrationTest`, `SecurityIntegrationTest`, `BookingHistoryApiTest`, `BookingPaymentSecurityIntegrationTest`; frontend: `booking-order-api.test.ts`, `booking-order-page.test.tsx`, `axios.test.ts`, `post-login-redirect.test.ts`.
