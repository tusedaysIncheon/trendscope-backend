package com.trendscope.backend.domain.user.entity;

import com.trendscope.backend.domain.user.entity.enums.TicketLedgerReason;
import com.trendscope.backend.domain.user.entity.enums.TicketType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "ticket_ledger",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ticket_ledger_user_type_reason_ref_id",
                        columnNames = {"user_id", "ticket_type", "reason", "ref_id"}
                )
        },
        indexes = {
                @Index(name = "idx_ticket_ledger_user_type_created", columnList = "user_id,ticket_type,created_date")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketLedgerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "delta", nullable = false)
    private Integer delta;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private TicketLedgerReason reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_type", nullable = false)
    private TicketType ticketType;

    @Column(name = "ref_id")
    private String refId;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;
}
