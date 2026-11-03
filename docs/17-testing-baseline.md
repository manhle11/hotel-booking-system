# 17. Inherited System Testing Baseline

## 1. Scope

This document records the two-member team's first reproducible test baseline
after the Java package rename. The baseline was captured on 2026-10-12 without
using credentials, callbacks or infrastructure belonging to the previous
five-member team.

The host had Java 21 and Node 26 as its defaults, so the verification commands
were run against read-only copies of the repository in temporary containers:

- Eclipse Temurin 17.0.20.1;
- Node.js 22.23.3 with npm 10.9.9.

No application stack was started and no persistent Docker volume was created
or deleted. A second backend run did use Docker Testcontainers; its temporary
PostgreSQL containers were cleaned up automatically.

## 2. Backend baseline

Equivalent project command:

```bash
cd hotel-booking
./mvnw --batch-mode clean verify
```

Result:

| Measurement | Without Docker socket | With Docker/Testcontainers |
| --- | ---: | ---: |
| Main source files compiled | 408 | 408 |
| Test source files compiled | 118 | 118 |
| Tests run | 608 | 608 |
| Failures | 0 | 0 |
| Errors | 0 | 0 |
| Skipped | 49 | 0 |
| Maven result | `BUILD SUCCESS` | `BUILD SUCCESS` |
| Total time | 2 min 26 s | 13 min 14 s |

The 49 tests skipped without a Docker socket require Testcontainers. They were
actually executed and passed in the second run. The test run used Java 17,
Docker socket access, and no external service credentials.

The Docker-enabled attempts exposed two missing test-only settings:
`payment.expiration-minutes` and disabling the OpenAI audio/image/moderation
auto-configurations in addition to chat/embedding. These were added to
`src/test/resources/application.properties`. No production secret was copied
into the test container. The successful run emitted background Hikari warnings
when a PostgreSQL Testcontainer had already stopped; they did not fail tests.

The backend requires the repository-level `docs/knowledge-base` directory at
test time. Running from a copy that contains only the `hotel-booking` module
causes `PolicyTestEmbeddingTest` to fail because its policy documents are not
available. The successful baseline therefore preserved the complete repository
layout.

## 3. Frontend baseline

Commands:

```bash
cd client
npm ci
npm run format:check
npm run lint
npm run test:run
npm run build
```

Result:

| Check | Actual result |
| --- | --- |
| `npm ci` | Completed |
| Prettier | Passed |
| ESLint | Passed |
| Test files | 49 passed |
| Tests | 231 passed |
| Production build | Passed |

Non-blocking observations:

- `npm ci` reported 12 known dependency vulnerabilities: 4 moderate and 8
  high. Exact advisories and production reachability were not classified, and
  no automatic dependency update was performed as part of this baseline.
- Tests logged Ant Design deprecation warnings and jsdom
  `getComputedStyle(..., pseudo-element)` limitations.
- The production build warned that the generated Axios chunk was 608.56 kB,
  above the default 500 kB warning threshold.

## 4. Docker Compose baseline

Commands:

```bash
docker compose --env-file .env.example config --quiet
docker compose --env-file .env.example config --services
```

The configuration is valid. Resolved services:

```text
postgres
redis
backend
frontend
minio
pgadmin
```

When starting a single local stack with a team-owned `.env` file, give it a
separate project name:

```bash
docker compose -p hotel-booking-two-member up -d
```

This project name alone does **not** make a second simultaneous stack safe:
`docker-compose.yml` also fixes container names, host ports, the network subnet,
and the frontend IP address. A parallel smoke test needs an override for all of
those values. The configuration-only checks above created no containers or
volumes; the existing local stack was left untouched.

Do not run `docker compose down -v` without explicit confirmation that the
project's persistent data may be deleted.

## 5. External integration boundary

This baseline did not supply credentials for OpenAI, Resend/SMTP, SePay,
external MinIO or VPS/deployment infrastructure. Future integration checks must
use local services or sandbox/test credentials owned by the current team.
The Docker build context now excludes the untracked `application-local.yml`,
so local-only configuration cannot be copied into the built image.

## Ghi chú cập nhật lưu trú03/11/2026

Số liệu/bước demo phía trên giữ ngữ cảnh lần ghi nhận, không đại diện kết quả334 ca UAT mới.
Quy tắc hiện tại xem [tài liệu23](23-stay-time-and-inventory.md):14:00 bắt đầu nhận, không chốt đặt;
cutoff 23:59:59 riêng, nhận sớm cần lý do, hôm qua đến trước 06, sau 12 trả cần note, trả sớm chỉ hôm nay,
tồn theo đêm gồm overdue, đúng expiredAt hết giữ. Kỳ demo cần sửa theo hotelToday của server.
Checklist đúng phần thay đổi: [16 luồng FE](testing/stay-time-fe-checklist.md), ban đầu Chưa chạy.
