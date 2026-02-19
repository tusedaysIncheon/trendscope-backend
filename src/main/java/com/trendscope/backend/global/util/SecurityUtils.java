package com.trendscope.backend.global.util;

import com.trendscope.backend.domain.user.dto.CustomOAuth2User;
import com.trendscope.backend.domain.user.entity.UserEntity;
import com.trendscope.backend.global.security.custom.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

    public static UserEntity getCurrentUser() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getPrincipal() instanceof String) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof CustomUserDetails) {
            return ((CustomUserDetails) principal).getUserEntity();
        }

        if (principal instanceof CustomOAuth2User) {
            return ((CustomOAuth2User) principal).getUserEntity();
        }

        return null;
    }

    public static Long getCurrentUserId() {
        UserEntity userEntity = getCurrentUser();
        return (userEntity == null) ? null : userEntity.getId();
    }

}
