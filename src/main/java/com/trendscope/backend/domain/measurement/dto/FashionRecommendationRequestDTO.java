package com.trendscope.backend.domain.measurement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FashionRecommendationRequestDTO {

    @Schema(description = "측정 완료된 analyze jobId", example = "de73d112732a46d7b73c8c180aae2b7e")
    @NotBlank(message = "jobId는 필수입니다.")
    private String jobId;

    @Schema(
            description = "응답 언어 힌트(ko/en/ja/zh, locale 허용)",
            example = "ko-KR"
    )
    @Pattern(
            regexp = "(?i)^(ko|en|ja|zh)([-_][a-z]{2})?$",
            message = "language는 ko/en/ja/zh 또는 locale 형식(예: ko-KR)이어야 합니다."
    )
    @Size(max = 40, message = "language는 최대 40자까지 허용됩니다.")
    private String language;

    @Schema(
            description = "사용자 위치 힌트(국가/도시/타임존)",
            example = "Asia/Seoul"
    )
    @Size(max = 120, message = "location은 최대 120자까지 허용됩니다.")
    private String location;
}
