package com.trendscope.backend.domain.user.dto;

import com.trendscope.backend.domain.user.entity.enums.TicketLedgerReason;
import com.trendscope.backend.domain.user.entity.enums.TicketType;

import java.time.LocalDateTime;

public record TicketTransactionResponseDTO(
        Long transactionId,
        TicketType ticketType,
        TicketLedgerReason reason,
        String refId,
        Integer quantity,
        Integer delta,
        Integer quickTicketBalance,
        Integer premiumTicketBalance,
        Integer totalTicketBalance,
        Boolean applied,
        LocalDateTime createdDate
) {
}
