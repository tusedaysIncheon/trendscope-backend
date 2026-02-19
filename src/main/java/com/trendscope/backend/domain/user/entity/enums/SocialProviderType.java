package com.trendscope.backend.domain.user.entity.enums;

public enum SocialProviderType {

    EMAIL_OTP("이메일 OTP"),
    KAKAO("카카오"),
    NAVER("네이버"),
    GOOGLE("구글");

    private final String description;
    SocialProviderType(String description) {
        this.description = description;
    }

}
