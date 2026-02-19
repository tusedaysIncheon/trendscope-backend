package com.trendscope.backend.domain.user.repository;

import com.trendscope.backend.domain.user.entity.TicketLedgerEntity;
import com.trendscope.backend.domain.user.entity.enums.TicketLedgerReason;
import com.trendscope.backend.domain.user.entity.enums.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface TicketLedgerRepository extends JpaRepository<TicketLedgerEntity, Long> {
    Optional<TicketLedgerEntity> findFirstByUserIdAndTicketTypeAndReasonAndRefId(
            Long userId,
            TicketType ticketType,
            TicketLedgerReason reason,
            String refId
    );
    Page<TicketLedgerEntity> findByUserIdOrderByCreatedDateDesc(Long userId, Pageable pageable);
}
