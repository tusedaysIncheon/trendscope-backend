package com.trendscope.backend.global.util;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {

    private String code;    // "S-1", "C-1", "F-1" 등
    private String message; // "성공", "실패", "에러 메시지"
    private T data;         // 실제 데이터

    // ------------------------------------------------
    // 1. 성공 응답 (조회, 수정, 삭제 등) - 200 OK 계열
    // ------------------------------------------------
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("S-1", "성공", data);
    }

    // 데이터 없이 메시만 보낼 때 (예: 로그아웃 성공)
    public static <T> ApiResponse<T> ok(String message) {
        return new ApiResponse<>("S-1", message, null);
    }

    // ------------------------------------------------
    // 2. 생성 성공 응답 (등록 등) - 201 Created 계열
    // ------------------------------------------------
    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>("C-1", "생성 성공", data);
    }

    // ------------------------------------------------
    // 3. 실패 응답 (예외 처리용)
    // ------------------------------------------------
    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>("F-1", message, null);
    }

    // 커스텀 코드가 필요할 때 (예: F-404, F-500)
    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    // ------------------------------------------------
    // 4. 성공 여부 판단 (프론트엔드에서 쓰기 편하게)
    // ------------------------------------------------
    // JSON 필드명: "success": true/false
    public boolean isSuccess() {
        // "S-"(Success) 또는 "C-"(Created)로 시작하면 성공!
        return this.code != null && (this.code.startsWith("S-") || this.code.startsWith("C-"));
    }

    // JSON 필드명: "fail": true/false (굳이 필요 없다면 제거해도 됨)
    public boolean isFail() {
        return !isSuccess();
    }
}
