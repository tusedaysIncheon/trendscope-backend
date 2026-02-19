package com.trendscope.backend.domain.payment.dto;

import com.trendscope.backend.domain.user.entity.enums.TicketType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreemCheckoutCreateRequestDTO {

    @Schema(description = "구매할 티켓 타입", example = "PREMIUM")
    @NotNull(message = "ticketType은 필수입니다.")
    private TicketType ticketType;

    @Schema(description = "구매 수량 (미입력 시 1)", example = "1")
    private Integer quantity;

    @Schema(description = "결제 완료 후 리디렉션 URL (미입력 시 서버 기본값 사용)", example = "http://localhost:5173/payment/success")
    private String successUrl;
}
