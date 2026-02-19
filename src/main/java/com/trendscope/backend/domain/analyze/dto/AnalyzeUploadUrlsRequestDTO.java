package com.trendscope.backend.domain.analyze.dto;

import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AnalyzeUploadUrlsRequestDTO {

    @Schema(description = "측정 모드", example = "STANDARD_2VIEW")
    @NotNull(message = "mode는 필수입니다.")
    private AnalyzeMode mode;

    @Schema(description = "정면 사진 파일명", example = "front.jpg")
    @NotBlank(message = "frontFilename은 필수입니다.")
    private String frontFilename;

    @Schema(description = "측면 사진 파일명 (STANDARD_2VIEW 필수)", example = "side.jpg")
    private String sideFilename;
}
