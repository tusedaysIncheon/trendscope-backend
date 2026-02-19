package com.trendscope.backend.global.s3.api;

import com.trendscope.backend.domain.user.entity.UserEntity;
import com.trendscope.backend.global.s3.model.PresignedUrlRequestDTO;
import com.trendscope.backend.global.s3.model.PresignedUrlResponseDTO;
import com.trendscope.backend.global.util.ApiResponse;
import com.trendscope.backend.global.util.S3Util;
import com.trendscope.backend.global.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/v1/s3")
@RequiredArgsConstructor
public class S3Controller {

    private final S3Util s3Util;

    @PostMapping("/presigned-url")
    public ApiResponse<PresignedUrlResponseDTO> getPresignedUrl(
            @RequestBody PresignedUrlRequestDTO requestDTO) {

        // @AuthenticationPrincipal CustomUserDetails user removed
        UserEntity user = SecurityUtils.getCurrentUser();

        if (user == null) {
            log.error("S3 Presigned URL 요청 실패: 인증 정보가 없습니다.");
            return ApiResponse.fail("401", "인증 정보가 없습니다.");
        }

        String username = user.getUsername();
        String finalFolder;

        if ("profileImage".equals(requestDTO.getFolder())) {
            finalFolder = "profileImage";
        } else if ("contentImage".equals(requestDTO.getFolder())) {
            finalFolder = "contentImage/" + username;
        } else if ("boatpic".equals(requestDTO.getFolder())) {
            finalFolder = "boatpic/" + username;
        } else if ("options".equals(requestDTO.getFolder())) {
            finalFolder = "options/" + username;
        } else {
            throw new IllegalArgumentException("유효하지 않은 폴더명입니다.");
        }

        String[] result = s3Util.getPresignedUrl(requestDTO.getFilename(), finalFolder);
        log.info("S3 URL 발급 완료 - User: {}, Folder: {}", username, finalFolder);

        return ApiResponse.ok(new PresignedUrlResponseDTO(result[0], result[1]));

    }

}
