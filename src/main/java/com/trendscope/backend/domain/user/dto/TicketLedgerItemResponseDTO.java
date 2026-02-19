package com.trendscope.backend.domain.user.dto;

import com.trendscope.backend.domain.user.entity.enums.TicketLedgerReason;
import com.trendscope.backend.domain.user.entity.enums.TicketType;

import java.time.LocalDateTime;

public record TicketLedgerItemResponseDTO(
        Long id,
        TicketType ticketType,
        TicketLedgerReason reason,
        Integer delta,
        String refId,
        LocalDateTime createdDate
) {
}
