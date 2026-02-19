package com.trendscope.backend.domain.mypage.dto;

import com.trendscope.backend.domain.analyze.dto.AnalyzeJobListItemDTO;
import com.trendscope.backend.domain.user.dto.TicketSummaryResponseDTO;

import java.util.List;

public record MyPageSummaryResponseDTO(
        String username,
        TicketSummaryResponseDTO ticket,
        List<AnalyzeJobListItemDTO> recentAnalyzeJobs
) {
}
