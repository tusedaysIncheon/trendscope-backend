package com.trendscope.backend.domain.analyze.dto;

import com.trendscope.backend.domain.analyze.entity.enums.AnalyzeMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@NoArgsConstructor
public class AnalyzeOneShotRequestDTO {

    @Schema(description = "측정 모드", example = "STANDARD_2VIEW")
    @NotNull(message = "mode는 필수입니다.")
    private AnalyzeMode mode;

    @Schema(description = "정면 전신 사진", type = "string", format = "binary")
    @NotNull(message = "frontImage는 필수입니다.")
    private MultipartFile frontImage;

    @Schema(description = "측면 전신 사진 (STANDARD_2VIEW 필수)", type = "string", format = "binary")
    private MultipartFile sideImage;

    @Schema(description = "키(cm)", example = "175")
    @NotNull(message = "heightCm는 필수입니다.")
    @Min(value = 100, message = "heightCm는 100 이상이어야 합니다.")
    @Max(value = 230, message = "heightCm는 230 이하이어야 합니다.")
    private Double heightCm;

    @Schema(description = "몸무게(kg)", example = "70")
    @Min(value = 20, message = "weightKg는 20 이상이어야 합니다.")
    @Max(value = 300, message = "weightKg는 300 이하이어야 합니다.")
    private Double weightKg;

    @Schema(description = "성별 (male/female/other)", example = "male")
    @NotBlank(message = "gender는 필수입니다.")
    private String gender;

    @Schema(description = "측정 모델 (quick/premium). mode=QUICK_1VIEW에서는 quick, mode=STANDARD_2VIEW에서는 premium만 허용", example = "premium")
    private String measurementModel;
}
