package com.trendscope.backend.domain.user.dto;

import com.trendscope.backend.domain.user.entity.enums.TicketType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TicketTransactionRequestDTO {

    @Schema(description = "티켓 타입", example = "PREMIUM")
    @NotNull(message = "ticketType은 필수입니다.")
    private TicketType ticketType;

    @Schema(description = "거래 참조 ID (payment_id / job_id 등)", example = "pay_20260216_001")
    @NotBlank(message = "refId는 필수입니다.")
    private String refId;

    @Schema(description = "티켓 수량 (미입력 시 1)", example = "1")
    private Integer quantity;
}
