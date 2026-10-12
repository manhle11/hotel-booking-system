# 20. Giai đoạn 5 — Backlog và phân công

## 1. Mục tiêu

Backlog này chỉ ghi nhận công việc mới trên nền hệ thống đã có. Không issue nào
được mô tả như việc xây lại Authentication, Booking, Payment, Operations,
Reporting hoặc AI từ đầu.

Baseline dùng để lập backlog:

- Backend đã có 408 source files, 118 test source files và 608 tests chạy qua
  Maven verify trên Java 17 với PostgreSQL Testcontainers.
- Frontend đã có 49 test files, 231 tests, lint, format check và production build
  đều đạt.
- Các module Authentication, User, Room/Pricing, Booking, Payment, Operations,
  Reporting và AI đều đã có code và test.
- Playwright đã là dev dependency nhưng chưa có cấu hình hoặc E2E suite.
- Backend chưa có JaCoCo coverage gate; frontend baseline còn dependency advisory
  và bundle-size warning cần được phân loại riêng, không tự động nâng dependency.

Chi tiết baseline có tại [17-testing-baseline.md](17-testing-baseline.md).

## 2. Quy ước workflow

GitHub Project dùng field `Workflow` và hiển thị dạng board, group theo field này:

```text
Backlog → Ready → In Progress → Review → Testing → Done
```

Definition of Ready:

- Hiện trạng và phạm vi thay đổi có bằng chứng từ repo.
- Acceptance criteria kiểm chứng được.
- Owner và dependency đã rõ.
- Không phụ thuộc credential hoặc hạ tầng của nhóm cũ.

Definition of Done:

- Acceptance criteria hoàn tất.
- Có test evidence thực tế; không ghi test bị skip là pass.
- Pull request dùng `Closes #<issue-number>` và đã được review.
- CI liên quan đạt; tài liệu/API contract được cập nhật nếu thay đổi.
- Issue và Project item đã chuyển qua `Testing` trước khi sang `Done`.

## 3. Phân công

| Epic           | Lê Trần Hữu Mạnh — Backend/AI                              | Hoàng Thị Ánh Nguyệt — Frontend               |
| -------------- | ---------------------------------------------------------- | --------------------------------------------- |
| Authentication | Audit JWT, refresh token, RBAC, rate limit, security tests | Audit auth UI, route guard, session handling  |
| User Profile   | Validation, authorization, API tests                       | Profile/user management UI                    |
| Room/Pricing   | API, JPA, price calculation, concurrency                   | Catalog/search/detail/admin UI                |
| Booking        | Booking state, cancellation, extension, race conditions    | Booking flow, history, status tracking        |
| Payment        | Webhook security, payment/refund state                     | Payment/result/refund UI                      |
| Operations     | Check-in/out, housekeeping, maintenance APIs               | Front-desk, housekeeping, maintenance screens |
| Reporting      | Metrics/query/API performance                              | Dashboard/chart/filter                        |
| AI             | RAG, vector store, function calling, recommendation        | AI assistant and recommendation UI            |
| Quality        | Backend unit/integration/security tests                    | UI/integration/E2E tests                      |
| Demo           | Backend services and deterministic data preparation        | Demo flow, UI polish, presentation            |

## 4. Backlog ưu tiên

| ID    | Epic           | Work item                                                      | Owner                | Priority | Initial workflow | Dependency                                 |
| ----- | -------------- | -------------------------------------------------------------- | -------------------- | -------- | ---------------- | ------------------------------------------ |
| P5-01 | Authentication | Audit refresh-token rotation, RBAC and rate-limit security     | Lê Trần Hữu Mạnh     | P0       | Ready            | —                                          |
| P5-02 | Authentication | Harden auth session, route guards and recovery UX              | Hoàng Thị Ánh Nguyệt | P0       | Ready            | P5-01 contract                             |
| P5-03 | User Profile   | Tighten profile authorization and validation tests             | Lê Trần Hữu Mạnh     | P1       | Ready            | —                                          |
| P5-04 | User Profile   | Complete profile and user-management UI states                 | Hoàng Thị Ánh Nguyệt | P1       | Backlog          | P5-03                                      |
| P5-05 | Room/Pricing   | Audit price calculation and concurrent rule updates            | Lê Trần Hữu Mạnh     | P0       | Ready            | —                                          |
| P5-06 | Room/Pricing   | Improve responsive room discovery and admin UI                 | Hoàng Thị Ánh Nguyệt | P1       | Backlog          | P5-05                                      |
| P5-07 | Booking        | Add PostgreSQL race tests for booking transitions              | Lê Trần Hữu Mạnh     | P0       | Ready            | —                                          |
| P5-08 | Booking        | Improve booking flow, history and status recovery              | Hoàng Thị Ánh Nguyệt | P0       | Backlog          | P5-07                                      |
| P5-09 | Payment        | Harden webhook idempotency and payment/refund states           | Lê Trần Hữu Mạnh     | P0       | Backlog          | P5-07                                      |
| P5-10 | Payment        | Improve payment result and refund recovery UI                  | Hoàng Thị Ánh Nguyệt | P1       | Backlog          | P5-09                                      |
| P5-11 | Operations     | Verify atomic check-in/out and room-operation transitions      | Lê Trần Hữu Mạnh     | P0       | Backlog          | P5-07, P5-09                               |
| P5-12 | Operations     | Unify front-desk, housekeeping and maintenance UX              | Hoàng Thị Ánh Nguyệt | P1       | Backlog          | P5-11                                      |
| P5-13 | Reporting      | Establish metric correctness and query performance budget      | Lê Trần Hữu Mạnh     | P1       | Ready            | —                                          |
| P5-14 | Reporting      | Improve dashboard filters, charts and accessibility            | Hoàng Thị Ánh Nguyệt | P1       | Backlog          | P5-13                                      |
| P5-15 | AI             | Automate RAG grounding and tool-authorization evaluation       | Lê Trần Hữu Mạnh     | P0       | Ready            | —                                          |
| P5-16 | AI             | Test assistant streaming, citations and recommendation actions | Hoàng Thị Ánh Nguyệt | P1       | Backlog          | P5-15                                      |
| P5-17 | Quality        | Add backend coverage gates and CI test evidence                | Lê Trần Hữu Mạnh     | P1       | Ready            | —                                          |
| P5-18 | Quality        | Add Playwright critical-flow E2E and accessibility checks      | Hoàng Thị Ánh Nguyệt | P0       | Ready            | Stable test data from P5-19 for full suite |
| P5-19 | Demo           | Make local demo data deterministic and resettable              | Lê Trần Hữu Mạnh     | P1       | Ready            | —                                          |
| P5-20 | Demo           | Build repeatable demo flow and final UI polish checklist       | Hoàng Thị Ánh Nguyệt | P1       | Backlog          | P5-12, P5-14, P5-16, P5-19                 |

Các issue body đầy đủ nằm trong [`.github/backlog/phase-5`](../.github/backlog/phase-5/).
File `manifest.tsv` là nguồn dữ liệu cho script khởi tạo, không phải bằng chứng rằng
issue đã tồn tại trên GitHub.

## 5. Cấu trúc bắt buộc của issue

Mỗi issue trong backlog có các phần sau:

1. `Hiện trạng` — nêu rõ code/test nào đã tồn tại và khoảng trống thực tế.
2. `Thay đổi dự kiến` — giới hạn phạm vi thay đổi mới.
3. `Acceptance criteria` — checklist có thể kiểm chứng.
4. `Người phụ trách` — một owner chính theo bảng phân công.
5. `Backend/frontend dependency` — issue phụ thuộc hoặc contract cần giữ.
6. `Test evidence` — lệnh/chứng cứ trước và sau thay đổi.
7. `Pull request` — URL PR; PR phải đóng issue bằng `Closes #...`.

Issue form cho công việc phát sinh thêm nằm tại
[`.github/ISSUE_TEMPLATE/phase-5-work-item.yml`](../.github/ISSUE_TEMPLATE/phase-5-work-item.yml).

## 6. Khởi tạo trên GitHub

Máy hiện tại chưa có GitHub CLI và chưa xác định GitHub username của Hoàng Thị
Ánh Nguyệt. Có thể kiểm tra file và xem trước kế hoạch mà không ghi lên GitHub,
kể cả khi chưa cài `gh`:

```bash
./scripts/create-phase-5-backlog.sh --dry-run
```

Khi đã cài `gh`, đăng nhập và biết username frontend, chạy lại `--dry-run` để
kiểm tra repository, khả năng assign hai tài khoản, Project và issue hiện có:

```bash
gh auth refresh -s project
FRONTEND_ASSIGNEE=<github-username> ./scripts/create-phase-5-backlog.sh --dry-run
FRONTEND_ASSIGNEE=<github-username> ./scripts/create-phase-5-backlog.sh
```

Trước khi ghi, script kiểm tra đủ 20 issue body, ID/title, owner, nhãn, workflow,
repository, hai assignee và cấu hình Project hiện có. `--dry-run` không tạo hoặc
chỉnh sửa dữ liệu GitHub. Nếu `gh` hoặc phiên đăng nhập chưa sẵn sàng, dry-run
chỉ kiểm tra dữ liệu cục bộ và thông báo rằng kiểm tra từ xa đã được bỏ qua.

Script khởi tạo theo hướng chạy lại an toàn:

- tạo label còn thiếu;
- tạo Project nếu chưa có;
- liên kết Project với repository nếu chưa liên kết;
- tạo field `Workflow` với sáu trạng thái yêu cầu;
- nhận diện issue bằng mã `[P5-XX]` ở đầu title và giữ nguyên body, assignee,
  labels cũng như title đã được chỉnh thủ công;
- thêm issue còn thiếu vào Project, chỉ gán trạng thái ban đầu cho item mới;
- giữ nguyên `Workflow` của item đã có, kể cả khi đang `In Progress` hoặc `Done`.

Script chỉ khởi tạo. Khi cần thay đổi issue đã bắt đầu, cập nhật trên GitHub và
ghi nhận trong PR; chỉnh file backlog cục bộ sẽ không ghi đè nội dung thủ công
trên GitHub.

Project là board cấp user/organization. Nếu owner của Project khác owner repo, đặt
`PROJECT_OWNER=<login>` khi chạy. Có thể đặt `PROJECT_NUMBER=<number>` để dùng một
Project đã tồn tại.

Sau lần chạy đầu, mở Project, chọn Board layout và group theo `Workflow` để nhận
đúng sáu cột. Script không thay đổi field `Status` mặc định của GitHub.

## 7. Nguyên tắc cập nhật

- Không đổi title/ID trong manifest sau khi issue đã tạo; dùng GitHub issue number
  làm liên kết chính thức.
- Khi phát hiện issue đã được code hiện tại giải quyết, đóng bằng bằng chứng hoặc
  thu hẹp phạm vi; không giữ issue chỉ để đủ số lượng.
- Không đưa secret, token, dữ liệu khách thật hoặc endpoint hạ tầng riêng vào
  issue, test evidence hay Pull Request.
- Mọi thay đổi scope/dependency phải được phản ánh đồng thời trong issue và
  Project item.
