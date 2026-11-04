# HANBAY HOTEL

Smart Hotel Reservation & Property Management System.

HANBAY HOTEL is a full-stack hotel management application covering
online reservation, room operations, payment, housekeeping and hotel
administration.

## Project status

The project is currently under active development.

The repository currently has no production deployment workflow or VPS-specific
configuration. Deployment will be designed again when the project reaches its
final stage; the checked-in containers are for local development and verification.

The current two-member team maintains and extends a codebase inherited from an
earlier five-member project. The inherited code is not presented as a rewrite by
the current team. See [Project provenance](docs/provenance.md) for ownership and
legacy-document details.

## Current team

- Lê Trần Hữu Mạnh — Backend and AI.
- Hoàng Thị Ánh Nguyệt — Frontend.

Historical author names remain in legacy artifacts for attribution; they do not
describe current project ownership.

## Technology stack

### Backend

- Java 17
- Spring Boot
- Spring MVC
- Spring Security
- Spring Data JPA
- PostgreSQL
- Flyway
- Maven

### Frontend

- React
- TypeScript
- Vite
- React Router
- TanStack Query
- Axios
- Ant Design
- React Hook Form
- Zod

### Development and CI

- Docker Compose
- GitHub Actions
- PlantUML
- Vitest
- Maven Test

## Repository structure

```text
hotel-booking-system/
├── client/                 # React and TypeScript frontend
├── hotel-booking/          # Spring Boot backend
├── docs/                   # Project documentation
├── docs/diagrams/          # PlantUML diagrams
├── .github/workflows/      # Frontend and backend CI
└── README.md
```

## Run the complete Docker stack

Create the local environment file and provide non-placeholder secrets/API keys:

```bash
cp .env.example .env
docker compose build backend frontend
docker compose up -d
docker compose ps
```

Never commit `.env`, `client/.env.local`,
`hotel-booking/src/main/resources/application-local.yml`, credentials, tokens or
real customer data. The tracked environment and HTTP files contain only empty
secret fields or explicit local/example values. See
[Security review](docs/security-review.md) for the repository baseline.

The packaged application is available at:

- Frontend: `http://localhost:5173`
- Backend health: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- pgAdmin: `http://localhost:5050`

The frontend image serves the compiled Vite bundle through local Nginx. Nginx proxies
`/api` and `/actuator` to the backend container, including unbuffered SSE responses
for the AI assistant.

Stop the stack without deleting persisted database data:

```bash
docker compose down
```

Do not use `docker compose down --volumes` unless the PostgreSQL, Redis, MinIO and
pgAdmin data may be permanently discarded.

## Stay time, inventory and manual verification

Current rules and configuration: [Stay time and inventory](docs/23-stay-time-and-inventory.md).
14:00 is the start of standard check-in, not the last time to book that day.
A same-day booking at19:55 is allowed before the configured23:59:59 cutoff when inventory permits.
The payment hold deadline is separate; availability uses the lowest remaining stock across nights.

- [16 FE flows for the latest changes](docs/testing/stay-time-fe-checklist.md)
- [Full UAT guide and workbook](docs/testing/web-uat-guide.md) —334 designed cases, initially not run.
- [Public stay-policy/search API contract](docs/openapi/room/stay-policy-and-availability.yaml)
- [Đặt nhiều phòng trong một đơn](docs/24-multi-room-booking.md) — luồng khách hàng, API, giữ phòng và thanh toán chung.
