package com.trendscope.backend.domain.user.service;

import com.trendscope.backend.domain.user.dto.TicketLedgerItemResponseDTO;
import com.trendscope.backend.domain.user.dto.TicketSummaryResponseDTO;
import com.trendscope.backend.domain.user.dto.TicketTransactionRequestDTO;
import com.trendscope.backend.domain.user.dto.TicketTransactionResponseDTO;
import com.trendscope.backend.domain.user.entity.TicketLedgerEntity;
import com.trendscope.backend.domain.user.entity.UserEntity;
import com.trendscope.backend.domain.user.entity.enums.TicketLedgerReason;
import com.trendscope.backend.domain.user.entity.enums.TicketType;
import com.trendscope.backend.domain.user.repository.TicketLedgerRepository;
import com.trendscope.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TicketLedgerService {

    private final TicketLedgerRepository ticketLedgerRepository;
    private final UserRepository userRepository;

    private static final int DEFAULT_QUANTITY = 1;
    private static final int MAX_LEDGER_SIZE = 100;

    @Transactional
    public TicketTransactionResponseDTO purchase(String username, TicketTransactionRequestDTO dto) {
        int quantity = normalizeQuantity(dto.getQuantity());
        return apply(username, dto.getTicketType(), quantity, TicketLedgerReason.PURCHASE, normalizeRefId(dto.getRefId()));
    }

    @Transactional
    public TicketTransactionResponseDTO purchase(String username, TicketType ticketType, int quantity, String refId) {
        int normalized = normalizeQuantity(quantity);
        return apply(username, ticketType, normalized, TicketLedgerReason.PURCHASE, normalizeRefId(refId));
    }

    @Transactional
    public TicketTransactionResponseDTO use(String username, TicketTransactionRequestDTO dto) {
        int quantity = normalizeQuantity(dto.getQuantity());
        return apply(username, dto.getTicketType(), -quantity, TicketLedgerReason.USE, normalizeRefId(dto.getRefId()));
    }

    @Transactional
    public TicketTransactionResponseDTO refund(String username, TicketTransactionRequestDTO dto) {
        int quantity = normalizeQuantity(dto.getQuantity());
        return apply(username, dto.getTicketType(), quantity, TicketLedgerReason.REFUND, normalizeRefId(dto.getRefId()));
    }

    @Transactional
    public TicketTransactionResponseDTO holdForAnalyze(String username, TicketType ticketType, String refId) {
        return apply(username, ticketType, -1, TicketLedgerReason.HOLD, normalizeRefId(refId));
    }

    @Transactional
    public TicketTransactionResponseDTO consumeHeldForAnalyze(String username, TicketType ticketType, String refId) {
        String normalizedRefId = normalizeRefId(refId);
        UserEntity user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        TicketLedgerEntity duplicate = ticketLedgerRepository
                .findFirstByUserIdAndTicketTypeAndReasonAndRefId(
                        user.getId(),
                        ticketType,
                        TicketLedgerReason.CONSUME,
                        normalizedRefId
                )
                .orElse(null);
        if (duplicate != null) {
            return toResponse(duplicate, user, false);
        }

        TicketLedgerEntity hold = ticketLedgerRepository
                .findFirstByUserIdAndTicketTypeAndReasonAndRefId(
                        user.getId(),
                        ticketType,
                        TicketLedgerReason.HOLD,
                        normalizedRefId
                )
                .orElse(null);
        if (hold == null) {
            throw new IllegalArgumentException("예약된 티켓(HOLD)이 없어 확정 차감할 수 없습니다.");
        }

        TicketLedgerEntity released = ticketLedgerRepository
                .findFirstByUserIdAndTicketTypeAndReasonAndRefId(
                        user.getId(),
                        ticketType,
                        TicketLedgerReason.RELEASE,
                        normalizedRefId
                )
                .orElse(null);
        if (released != null) {
            throw new IllegalArgumentException("이미 해제(RELEASE)된 티켓은 확정 차감할 수 없습니다.");
        }

        TicketLedgerEntity ledger = TicketLedgerEntity.builder()
                .user(user)
                .delta(0)
                .reason(TicketLedgerReason.CONSUME)
                .ticketType(ticketType)
                .refId(normalizedRefId)
                .build();
        ticketLedgerRepository.save(ledger);

        return toResponse(ledger, user, true);
    }

    @Transactional
    public TicketTransactionResponseDTO releaseHeldForAnalyze(String username, TicketType ticketType, String refId) {
        String normalizedRefId = normalizeRefId(refId);
        UserEntity user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        TicketLedgerEntity duplicate = ticketLedgerRepository
                .findFirstByUserIdAndTicketTypeAndReasonAndRefId(
                        user.getId(),
                        ticketType,
                        TicketLedgerReason.RELEASE,
                        normalizedRefId
                )
                .orElse(null);
        if (duplicate != null) {
            return toResponse(duplicate, user, false);
        }

        TicketLedgerEntity hold = ticketLedgerRepository
                .findFirstByUserIdAndTicketTypeAndReasonAndRefId(
                        user.getId(),
                        ticketType,
                        TicketLedgerReason.HOLD,
                        normalizedRefId
                )
                .orElse(null);
        if (hold == null) {
            throw new IllegalArgumentException("예약된 티켓(HOLD)이 없어 해제할 수 없습니다.");
        }

        TicketLedgerEntity consumed = ticketLedgerRepository
                .findFirstByUserIdAndTicketTypeAndReasonAndRefId(
                        user.getId(),
                        ticketType,
                        TicketLedgerReason.CONSUME,
                        normalizedRefId
                )
                .orElse(null);
        if (consumed != null) {
            throw new IllegalArgumentException("이미 확정 차감(CONSUME)된 티켓은 해제할 수 없습니다.");
        }

        user.changeTicketBalance(ticketType, 1);

        TicketLedgerEntity ledger = TicketLedgerEntity.builder()
                .user(user)
                .delta(1)
                .reason(TicketLedgerReason.RELEASE)
                .ticketType(ticketType)
                .refId(normalizedRefId)
                .build();
        ticketLedgerRepository.save(ledger);

        return toResponse(ledger, user, true);
    }

    @Transactional(readOnly = true)
    public TicketSummaryResponseDTO getSummary(String username, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_LEDGER_SIZE));
        UserEntity user = userRepository.findByUsernameAndIsLock(username, false)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        Pageable pageable = PageRequest.of(0, safeSize);
        List<TicketLedgerItemResponseDTO> items = ticketLedgerRepository
                .findByUserIdOrderByCreatedDateDesc(user.getId(), pageable)
                .stream()
                .map(ledger -> new TicketLedgerItemResponseDTO(
                        ledger.getId(),
                        ledger.getTicketType(),
                        ledger.getReason(),
                        ledger.getDelta(),
                        ledger.getRefId(),
                        ledger.getCreatedDate()
                ))
                .toList();

        return new TicketSummaryResponseDTO(
                user.getUsername(),
                user.getQuickTicketBalance(),
                user.getPremiumTicketBalance(),
                user.getTicketBalance(),
                items
        );
    }

    private TicketTransactionResponseDTO apply(
            String username,
            TicketType ticketType,
            int delta,
            TicketLedgerReason reason,
            String refId
    ) {
        if (ticketType == null) {
            throw new IllegalArgumentException("ticketType은 필수입니다.");
        }

        UserEntity user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        TicketLedgerEntity duplicate = ticketLedgerRepository
                .findFirstByUserIdAndTicketTypeAndReasonAndRefId(user.getId(), ticketType, reason, refId)
                .orElse(null);
        if (duplicate != null) {
            return toResponse(duplicate, user, false);
        }

        if (delta < 0) {
            int required = Math.abs(delta);
            int current = user.getTicketBalance(ticketType);
            if (current < required) {
                throw new IllegalArgumentException("보유 티켓이 부족합니다.");
            }
        }

        if (delta != 0) {
            user.changeTicketBalance(ticketType, delta);
        }

        TicketLedgerEntity ledger = TicketLedgerEntity.builder()
                .user(user)
                .delta(delta)
                .reason(reason)
                .ticketType(ticketType)
                .refId(refId)
                .build();
        ticketLedgerRepository.save(ledger);

        return toResponse(ledger, user, true);
    }

    private TicketTransactionResponseDTO toResponse(TicketLedgerEntity ledger, UserEntity user, boolean applied) {
        return new TicketTransactionResponseDTO(
                ledger.getId(),
                ledger.getTicketType(),
                ledger.getReason(),
                ledger.getRefId(),
                Math.abs(ledger.getDelta()),
                ledger.getDelta(),
                user.getQuickTicketBalance(),
                user.getPremiumTicketBalance(),
                user.getTicketBalance(),
                applied,
                ledger.getCreatedDate()
        );
    }

    private int normalizeQuantity(Integer quantity) {
        int value = quantity == null ? DEFAULT_QUANTITY : quantity;
        if (value <= 0) {
            throw new IllegalArgumentException("quantity는 1 이상이어야 합니다.");
        }
        return value;
    }

    private int normalizeQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity는 1 이상이어야 합니다.");
        }
        return quantity;
    }

    private String normalizeRefId(String refId) {
        if (refId == null || refId.isBlank()) {
            throw new IllegalArgumentException("refId는 필수입니다.");
        }
        return refId.trim().toLowerCase(Locale.ROOT);
    }
}
