# TrendScope Backend 배포 체크리스트

## 1) 배포 전 준비

- [ ] 배포 대상 브랜치/커밋 확정
- [ ] `./gradlew test` 통과 확인
- [ ] 민감정보(`.env`)가 Git에 포함되지 않았는지 확인
- [ ] 운영용 환경변수 목록 확정 (DB, Redis, JWT, SES, OAuth, S3, Modal)

## 2) 데이터베이스/Flyway

- [ ] Flyway 마이그레이션 파일이 최신 스키마를 반영하는지 확인
- [ ] 스테이징에서 앱 기동 시 Flyway가 오류 없이 실행되는지 확인
- [ ] `user_` 필수 컬럼/제약 확인
  - [ ] `provider_user_id` 존재 및 `NOT NULL`
  - [ ] `quick_ticket_balance` 존재 및 `NOT NULL DEFAULT 0`
  - [ ] `premium_ticket_balance` 존재 및 `NOT NULL DEFAULT 0`
  - [ ] `UNIQUE(social_provider_type, provider_user_id)` 존재
  - [ ] `CHECK(quick_ticket_balance >= 0 AND premium_ticket_balance >= 0)` 존재
- [ ] `ticket_ledger` 테이블 생성 여부 확인
- [ ] `analyze_job` 테이블 생성 여부 확인
- [ ] 운영 배포 직전 DB 스냅샷/백업 생성

## 3) JPA 설정 점검

- [ ] 개발/스테이징: `ddl-auto=update` 유지
- [ ] 운영 전환 시점: Flyway 안정화 후 `ddl-auto=validate` 전환 계획 수립

## 4) 인증/보안

- [ ] OTP 요청/검증 API 정상 동작
  - [ ] `POST /v1/auth/email-otp/request`
  - [ ] `POST /v1/auth/email-otp/verify`
- [ ] Access/Refresh 토큰 발급/재발급/로그아웃 정상 동작
- [ ] CORS 도메인이 운영 프론트 도메인으로 설정되어 있는지 확인
- [ ] JWT 시크릿/만료시간 운영값으로 설정 확인

## 5) SES 메일 발송

- [ ] `EMAIL_OTP_DELIVERY_MODE=ses`
- [ ] `EMAIL_OTP_FROM`이 SES에서 검증된 발신자인지 확인
- [ ] SES 리전 일치 확인 (`SES_REGION` vs 실제 SES 리전)
- [ ] 샌드박스 여부 확인
  - [ ] 샌드박스면 수신자도 검증 필요
  - [ ] 운영 전 프로덕션 액세스 신청 완료
- [ ] 도메인 사용 시 SPF/DKIM/DMARC 설정 완료

## 6) 결제/티켓 정합성

- [ ] 결제 성공 시 `ticket_ledger(PURCHASE)` 적재 확인
- [ ] `user_.quick_ticket_balance / premium_ticket_balance` 증가 확인
- [ ] 중복 웹훅 멱등 처리 확인 (중복 적립 방지)
- [ ] 측정 사용 시 `USE`, 실패 환불 시 `REFUND` 적재 확인

## 7) 측정 파이프라인

- [ ] 업로드 URL 발급 API 정상 (`POST /v1/analyze/jobs/upload-urls`)
- [ ] S3 presigned URL 업로드 정상
- [ ] 측정 시작 API 정상 (`POST /v1/analyze/jobs/{jobId}/start`)
- [ ] Modal API 호출 정상 (quick/standard 모드 포함)
- [ ] 상태 전이 확인 (`QUEUED -> RUNNING -> COMPLETED/FAILED`)
- [ ] 상태 조회 API 정상 (`GET /v1/analyze/jobs/{jobId}`)
- [ ] 내 측정 목록 API 정상 (`GET /v1/analyze/jobs/me`)
- [ ] 마이페이지 요약 API 정상 (`GET /v1/mypage/summary`)

## 8) 개인정보/보관정책

- [ ] 30일 경과 데이터 자동 삭제 스케줄러 동작 확인
- [ ] 삭제 대상 범위 확인
  - [ ] 입력 신체정보(키/몸무게/성별)
  - [ ] 원본 사진
  - [ ] 3D 메쉬/결과물
  - [ ] 분석 결과 JSON
- [ ] 개인정보/보관정책 링크가 프론트에 노출되는지 확인

## 9) 배포 직후 스모크 테스트

- [ ] `/v1/auth/email-otp/request` 200 응답 확인
- [ ] `/v1/auth/email-otp/verify` 성공 + 토큰 발급 확인
- [ ] `/v1/user` 인증 조회 정상
- [ ] 결제 1건 테스트 후 티켓 반영 확인
- [ ] `/v1/analyze/jobs/upload-urls` -> 업로드 -> `/start` -> 상태 조회 흐름 확인
- [ ] `/v1/mypage/summary`에서 티켓/최근 측정 동시 조회 확인
- [ ] 에러 로그/알람 확인 (DB, Redis, SES, Modal)

## 10) 롤백 계획

- [ ] 롤백 기준 정의 (예: 로그인 실패율, 결제 반영 실패율)
- [ ] 이전 배포 아티팩트/이미지 보관 확인
- [ ] DB 복구 절차(스냅샷 복원) 문서화
