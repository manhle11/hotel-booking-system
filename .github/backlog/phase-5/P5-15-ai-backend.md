# [P5-15] Automate RAG grounding and tool-authorization evaluation

## Hiện trạng

AI đã có policy knowledge base, pgvector retrieval, unified chat, time/date
normalization, function tools, request logging và 11 nhóm backend tests. Repo có
`rag-test-cases.md`, nhưng chưa có automated evaluation gate đo retrieval/grounding,
prompt-injection resistance và service-level authorization cho toàn bộ tool matrix.

## Thay đổi dự kiến

Chuyển test cases ổn định thành deterministic evaluation không cần live OpenAI;
audit RAG ingestion/retrieval, tool allowlist/ownership và logging privacy; thêm
integration tests với fake model/embedding và pgvector nơi cần.

## Acceptance criteria

- [ ] Knowledge source có stable ID/version/checksum; re-index idempotent và xóa tài liệu cũ đúng.
- [ ] Evaluation set đo retrieval hit/grounded answer/no-evidence behavior với threshold được ghi, không gọi live API trong CI.
- [ ] Prompt injection trong user text hoặc retrieved document không thể mở rộng tool/role/ownership.
- [ ] Mỗi function tool lấy identity từ security context và test anonymous/wrong-owner/wrong-role/success.
- [ ] Tool mutation yêu cầu xác nhận theo contract; duplicate/replayed action không tạo side effect ngoài ý muốn.
- [ ] Prompt, log, trace và error không lộ token, full PII hoặc dữ liệu booking của user khác.

## Người phụ trách

Lê Trần Hữu Mạnh — Backend/AI.

## Backend/frontend dependency

P5-16 cần streaming event, citation/source, confirmation action và error contract.
Không để frontend là lớp authorization duy nhất cho function calling.

## Test evidence

- Before: AI controller/contract, retriever/pgvector, tools, logger và policy embedding tests hiện có.
- Required: `cd hotel-booking && ./mvnw --batch-mode clean verify` không live credentials; đính kèm evaluation summary và tool authorization matrix.
- After: _Chưa có — cập nhật trong Pull Request._

## Pull Request

_Chưa có — PR phải chứa `Closes #<issue-number>`._
