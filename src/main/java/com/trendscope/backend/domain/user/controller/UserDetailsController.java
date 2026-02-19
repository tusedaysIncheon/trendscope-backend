package com.trendscope.backend.domain.user.controller;

import com.trendscope.backend.domain.user.dto.UserDetailsRequestDTO;
import com.trendscope.backend.domain.user.dto.UserDetailsResponseDTO;
import com.trendscope.backend.domain.user.entity.UserEntity;
import com.trendscope.backend.domain.user.service.UserDetailService;
import com.trendscope.backend.global.util.ApiResponse;
import com.trendscope.backend.global.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/user-details")
@RequiredArgsConstructor
public class UserDetailsController {

    private final UserDetailService userDetailService;

    @PostMapping
    public ApiResponse<String> saveUserDetails(
            @Valid @RequestBody UserDetailsRequestDTO dto) {
        // @AuthenticationPrincipal CustomUserDetails user removed

        UserEntity currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null || currentUser.getUsername() == null) {
            throw new IllegalArgumentException("인증 정보가 없습니다.");
        }
        String username = currentUser.getUsername();

        log.info("save user details : {}", username);

        userDetailService.saveUserDetails(username, dto);

        return ApiResponse.ok("프로필이 성공적으로 저장되었습니다.");
    }



    @GetMapping("/exist-nickname")
    public ApiResponse<Boolean> existNickname(@RequestParam String nickname) {
        log.info("exist nickname : {}", nickname);
        return ApiResponse.ok(userDetailService.existNickname(nickname));

    }

}
