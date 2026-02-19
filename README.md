# Boat Backend

이 프로젝트는 AI 기반 다이어트/건강 코칭 플랫폼 'Boat'의 백엔드 서버로, Spring Boot와 Java 17을 기반으로 구축되었습니다. 사용자 인증(자체/소셜), JWT 기반 API 보안, 사용자 프로필 관리, AWS S3를 이용한 파일 업로드 등 다양한 기능을 제공합니다.

## 🚀 주요 기술 스택

- **Framework**: Spring Boot 3.5.6
- **Language**: Java 17
- **Security**: Spring Security, OAuth2 Client, JWT (jjwt)
- **Database**: PostgreSQL (for primary data), Redis (for refresh tokens and rate limiting)
- **File Storage**: AWS S3
- **API Documentation**: SpringDoc OpenAPI (Swagger UI)
- **Build Tool**: Gradle
- **Other**:
    - Lombok: Boilerplate code reduction
    - Bucket4j: Rate limiting
    - log4jdbc-log4j2: SQL query logging
    - Spring Boot Validation: for request data validation

## ✨ 시작하기

### 사전 요구사항

- Java 17
- Gradle
- Docker (for PostgreSQL and Redis) or local installations
- AWS Account and S3 bucket

### 빌드 및 실행

1.  **애플리케이션 클론:**

    ```bash
    git clone https://github.com/your-username/boat-backend.git
    cd boat-backend
    ```

2.  **데이터베이스 및 Redis 실행 (Docker 사용 시):**

    ```bash
    docker-compose up -d
    ```
    *(Note: A `docker-compose.yml` file is not included in the project, but this is the recommended way to run dependencies.)*

3.  **`application.yml` 설정:**

    `src/main/resources/application.yml` 파일을 열어 데이터베이스, Redis, JWT, OAuth2 클라이언트 및 AWS S3 설정을 환경에 맞게 수정합니다.

    ```yaml
    spring:
      datasource:
        url: jdbc:postgresql://localhost:5432/your_db
        username: your_username
        password: your_password
      redis:
        host: localhost
        port: 6379
      security:
        oauth2:
          client:
            registration:
              google:
                client-id: "your-google-client-id"
                client-secret: "your-google-client-secret"
              # Other social providers
    jwt:
      secret: "your-jwt-secret-key-with-at-least-256-bits"

    cloud:
      aws:
        credentials:
          access-key: "your-aws-access-key"
          secret-key: "your-aws-secret-key"
        s3:
          bucket: "your-s3-bucket-name"
        region:
          static: "your-aws-region"
    ```

4.  ** 빌드 및 실행:**

    ```bash
    ./gradlew bootRun
    ```

    이제 `http://localhost:8080`에서 애플리케이션이 실행됩니다. API 문서는 `http://localhost:8080/swagger-ui.html`에서 확인할 수 있습니다.

## 📁 프로젝트 구조

```
.
└── src
    └── main
        ├── java
        │   └── com/boat/boatbackend
        │       ├── BoatBackendApplication.java # Spring Boot 메인 클래스
        │       ├── api                         # 외부 요청을 처리하는 API 컨트롤러
        │       ├── domain                      # 비즈니스 로직 및 데이터 (Entity, DTO, Repository, Service)
        │       │   └── user
        │       └── global                      # 전역적으로 사용되는 모듈
        │           ├── config                  # Security, MVC, JWT 등 설정 클래스
        │           ├── filter                  # JWT, RateLimit 등 서블릿 필터
        │           ├── handler                 # 로그인 성공/실패, 예외 처리 핸들러
        │           ├── jwt                     # JWT 토큰 생성, 검증, 재발급 관련 로직
        │           ├── s3                      # AWS S3 Presigned URL 생성 관련 로직
        │           ├── security                # Spring Security 사용자 정의 클래스
        │           └── util                    # JWT, S3 등 유틸리티 클래스
        └── resources
            └── application.yml                 # 애플리케이션 설정 파일
```

## 🔐 주요 기능

### 1. 사용자 인증 및 인가

- **자체 로그인**: 이메일/비밀번호 기반의 로그인을 지원합니다. 성공 시 Access Token은 응답 본문에, Refresh Token은 `HttpOnly` 쿠키에 담아 전달합니다.
- **소셜 로그인**: Google, Naver, Kakao 등 OAuth2를 통한 소셜 로그인을 지원합니다.
- **JWT 기반 인증**:
  - **Access Token**: API 요청 시 `Authorization: Bearer <token>` 헤더에 담아 전송되며, 서버에서는 `JWTFilter`를 통해 유효성을 검증합니다.
  - **Refresh Token**: Access Token이 만료되었을 때 재발급을 위해 사용됩니다. Redis에 사용자 기기별로 저장되어 보안을 강화하고, 로그아웃 시 서버에서 삭제됩니다.
  - 소셜 로그인 후에는 `/jwt/exchange` 엔드포인트를 호출하여 서비스 자체 JWT로 교환할 수 있습니다.

### 2. 파일 업로드 (AWS S3 Presigned URL)

- 클라이언트가 파일을 직접 S3 버킷에 업로드할 수 있도록 서버에서 임시 서명된 URL(Presigned URL)을 발급합니다.
- 이를 통해 서버의 부하를 줄이고, 파일 스트림을 직접 다루지 않아도 되어 효율적이고 안전합니다. Presigned URL을 발급받기 위해서는 사용자 인증이 필요합니다.

### 3. 사용자 프로필 관리

- 사용자의 닉네임, MBTI, 거주 지역 등 상세 프로필 정보를 저장하고 조회하는 기능을 제공합니다.

## 📖 API 엔드포인트

### User API (`/v1/user`)

| Method | Path                | Description                                        | Auth Required |
|--------|---------------------|----------------------------------------------------|---------------|
| POST   | `/exist`            | 사용자 아이디 중복 여부를 확인합니다.               | No            |
| POST   | `/`                 | 신규 사용자를 등록합니다.                             | No            |
| GET    | `/`                 | 현재 로그인된 사용자의 정보를 조회합니다.           | Yes           |
| PUT    | `/`                 | 사용자 정보를 수정합니다.                             | Yes           |
| DELETE | `/`                 | 회원에서 탈퇴합니다.                                | Yes           |
| POST   | `/login`            | 자체 로그인을 수행합니다.                             | No            |
| POST   | `/logout`           | 로그아웃을 수행합니다.                               | Yes           |

### User Details API (`/v1/user-details`)

| Method | Path                 | Description                          | Auth Required |
|--------|----------------------|--------------------------------------|---------------|
| POST   | `/`                  | 사용자 상세 프로필을 저장합니다.       | Yes           |
| GET    | `/`                  | 내 상세 프로필을 조회합니다.           | Yes           |


### JWT API (`/jwt`)

| Method | Path          | Description                                                    | Auth Required |
|--------|---------------|----------------------------------------------------------------|---------------|
| POST   | `/exchange`   | 소셜 로그인 직후 세션 쿠키를 서비스 자체 JWT로 교환합니다.     | No            |
| POST   | `/refresh`    | Refresh 토큰을 사용하여 새로운 Access 토큰을 재발급합니다.     | No (Cookie)   |

### S3 API (`/v1/s3`)

| Method | Path                 | Description                                    | Auth Required |
|--------|----------------------|------------------------------------------------|---------------|
| POST   | `/presigned-url`     | 파일 업로드를 위한 Presigned URL을 발급합니다. | Yes           |


## 🛡️ 보안 평가

이 섹션은 프로젝트의 현재 보안 상태에 대한 평가를 제공합니다.

### 잘 구현된 점

- **JWT 기반 인증**: Stateless한 인증 방식을 사용하여 확장성이 좋습니다. Access/Refresh 토큰 분리 전략과 Refresh Token을 Redis에 저장하는 방식은 보안을 강화합니다.
- **비밀번호 암호화**: `BCryptPasswordEncoder`를 사용하여 비밀번호를 안전하게 해싱합니다.
- **역할 기반 접근 제어 (RBAC)**: `Spring Security`를 사용하여 엔드포인트별로 세분화된 권한 관리를 하고 있습니다.
- **S3 Presigned URL**: 클라이언트가 파일을 직접 S3에 업로드하도록 하여 서버 부하를 줄이고, 민감한 AWS 자격 증명을 클라이언트에 노출하지 않습니다.
- **입력 유효성 검사**: `@Validated`와 `@Valid` 어노테이션을 사용하여 DTO(Data Transfer Object)에 대한 서버사이드 유효성 검사를 적용하여 안정성을 높였습니다.
- **Rate Limiting**: `Bucket4j`를 사용하여 API 요청 제한을 구현하여 DoS 및 Brute-force 공격에 대한 방어책을 마련했습니다.
- **소셜 로그인**: OAuth2를 사용하여 안전하게 소셜 로그인을 구현했습니다.
- **CORS 설정**: 특정 오리진에 대해서만 요청을 허용하도록 설정되어 있어 기본적인 웹 보안을 준수합니다.

### 개선 제안

- **CSRF 보호**: 현재 `CSRF` 보호가 비활성화되어 있습니다. Stateless API에서는 일반적으로 문제가 되지 않지만, Refresh Token 요청과 같이 쿠키를 사용하는 엔드포인트(`/jwt/refresh`)는 CSRF 공격에 취약할 수 있습니다. 쿠키의 `SameSite` 속성을 `Strict`로 설정하여 방어 수준을 높이는 것을 권장합니다.
- **입력 유효성 검사 강화**: 기본적인 유효성 검사는 적용되어 있으나, 모든 API 엔드포인트에서 일관되게 적용되고 있는지, 더 복잡한 비즈니스 규칙(예: 비밀번호 복잡도)에 대한 검증이 충분한지 검토가 필요합니다.
- **민감한 정보 로깅**: 운영 환경에서는 민감한 사용자 정보(예: username)가 로그에 남지 않도록 로그 레벨과 내용을 신중하게 관리해야 합니다.
- **보안 헤더**: `Content-Security-Policy`, `X-Content-Type-Options`, `X-Frame-Options` 등의 보안 관련 HTTP 헤더를 응답에 추가하여 XSS와 같은 웹 취약점을 추가적으로 방어할 수 있습니다.
- **Secrets 관리**: `application.yml`에 포함된 JWT 시크릿 키, 데이터베이스 비밀번호 등은 개발용 예시입니다. 운영 환경에서는 환경 변수나 Vault와 같은 외부 secret 관리 도구를 통해 안전하게 주입해야 합니다.
- **의존성 스캔**: 프로젝트 라이브러리의 알려진 취약점을 정기적으로 확인하기 위해 OWASP Dependency-Check나 Snyk 같은 도구를 CI/CD 파이프라인에 통합하는 것이 좋습니다.

## 📦 주요 의존성

- `spring-boot-starter-web`
- `spring-boot-starter-security`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-data-redis`
- `spring-boot-starter-oauth2-client`
- `spring-boot-starter-validation`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0`
- `io.jsonwebtoken:jjwt:0.13.0`
- `org.postgresql:postgresql:42.7.8`
- `org.bgee.log4jdbc-log4j2:log4jdbc-log4j2-jdbc4.1:1.16`
- `com.bucket4j:bucket4j-redis:8.7.0`
- `software.amazon.awssdk:s3`
- `org.projectlombok:lombok`
