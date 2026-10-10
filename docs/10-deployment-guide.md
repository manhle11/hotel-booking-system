# 10. Local Development Guide

Tài liệu này chỉ mô tả cách build, kiểm thử và chạy HANBAY HOTEL
trên máy phát triển. Repository hiện không chứa quy trình triển khai
production; quy trình đó sẽ được thiết kế lại khi dự án sẵn sàng.

## 1. Yêu cầu

- Java 17.
- Node.js 22 và npm.
- Docker Engine và Docker Compose.
- Git.

## 2. Cấu trúc liên quan đến local

```text
hotel-booking-system/
├── hotel-booking/          # Spring Boot backend
├── client/                 # React frontend
├── docs/                   # Tài liệu và knowledge base
├── .env.example            # Mẫu biến môi trường local
├── docker-compose.yml      # Full local stack
└── README.md
```

Hai `Dockerfile` và `client/nginx.conf` được dùng bởi full local stack,
không phải cấu hình VPS.

## 3. Chuẩn bị biến môi trường

Từ thư mục gốc:

```bash
cp .env.example .env
```

Điền các trường bắt buộc còn trống trong `.env`, đặc biệt là mật
khẩu PostgreSQL/pgAdmin, JWT secret và mật khẩu admin local. Chỉ thêm
API key của nhà cung cấp khi cần kiểm thử tính năng tương ứng.

Không commit `.env`, `client/.env.local` hoặc
`hotel-booking/src/main/resources/application-local.yml`.

## 4. Chạy full local stack

```bash
docker compose build backend frontend
docker compose up -d
docker compose ps
```

Các endpoint local:

- Frontend: `http://localhost:5173`.
- Backend health: `http://localhost:8080/actuator/health`.
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`.
- pgAdmin: `http://localhost:5050`.
- MinIO API/Console: `http://localhost:9000` / `http://localhost:9001`.

Flyway tự chạy khi backend khởi động. Migration nằm trong
`hotel-booking/src/main/resources/db/migration`.

Dừng stack mà không xóa dữ liệu local:

```bash
docker compose down
```

Chỉ dùng `docker compose down --volumes` khi chấp nhận xóa toàn bộ dữ
liệu PostgreSQL, Redis, MinIO và pgAdmin trên máy local.

## 5. Phát triển frontend với Vite

Có thể chạy backend và hạ tầng bằng container, sau đó thay frontend
container bằng Vite:

```bash
docker compose up -d postgres redis minio backend
cd client
npm ci
npm run dev
```

`client/.env.development` cấu hình base URL cho môi trường phát triển.

## 6. Build và kiểm thử backend

```bash
cd hotel-booking
./mvnw --batch-mode clean verify
```

Kiểm tra format riêng:

```bash
./mvnw spotless:check
```

Backend CI chạy cùng Maven verify với PostgreSQL 17 dành riêng cho test.

## 7. Build và kiểm thử frontend

```bash
cd client
npm ci
npm run format:check
npm run lint
npm run test:run
npm run build
```

## 8. Cấu hình dịch vụ

- PostgreSQL, Redis, MinIO và pgAdmin trong `docker-compose.yml` chỉ phục vụ
  môi trường local.
- Email và AI dùng biến môi trường; có thể để trống credential
  nếu không kiểm thử tích hợp tương ứng.
- Webhook SePay dùng local/example key. Không được dùng credential thật
  trong file `.http` hoặc file được Git theo dõi.
- `application-prod.yml` hiện được backend container trong local Compose
  sử dụng. Không xóa hoặc đổi tên file này nếu chưa thay profile của
  local stack.
