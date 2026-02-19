package com.trendscope.backend.domain.user.controller;

import com.trendscope.backend.domain.user.dto.TicketSummaryResponseDTO;
import com.trendscope.backend.domain.user.dto.TicketTransactionRequestDTO;
import com.trendscope.backend.domain.user.dto.TicketTransactionResponseDTO;
import com.trendscope.backend.domain.user.service.TicketLedgerService;
import com.trendscope.backend.global.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/tickets")
@Tag(name = "Ticket API", description = "티켓 잔액/원장 관리 API")
public class TicketController {

    private final TicketLedgerService ticketLedgerService;

    @Operation(summary = "티켓 구매 적립", description = "결제 완료 후 티켓을 적립합니다. (멱등 처리: reason+refId)")
    @PostMapping("/purchase")
    public ApiResponse<TicketTransactionResponseDTO> purchase(
            @Valid @RequestBody TicketTransactionRequestDTO dto
    ) {
        String username = currentUsername();
        return ApiResponse.ok(ticketLedgerService.purchase(username, dto));
    }

    @Operation(summary = "티켓 사용 차감", description = "측정 시작/완료 시 티켓을 차감합니다. (멱등 처리: reason+refId)")
    @PostMapping("/use")
    public ApiResponse<TicketTransactionResponseDTO> use(
            @Valid @RequestBody TicketTransactionRequestDTO dto
    ) {
        String username = currentUsername();
        return ApiResponse.ok(ticketLedgerService.use(username, dto));
    }

    @Operation(summary = "티켓 환불", description = "측정 실패/취소 시 티켓을 환불합니다. (멱등 처리: reason+refId)")
    @PostMapping("/refund")
    public ApiResponse<TicketTransactionResponseDTO> refund(
            @Valid @RequestBody TicketTransactionRequestDTO dto
    ) {
        String username = currentUsername();
        return ApiResponse.ok(ticketLedgerService.refund(username, dto));
    }

    @Operation(summary = "내 티켓 요약 조회", description = "현재 티켓 잔액과 최근 거래 원장을 조회합니다.")
    @GetMapping("/me")
    public ApiResponse<TicketSummaryResponseDTO> myTicketSummary(
            @RequestParam(defaultValue = "20") int size
    ) {
        String username = currentUsername();
        return ApiResponse.ok(ticketLedgerService.getSummary(username, size));
    }

    private String currentUsername() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("인증 사용자 정보를 찾을 수 없습니다.");
        }
        return username;
    }
}
