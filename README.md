# UPI-Lite Engine

A production-minded UPI Lite clone built with **Java/Spring Boot + React**.  
This project demonstrates secure wallet payments, OTP auth, realtime alerts, and resilient transaction processing patterns expected in modern fintech systems.

## What This Project Demonstrates
- End-to-end auth with JWT + OTP login and OTP-based password reset.
- UPI Lite policy enforcement:
  - wallet balance cap: `Rs 2000`
  - PIN required for transactions `>= Rs 500`
  - sender identity derived from JWT (prevents sender spoofing).
- Transfer safety with idempotency keys and ledger entries.
- Async + realtime notifications using Kafka and SSE.
- Modern frontend UX: dashboard, transaction history filters, contacts, KYC flow, QR.

## Tech Stack

### Backend
- Java 21
- Spring Boot `4.0.2`
- Spring Security (stateless JWT)
- Spring Data JPA + PostgreSQL
- Apache Kafka
- Spring Mail (OTP email)
- ZXing (UPI QR generation)
- Server-Sent Events (SSE)

### Frontend
- React 18 + Vite
- Tailwind CSS
- Axios interceptors
- Framer Motion
- Lucide React

## Architecture Snapshot
- **Auth layer**: email/password + OTP login, JWT token issuance.
- **Payment core**: transfer + wallet credit with policy and validation rules.
- **Reliability layer**: idempotency records + ledger entries for retry safety and auditability.
- **Notification layer**:
  - Kafka for async dispatch.
  - SSE for instant UI alerts after committed transactions.
- **Presentation layer**: dashboard, contacts, history, QR, KYC pages.

## Implemented Features

### 1) User, Auth, and Identity
- Register/login with JWT.
- OTP endpoints:
  - `POST /api/users/login/otp/request`
  - `POST /api/users/login/otp/verify`
  - `POST /api/users/password/forgot/request`
  - `POST /api/users/password/forgot/reset`
- User profile endpoint with wallet and KYC metadata:
  - `GET /api/users/profile`

### 2) Wallet and Transfer Safety
- Wallet top-up:
  - `POST /api/wallet/credit`
- Transfer by UPI ID or mobile:
  - `POST /api/transactions/transfer`
- Business rules:
  - amount must be positive
  - receiver identifier must be exactly one of `receiverUpiId` or `receiverMobile`
  - PIN required for `>= Rs 500`
  - sender/receiver wallet balance checks and cap enforcement
  - sender identity from authenticated principal only
- Idempotency support via `Idempotency-Key` header for transfer and credit.

### 3) History, Notifications, and Realtime UX
- Legacy + paged history endpoints:
  - `GET /api/transactions/history/{userId}`
  - `GET /api/transactions/history/{userId}/paged`
  - `GET /api/transactions/history` (authenticated user)
- Filters for date range and `ALL | CREDIT | DEBIT`.
- Realtime stream:
  - `GET /api/notifications/stream` (SSE)
- Kafka producer/consumer notifications with non-blocking publish behavior.

### 4) Product Experience Extensions
- Contacts discovery and quick actions:
  - `GET /api/users/contacts`
  - `POST /api/users/contacts/message`
- KYC mock workflow:
  - `POST /api/users/kyc/submit`
  - `GET /api/users/kyc/status`
  - `POST /api/users/kyc/mock-approve`
- Personal QR generation:
  - `GET /api/qr/my-upi`

## Frontend Modules
- `Dashboard`: balance, quick actions, recent transactions, PIN setup, wallet credit.
- `Transactions`: paginated history with filters.
- `Contacts`: searchable in-app contacts with send/message actions.
- `QR`: fetch and render personal UPI QR.
- `KYC`: upload + status tracking.
- `Auth`: login/register + OTP-driven flows.

## Local Setup

### Prerequisites
- JDK 21
- Node.js 18+
- Docker + Docker Compose

### 1) Start Infra
```bash
docker-compose up -d
```

### 2) Configure Local Secrets
```bash
cp src/main/resources/application-local.example.properties src/main/resources/application-local.properties
```
Then fill real values in `application-local.properties` (this file is gitignored).

### 3) Run Backend
```bash
./mvnw spring-boot:run
```

### 4) Run Frontend
```bash
cd frontend
npm install
npm run dev
```

### 5) Open
- Frontend: `http://localhost:5173`
- Swagger: `http://localhost:8080/swagger-ui.html`

## Configuration Keys
Use environment variables or `application-local.properties` for secrets:
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `JWT_SECRET_KEY`, `JWT_EXPIRATION_MS`
- `KAFKA_BOOTSTRAP_SERVERS`
- `OTP_EXPIRY_MINUTES`, `OTP_MAX_ATTEMPTS`, `OTP_EMAIL_ENABLED`
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`

## Verification Commands
```bash
./mvnw -q -DskipTests compile
./mvnw -q test
cd frontend && npm run build
```

## Resume/ATS Highlights
- Designed a secure payment flow with policy enforcement and auth-derived sender identity.
- Implemented idempotent APIs for retry-safe payment and wallet operations.
- Built async + realtime notification channels (Kafka + SSE) for payment events.
- Added OTP-based login and password reset with email delivery support.
- Delivered full-stack product features (contacts, QR, KYC, history filters) with cohesive UX.

## Next High-Impact Upgrades
- Outbox pattern + DLQ + replay tools for stronger delivery guarantees.
- Double-entry balancing and reconciliation jobs.
- Fraud controls (velocity limits, anomaly checks, account risk scoring).
- Observability stack (metrics, tracing, alerting dashboards).
- CI/CD with automated security and dependency scanning.

---
This codebase is strong for 2026-level fresher/full-stack interviews because it combines **security, distributed systems thinking, product UX, and reliability engineering** in one coherent fintech project.
