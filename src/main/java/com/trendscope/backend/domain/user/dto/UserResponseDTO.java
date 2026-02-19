package com.trendscope.backend.domain.user.dto;

public record UserResponseDTO(
        String username,
        Boolean isSocial,
        String email,
        Integer ticketBalance
) {


}
