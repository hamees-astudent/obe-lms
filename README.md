# LMS — Learning Management System

A full-stack Learning Management System built with **Spring Boot 3.3** (backend) and **React 18 + Vite 5** (frontend), packaged as a single deployable JAR.

---

## Table of Contents

1. [Tech Stack](#tech-stack)
2. [Prerequisites](#prerequisites)
3. [Infrastructure Setup](#infrastructure-setup)
4. [Environment Variables](#environment-variables)
5. [Default Login Credentials](#default-login-credentials)
6. [Running in Development](#running-in-development)
7. [Building for Production](#building-for-production)
8. [Project Structure](#project-structure)
9. [API Overview](#api-overview)
10. [Observability](#observability)

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend framework | Spring Boot 3.3.0, Spring Modulith 1.2 |
| Language | Java 21 |
| Build tool | Maven 3.9+ |
| Database | PostgreSQL 15+ with Flyway migrations |
| Cache / sessions | Redis 7+ |
| Messaging | Apache Kafka (Spring Cloud Stream) |
| Object storage | MinIO (S3-compatible) |
| Security | Spring Security + JJWT 0.12 (JWT access + refresh tokens) |
| Frontend | React 18, Vite 5, TypeScript 5, Tailwind CSS |
| State management | Zustand 5, TanStack Query 5 |
| E2E tests | Playwright (see `lms-e2e/`) |

---

## Prerequisites

Install the following before proceeding:

| Tool | Minimum version | Notes |
|---|---|---|
| Java (JDK) | 21 | [Adoptium](https://adoptium.net/) recommended |
| Maven | 3.8+ | Required to generate the Maven wrapper on first checkout |
| Node.js | 20 LTS | Only needed for frontend standalone dev |
| PostgreSQL | 15 | |
| Redis | 7 | |
| Apache Kafka | 3.6 | With Zookeeper **or** KRaft mode |
| MinIO | latest | Any S3-compatible endpoint works |

> **Tip — quick infrastructure with Docker:**
> If you have Docker installed you can spin up all four services without installing them locally:
> ```bash
> docker run -d --name lms-postgres -e POSTGRES_USER=lms_user -e POSTGRES_PASSWORD=lms_password \
>   -e POSTGRES_DB=lms_db -p 5432:5432 postgres:15
>
> docker run -d --name lms-redis -p 6379:6379 redis:7
>
> docker run -d --name lms-minio \
>   -e MINIO_ROOT_USER=minioadmin -e MINIO_ROOT_PASSWORD=minioadmin \
>   -p 9000:9000 -p 9001:9001 minio/minio server /data --console-address ":9001"
>
> # Kafka (KRaft single-node — no Zookeeper)
> docker run -d --name lms-kafka -p 9092:9092 \
>   -e KAFKA_PROCESS_ROLES=broker,controller \
>   -e KAFKA_NODE_ID=1 \
>   -e KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
>   -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
>   -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
>   -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
>   -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
>   apache/kafka:3.7.0
> ```

---

## Generating the Maven Wrapper

The `./mvnw` wrapper script is not committed to the repository. Generate it once after cloning:

```bash
# Install Maven if not already present (Debian / Ubuntu)
sudo apt-get install -y maven

# Generate mvnw, mvnw.cmd, and .mvn/ inside lms-backend/
cd lms-backend
mvn wrapper:wrapper
```

All subsequent commands can then use `./mvnw` from the `lms-backend/` directory without requiring a system Maven installation.

---

## Infrastructure Setup

### PostgreSQL

```sql
-- Run once as superuser
CREATE USER lms_user WITH PASSWORD 'lms_password';
CREATE DATABASE lms_db OWNER lms_user;
GRANT ALL PRIVILEGES ON DATABASE lms_db TO lms_user;
```

Flyway applies all schema migrations automatically on first startup — no manual SQL needed.

### MinIO bucket

After starting MinIO, create the storage bucket once:

```bash
# Using the MinIO client (mc)
mc alias set local http://localhost:9000 minioadmin minioadmin
mc mb local/lms-files
```

Alternatively use the MinIO web console at `http://localhost:9001`.

---

## Environment Variables

The application reads configuration from environment variables with safe defaults (see `src/main/resources/application.yml`). Override any of the following for non-default setups:

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `lms_db` | Database name |
| `DB_USERNAME` | `lms_user` | Database user |
| `DB_PASSWORD` | `lms_password` | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | *(empty)* | Redis password (leave blank if none) |
| `KAFKA_BROKERS` | `localhost:9092` | Comma-separated Kafka broker list |
| `MINIO_ENDPOINT` | `http://localhost:9000` | MinIO / S3 endpoint URL |
| `MINIO_ACCESS_KEY` | `minioadmin` | MinIO access key |
| `MINIO_SECRET_KEY` | `minioadmin` | MinIO secret key |
| `MINIO_BUCKET` | `lms-files` | Storage bucket name |
| `JWT_SECRET` | *(insecure default)* | **Must be overridden in production** — 32+ char random string |
| `JWT_EXPIRATION_MS` | `86400000` | Access token TTL (ms) — default 24 h |
| `JWT_REFRESH_MS` | `604800000` | Refresh token TTL (ms) — default 7 d |
| `MAIL_HOST` | `smtp.gmail.com` | SMTP host for email notifications |
| `MAIL_PORT` | `587` | SMTP port |
| `MAIL_USERNAME` | *(empty)* | SMTP username |
| `MAIL_PASSWORD` | *(empty)* | SMTP password |
| `SERVER_PORT` | `8080` | HTTP port the application listens on |
| `MANAGEMENT_PORT` | `8081` | Actuator / metrics port |
| `ATTENDANCE_THRESHOLD` | `75` | Attendance warning threshold (%) |

Export variables in your shell, or create a `.env` file and source it before running Maven:

```bash
export DB_PASSWORD=secret
export JWT_SECRET=my-very-long-random-secret-key-here
```

---

## Default Login Credentials

The database starts empty. Before logging in for the first time, create the initial admin user by running the following SQL against `lms_db`:

```sql
INSERT INTO users (id, email, password_hash, name, role, status)
VALUES (
    gen_random_uuid(),
    'admin@lms.local',
    '$2y$10$IA093FHbo6QB6pygsMKw7O7.RfboT7b3Ac1Hq7dONL3QIk6jhA/0C',
    'System Admin',
    'ADMIN',
    'ACTIVE'
);
```

| Field    | Value             |
|----------|-------------------|
| Email    | `admin@lms.local` |
| Password | `admin123`        |
| Role     | `ADMIN`           |

Use these credentials at `POST /api/auth/login`:

```json
{ "email": "admin@lms.local", "password": "admin123" }
```

> **Security:** Change the password immediately in any non-local environment.  
> To generate a fresh bcrypt hash for a new password run:
> ```bash
> htpasswd -bnBC 10 "" <new-password> | tr -d ':\n'
> ```

---

## Running in Development

### Option A — backend only (skip React build)

Use this when you are working on backend code and want fast restart cycles. The frontend is not rebuilt; Vite serves it separately (see below).

```bash
cd lms-backend
./mvnw spring-boot:run -Dskip.frontend=true
```

The API is available at `http://localhost:8080/api`.

> **Port conflict:** If port 8080 is already in use (e.g. by another Docker container), the app will fail to start with `"Port 8080 was already in use"`. Either stop the conflicting process or start the app on a different port:
> ```bash
> ./mvnw spring-boot:run -Dskip.frontend=true -Dspring-boot.run.arguments=--server.port=8081
> # or export SERVER_PORT=8081 before running
> ```

### Option B — frontend dev server (hot-reload)

Run Vite alongside the Spring Boot server. Vite proxies API requests to port 8080.

```bash
cd lms-backend/src/main/frontend
npm install        # first time only
npm run dev
```

Frontend hot-reload is available at `http://localhost:5173`.

### Option C — full stack together

Build the React app into `src/main/resources/static/` and start Spring Boot so it serves everything on port 8080:

```bash
cd lms-backend
./mvnw spring-boot:run
```

The first run downloads Node 20 and npm 10 automatically via the `frontend-maven-plugin` — no manual Node installation required for this path.

---

## Building for Production

```bash
cd lms-backend
./mvnw clean package -DskipTests
```

This:
1. Runs `npm install && npm run build` inside `src/main/frontend/` (via `frontend-maven-plugin`)
2. Copies the Vite output into `src/main/resources/static/`
3. Compiles the Java source and packages everything into a single fat JAR

The output JAR is at:

```
lms-backend/target/lms-backend-0.0.1-SNAPSHOT.jar
```

Run it:

```bash
java -jar target/lms-backend-0.0.1-SNAPSHOT.jar \
  --DB_PASSWORD=secret \
  --JWT_SECRET=change-me
```

---

## Project Structure

```
lms-backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/lms/
    │   │   ├── config/              # Security, Redis, Jackson, Async config
    │   │   ├── shared/              # Cross-module DTOs, events, exceptions
    │   │   └── modules/
    │   │       ├── auth/            # JWT login, refresh, logout, password reset
    │   │       ├── users/           # User CRUD, admin user management
    │   │       ├── programs/        # Academic programs, PLOs, semesters
    │   │       ├── courses/         # Course catalog, CLOs, offerings, materials
    │   │       ├── enrollment/      # Student enrollment per offering
    │   │       ├── assessment/      # Assignments, quizzes, submissions, grading
    │   │       ├── attendance/      # Sessions, attendance records, summaries
    │   │       ├── files/           # File uploads via MinIO, pre-signed URLs
    │   │       ├── notifications/   # In-app notifications (Kafka-driven)
    │   │       ├── transcript/      # GPA calculation, transcript generation, PDF export
    │   │       └── users/           # User profiles
    │   └── resources/
    │       ├── application.yml      # All configuration with env-var placeholders
    │       └── db/migration/        # Flyway SQL migrations (V0001 – V0017)
    └── test/
        └── java/com/lms/           # Unit and integration tests

lms-e2e/                            # Playwright E2E tests (see lms-e2e/README.md)
```

---

## API Overview

All REST endpoints are served under `/api`. Authentication uses `Authorization: Bearer <access_token>`.

| Module | Base path | Roles |
|---|---|---|
| Auth | `/api/auth` | Public |
| Users (admin) | `/api/admin/users` | ADMIN |
| Programs | `/api/admin/programs` | ADMIN |
| Courses | `/api/admin/courses`, `/api/courses` | ADMIN / any |
| Offerings | `/api/admin/offerings`, `/api/offerings` | ADMIN / any |
| Enrollment | `/api/admin/enrollments` | ADMIN |
| Attendance | `/api/sessions`, `/api/offerings/{id}/sessions` | TEACHER+ |
| Assessment | `/api/offerings/{id}/assignments`, `/api/offerings/{id}/quizzes` | TEACHER+ |
| Files | `/api/files` | Authenticated |
| Notifications | `/api/notifications` | Authenticated (own) |
| Transcripts | `/api/transcripts`, `/api/admin/transcripts` | ADMIN / STUDENT (own) |
| Grading Scales | `/api/admin/grading-scales` | ADMIN |
| Me | `/api/me/*` | Authenticated (own data) |

---

## Observability

The management endpoints run on port **8081** (configurable via `MANAGEMENT_PORT`):

| Endpoint | Description |
|---|---|
| `GET :8081/actuator/health` | Liveness + readiness probes |
| `GET :8081/actuator/health/liveness` | Kubernetes liveness probe |
| `GET :8081/actuator/health/readiness` | Kubernetes readiness probe (checks DB, Redis, disk) |
| `GET :8081/actuator/metrics` | All registered metrics |
| `GET :8081/actuator/prometheus` | Prometheus scrape endpoint |
| `GET :8081/actuator/flyway` | Applied migration history |
| `GET :8081/actuator/info` | App name, version, Java info |
