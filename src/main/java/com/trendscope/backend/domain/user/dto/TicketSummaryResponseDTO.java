package com.trendscope.backend.domain.user.dto;

import java.util.List;

public record TicketSummaryResponseDTO(
        String username,
        Integer quickTicketBalance,
        Integer premiumTicketBalance,
        Integer totalTicketBalance,
        List<TicketLedgerItemResponseDTO> recentLedger
) {
}
