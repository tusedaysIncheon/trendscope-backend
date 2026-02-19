package com.trendscope.backend.domain.user.dto;

import com.trendscope.backend.domain.user.entity.UserDetailsEntity;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Year;


@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDetailsResponseDTO {


    private Long id;
    private String nickname;

    private BigDecimal height;

    private BigDecimal weight;

    public UserDetailsResponseDTO(UserDetailsEntity entity) {
        this.id = entity.getId();
        this.nickname = entity.getNickname();
        this.height = entity.getHeight();
        this.weight = entity.getWeight();


    }

}
