package com.trendscope.backend.domain.mypage.service;

import com.trendscope.backend.domain.analyze.dto.AnalyzeJobListItemDTO;
import com.trendscope.backend.domain.analyze.service.AnalyzeJobService;
import com.trendscope.backend.domain.mypage.dto.MyPageSummaryResponseDTO;
import com.trendscope.backend.domain.user.dto.TicketSummaryResponseDTO;
import com.trendscope.backend.domain.user.service.TicketLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MyPageService {

    private final TicketLedgerService ticketLedgerService;
    private final AnalyzeJobService analyzeJobService;

    @Transactional(readOnly = true)
    public MyPageSummaryResponseDTO getSummary(String username, int ticketSize, int analyzeSize) {
        TicketSummaryResponseDTO ticket = ticketLedgerService.getSummary(username, ticketSize);
        List<AnalyzeJobListItemDTO> jobs = analyzeJobService.getMyJobs(username, analyzeSize).jobs();
        return new MyPageSummaryResponseDTO(username, ticket, jobs);
    }
}
