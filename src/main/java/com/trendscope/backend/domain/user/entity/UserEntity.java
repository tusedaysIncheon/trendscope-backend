package com.trendscope.backend.domain.user.entity;

import com.trendscope.backend.domain.user.entity.enums.SocialProviderType;
import com.trendscope.backend.domain.user.entity.enums.TicketType;
import com.trendscope.backend.domain.user.entity.enums.UserRoleType;
import com.trendscope.backend.domain.user.dto.UserRequestDTO;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "user_",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_provider_type_user_id",
                        columnNames = {"social_provider_type", "provider_user_id"}
                )
        }
)
@Check(constraints = "quick_ticket_balance >= 0 AND premium_ticket_balance >= 0")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(name = "username", unique = true, nullable = false, updatable = false)
    private String username;

    @Builder.Default
    @Column(name = "is_lock", nullable = false)
    private Boolean isLock = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_provider_type", nullable = false)
    private SocialProviderType socialProviderType;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", nullable = false)
    private UserRoleType roleType;

    @Builder.Default
    @Column(name = "quick_ticket_balance", nullable = false, columnDefinition = "integer not null default 1")
    private Integer quickTicketBalance = 1;

    @Builder.Default
    @Column(name = "premium_ticket_balance", nullable = false, columnDefinition = "integer not null default 0")
    private Integer premiumTicketBalance = 0;

    @Column(name = "email", nullable = false)
    private String email;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true) // 혹은 EAGER
    private UserDetailsEntity userDetails;

    //일반 회원정보 업데이트 용
    public void updateUser(UserRequestDTO dto) {
        String normalizedEmail = dto.getEmail().trim().toLowerCase(Locale.ROOT);
        this.email = normalizedEmail;
        if (this.socialProviderType == SocialProviderType.EMAIL_OTP) {
            this.providerUserId = normalizedEmail;
        }
    }

    public void updateEmail(String email) {
        this.email = email;
    }

    public boolean isSocialAccount() {
        return this.socialProviderType != SocialProviderType.EMAIL_OTP;
    }

    public int getTicketBalance() {
        int quick = this.quickTicketBalance == null ? 0 : this.quickTicketBalance;
        int premium = this.premiumTicketBalance == null ? 0 : this.premiumTicketBalance;
        return quick + premium;
    }

    public int getTicketBalance(TicketType ticketType) {
        return switch (ticketType) {
            case QUICK -> this.quickTicketBalance == null ? 0 : this.quickTicketBalance;
            case PREMIUM -> this.premiumTicketBalance == null ? 0 : this.premiumTicketBalance;
        };
    }

    public void changeTicketBalance(TicketType ticketType, int delta) {
        int current = getTicketBalance(ticketType);
        int next = current + delta;
        if (next < 0) {
            throw new IllegalArgumentException("티켓 잔액은 0 미만이 될 수 없습니다.");
        }

        if (ticketType == TicketType.QUICK) {
            this.quickTicketBalance = next;
        } else {
            this.premiumTicketBalance = next;
        }
    }

}
