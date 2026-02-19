package com.trendscope.backend.domain.payment.dto;

import com.trendscope.backend.domain.user.entity.enums.TicketType;

public record CreemCheckoutCreateResponseDTO(
        String checkoutId,
        String checkoutUrl,
        String requestId,
        TicketType ticketType,
        Integer quantity,
        String productId
) {
}
