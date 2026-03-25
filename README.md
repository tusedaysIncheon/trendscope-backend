# Boat Backend

`boat-backend`는 TrendScope/Boat 서비스의 메인 API 서버입니다.  
Spring Boot 기반으로 인증, 티켓 결제, 3D 체형 분석 작업 생성, S3 업로드 URL 발급, Modal 연동, OpenAI 스타일 추천을 처리합니다.

포트폴리오용 설계 설명 문서는 [docs/portfolio-backend.md](docs/portfolio-backend.md)에서 확인할 수 있습니다.

## 사용 기술

| 영역 | 기술 |
| --- | --- |
| Language / Runtime | Java 17 |
| Framework | Spring Boot 3.5.6 |
| Build | Gradle Wrapper (`gradlew`) |
| Web / API | Spring Web, Spring Validation, SpringDoc OpenAPI |
| Security | Spring Security, OAuth2 Client, JWT (`jjwt`) |
| Database | PostgreSQL, Spring Data JPA, JDBC |
| Query | QueryDSL |
| Migration | Flyway |
| Cache / Token / Rate Limit | Redis, Spring Data Redis, Bucket4j |
| Storage / Email | AWS SDK v2 S3, AWS SES, Spring Mail, Resend |
| External Integrations | Modal body-analysis service, OpenAI API, Creem checkout/webhook |
| Logging / Utilities | Lombok, log4jdbc-log4j2 |
| Infra | Dockerfile, `docker-compose.yml` (PostgreSQL 15 / Redis 7) |

## 주요 기능

- 이메일 OTP 로그인과 OAuth2 소셜 로그인 지원
- JWT access/refresh token 발급 및 Redis 기반 refresh token 관리
- 티켓(quick / premium) 잔액, 사용, 환불, 구매 처리
- S3 presigned URL 기반 사진/GLB 업로드
- 체형 분석 job 생성, 상태 조회, 공유 링크 발급
- Modal GPU 서비스에 분석 요청 전달
- OpenAI 기반 패션 추천 생성 및 추천 이력 저장
- Creem 결제 세션 생성 및 webhook 처리

## 프로젝트 구조

```text
src/main/java/com/trendscope/backend
├── domain
│   ├── analyze
│   ├── auth
│   ├── measurement
│   ├── mypage
│   ├── payment
│   └── user
├── global
│   ├── config
│   ├── controller
│   ├── exception
│   ├── filter
│   ├── handler
│   ├── jwt
│   ├── s3
│   ├── security
│   └── util
└── TrendscopeBackendApplication.java

src/main/resources
├── application.yml
└── db/migration
```

## 로컬 실행

### 1. 환경 변수 준비

`.env.example`를 복사해서 `.env`를 만듭니다.

```bash
cp .env.example .env
```

Spring은 `application.yml`에서 `optional:file:.env[.properties]`를 읽도록 설정되어 있습니다.

### 2. 의존 서비스 실행

`docker-compose.yml`에는 PostgreSQL과 Redis가 포함되어 있습니다.

```bash
docker compose up -d
```

기본 포트:

- PostgreSQL: `localhost:5433`
- Redis: `localhost:6380`

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

기본 주소:

- API base: `http://localhost:8080/trendscope`
- Swagger UI: `http://localhost:8080/trendscope/swagger-ui.html`
- Health check: `http://localhost:8080/trendscope/healthz`

## 주요 환경 변수

| 분류 | 키 |
| --- | --- |
| DB | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` |
| Redis | `REDIS_HOST`, `REDIS_PORT`, `REDIS_USERNAME`, `REDIS_PASSWORD` |
| Auth | `JWT_SECRET`, `JWT_ACCESS_EXPIRATION_MS`, `JWT_REFRESH_EXPIRATION_MS` |
| OAuth | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET` |
| Frontend / CORS | `APP_FRONTEND_BASE_URL`, `CORS_ALLOWED_ORIGINS` |
| S3 / SES | `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `SES_ACCESS_KEY`, `SES_SECRET_KEY`, `SES_REGION` |
| Modal Analyze | `MODAL_BASE_URL`, `MODAL_ANALYZE_PATH`, `MODAL_CONNECT_TIMEOUT_MS`, `MODAL_READ_TIMEOUT_MS` |
| Email OTP | `EMAIL_OTP_DELIVERY_MODE`, `EMAIL_OTP_FROM`, `EMAIL_OTP_TTL_SECONDS`, `EMAIL_OTP_REVIEW_LOGIN_*`, `RESEND_API_KEY` |
| Payment | `CREEM_BASE_URL`, `CREEM_API_KEY`, `CREEM_WEBHOOK_SECRET`, `CREEM_PRODUCT_QUICK`, `CREEM_PRODUCT_PREMIUM`, `CREEM_CHECKOUT_SUCCESS_URL` |
| OpenAI | `OPENAI_API_KEY`, `OPENAI_BASE_URL`, `OPENAI_MODEL`, `OPENAI_TIMEOUT_MS` |

## 주요 API

| 영역 | 엔드포인트 |
| --- | --- |
| Auth | `POST /v1/auth/email-otp/request`, `POST /v1/auth/email-otp/verify` |
| User / JWT | `POST /v1/user`, `POST /v1/user/login`, `POST /jwt/exchange`, `POST /jwt/refresh` |
| Tickets / Payment | `GET /v1/tickets/me`, `POST /v1/payments/creem/checkout`, `POST /v1/payments/creem/webhook` |
| Analyze | `POST /v1/analyze/jobs/upload-urls`, `POST /v1/analyze/jobs/{jobId}/start`, `GET /v1/analyze/jobs/{jobId}`, `POST /v1/analyze/jobs/{jobId}/share` |
| Shared Result | `GET /v1/share/analyze/{token}` |
| Measurement | `POST /v1/measurement/fashion-recommendation`, `GET /v1/measurement/fashion-recommendation/history` |
| My Page | `GET /v1/mypage/summary` |
| Storage | `POST /v1/s3/presigned-url` |

## Docker 이미지 빌드

```bash
docker build -t boat-backend .
```

멀티 스테이지 빌드로 JAR를 만든 뒤 `eclipse-temurin:17-jre-jammy` 런타임 이미지로 실행합니다.
