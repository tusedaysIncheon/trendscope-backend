# TrendScope Backend API Spec (Frontend Integration)

최종 갱신: 2026-02-18

## 1) 공통

- Base URL: `http://localhost:8080/trendscope`
- Content-Type: `application/json`
- 인증 헤더: `Authorization: Bearer <accessToken>`
- Refresh 토큰: `refresh_token` (HttpOnly Cookie)

### 공통 응답 포맷 (`ApiResponse<T>`)

```json
{
  "code": "S-1",
  "message": "성공",
  "data": {},
  "success": true,
  "fail": false
}
```

### 대표 에러 코드

- `BAD_REQUEST` (400): 검증 실패/잘못된 파라미터
- `UNAUTHORIZED` (401): 인증 실패
- `FORBIDDEN` (403): 권한 부족
- `FEATURE_DISABLED` (503): 비활성화 기능
- `OPENAI_*` (502): OpenAI 업스트림 오류
- `INTERNAL_SERVER_ERROR` (500): 서버 내부 오류

---

## 2) 인증 API

## 2.1 OTP 요청

- `POST /v1/auth/email-otp/request`
- 인증: 없음

요청:

```json
{
  "email": "user@example.com"
}
```

응답:

```json
{
  "code": "S-1",
  "message": "인증 코드가 전송되었습니다.",
  "data": null,
  "success": true,
  "fail": false
}
```

## 2.2 OTP 검증 + 로그인

- `POST /v1/auth/email-otp/verify`
- 인증: 없음
- 부수효과: `refresh_token` 쿠키 발급

요청:

```json
{
  "email": "user@example.com",
  "code": "123456",
  "deviceId": "web-chrome-1"
}
```

응답:

```json
{
  "code": "S-1",
  "message": "성공",
  "data": {
    "accessToken": "<jwt>",
    "user": {
      "username": "OTP_xxxxxxxxxxxx",
      "isSocial": false,
      "email": "user@example.com",
      "ticketBalance": 1
    }
  },
  "success": true,
  "fail": false
}
```

참고:

- 최초 OTP 가입 사용자는 `quickTicketBalance=1`, `premiumTicketBalance=0` 으로 생성됩니다.

## 2.3 JWT Refresh

- `POST /jwt/refresh`
- 인증: 없음 (refresh cookie 기반)

요청(선택):

```json
{
  "deviceId": "web-chrome-1"
}
```

응답(정상):

```json
{
  "code": "S-1",
  "message": "성공",
  "data": {
    "accessToken": "<jwt>"
  },
  "success": true,
  "fail": false
}
```

응답(쿠키 없음, 게스트 모드):

```json
{
  "code": "S-1",
  "message": "성공",
  "data": {
    "message": "Guest Mode",
    "accessToken": ""
  },
  "success": true,
  "fail": false
}
```

## 2.4 로그아웃

- `POST /v1/user/logout`
- 인증: 필요
- 부수효과: refresh 토큰 Redis 삭제 + 쿠키 만료

---

## 3) 티켓/결제 API

## 3.1 Creem Checkout 생성

- `POST /v1/payments/creem/checkout`
- 인증: 필요

요청:

```json
{
  "ticketType": "PREMIUM",
  "quantity": 1,
  "successUrl": "http://localhost:5173/payment/success"
}
```

- `ticketType`: `QUICK | PREMIUM`
- `quantity`: 생략 시 1

## 3.2 티켓 구매/사용/환불

- `POST /v1/tickets/purchase`
- `POST /v1/tickets/use`
- `POST /v1/tickets/refund`
- 인증: 필요

공통 요청:

```json
{
  "ticketType": "PREMIUM",
  "refId": "73e37842c510410ca30fb4751a841b9e",
  "quantity": 1
}
```

중요 규칙:

- 멱등키: `(user_id, ticket_type, reason, ref_id)`
- `refId`는 서버에서 소문자 normalize 후 처리됩니다.
- Analyze 시작 전 반드시 `POST /v1/tickets/use`를 먼저 호출해야 합니다.
- 권장: `refId = jobId` 그대로 사용.

## 3.3 내 티켓 요약

- `GET /v1/tickets/me?size=20`
- 인증: 필요

응답 `data`:

- `quickTicketBalance`
- `premiumTicketBalance`
- `totalTicketBalance`
- `recentLedger[]`

---

## 4) Analyze Job API (실서비스 파이프라인)

## 4.1 업로드 URL 발급

- `POST /v1/analyze/jobs/upload-urls`
- 인증: 필요

요청:

```json
{
  "mode": "STANDARD_2VIEW",
  "frontFilename": "front.jpg",
  "sideFilename": "side.jpg"
}
```

- `mode`:
  - `QUICK_1VIEW`: `sideFilename` 생략 가능
  - `STANDARD_2VIEW`: `sideFilename` 필수

응답 `data`:

- `jobId`
- `frontImage.fileKey/uploadUrl`
- `sideImage.fileKey/uploadUrl` (quick일 때 `null`)
- `glbObject.fileKey/uploadUrl`

## 4.2 이미지 업로드 (S3 Presigned PUT)

- 프론트가 `uploadUrl`로 직접 PUT 업로드
- 업로드 성공 후 서버 저장 경로는 `fileKey`

## 4.3 측정 시작

- `POST /v1/analyze/jobs/{jobId}/start`
- 인증: 필요

요청:

```json
{
  "heightCm": 175,
  "weightKg": 70,
  "gender": "male",
  "measurementModel": "premium"
}
```

- `measurementModel`: `quick | premium` (생략 시 mode에 따라 자동 결정)
- 제약:
  - `mode=QUICK_1VIEW` => `measurementModel=quick`만 허용
  - `mode=STANDARD_2VIEW` => `measurementModel=premium`만 허용

고정 처리:

- `outputPose`는 서버 내부에서 항상 `PHOTO_POSE`
- `qualityMode`, `normalizeWithAnny`는 `measurementModel`로 내부 결정

응답 `data`:

- `jobId`, `mode`, `status`, `queuedAt`

## 4.4 상태/결과 조회

- `GET /v1/analyze/jobs/{jobId}`
- 인증: 필요

응답 `data.status`:

- `QUEUED | RUNNING | COMPLETED | FAILED`

COMPLETED 시 `data.result`는 모드별로 다음 형태:

### Quick (`measurementModel=quick`)

```json
{
  "success": true,
  "lengths": {
    "shoulder_width_cm": 41.82,
    "arm_length_cm": 56.74,
    "leg_length_cm": 81.19,
    "torso_length_cm": 49.31,
    "inseam_cm": null
  }
}
```

### Premium (`measurementModel=premium`)

```json
{
  "success": true,
  "lengths": {
    "shoulder_width_cm": 44.45,
    "arm_length_cm": 58.27,
    "leg_length_cm": 82.60,
    "torso_length_cm": 45.32,
    "inseam_cm": 78.92
  },
  "circumferences": {
    "chest_cm": 84.76,
    "waist_cm": 68.39,
    "hip_cm": 94.79,
    "thigh_cm": 45.23,
    "chest_axis_m": 1.123,
    "waist_axis_m": 1.090,
    "hip_axis_m": 0.786,
    "thigh_axis_m": 0.715
  },
  "body_shape": "pear"
}
```

FAILED 시:

- `errorCode`
- `errorDetail`

## 4.5 내 측정 목록

- `GET /v1/analyze/jobs/me?size=20`
- 인증: 필요

---

## 5) 패션 추천 API (OpenAI)

## 5.1 추천 생성

- `POST /v1/measurement/fashion-recommendation`
- 인증: 필요

요청:

```json
{
  "jobId": "de73d112732a46d7b73c8c180aae2b7e"
}
```

동작:

- 해당 `jobId`의 `result`를 OpenAI 입력으로 사용
- quick 입력: `success + lengths`
- premium 입력: `success + lengths + circumferences + body_shape`
- 같은 `jobId`로 재요청 시 새 호출 없이 기존 추천 이력 반환(1 job = 1 recommendation record)

응답:

```json
{
  "code": "S-1",
  "message": "성공",
  "data": {
    "jobId": "de73d112732a46d7b73c8c180aae2b7e",
    "measurementModel": "premium",
    "recommendation": {
      "version": "mvp.v1"
    }
  },
  "success": true,
  "fail": false
}
```

## 5.2 추천 이력 목록

- `GET /v1/measurement/fashion-recommendation/history?size=20`
- 인증: 필요

응답 `data.histories[]`:

- `userSeq` (사용자별 추천 시퀀스)
- `jobId`
- `mode`
- `measurementModel`
- `frontImageKey`
- `sideImageKey`
- `glbObjectKey`
- `createdDate`

## 5.3 추천 이력 상세

- `GET /v1/measurement/fashion-recommendation/history/{userSeq}`
- 인증: 필요

응답 `data`:

- 이력 메타 + `result`(측정 JSON) + `recommendation`(LLM JSON)
- `llmModel`, `promptVersion`

---

## 6) 마이페이지 API

- `GET /v1/mypage/summary?ticketSize=20&analyzeSize=20`
- 인증: 필요

응답 `data`:

- `username`
- `ticket` (`/v1/tickets/me` 요약)
- `recentAnalyzeJobs` (`/v1/analyze/jobs/me` 요약)

---

## 7) Dev 전용 API (프로덕션 미사용)

- `POST /v1/dev/analyze/one-shot`
- 토큰 없이 multipart 파일 업로드 1회로 즉시 결과 반환
- 로컬 튜닝/디버깅 전용

---

## 8) 프론트 권장 호출 순서

1. `POST /v1/auth/email-otp/request`
2. `POST /v1/auth/email-otp/verify` (accessToken 획득)
3. `POST /v1/analyze/jobs/upload-urls`
4. Presigned PUT으로 이미지 업로드
5. `POST /v1/tickets/use` (`refId=jobId`, `ticketType`은 모델과 일치)
6. `POST /v1/analyze/jobs/{jobId}/start`
7. `GET /v1/analyze/jobs/{jobId}` 폴링 (`COMPLETED` 대기)
8. `POST /v1/measurement/fashion-recommendation` (선택)
9. 필요 시 `GET /v1/measurement/fashion-recommendation/history`

---

## 9) 참고

- Swagger UI: `/trendscope/swagger-ui.html`
- OpenAPI JSON: `/trendscope/v3/api-docs`
