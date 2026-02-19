package com.trendscope.backend.domain.user.entity;

import com.trendscope.backend.domain.user.entity.enums.Gender;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "user_details")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserDetailsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    @Column(nullable = false, length = 20, unique = true)
    private String nickname;

    @Column(nullable = false)
    private Gender gender;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal height; // 예: 185.50 (전체 5자리, 소수점 2자리)

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weight; // 예: 075.25 (전체 5자리, 소수점 2자리)




    // Setter 대체
    public void updateProfile(String nickname, String imageUrl, String introduce) {
        if (nickname != null) this.nickname = nickname;

    }

}



