package com.trendscope.backend.domain.user.service;

import com.trendscope.backend.domain.user.entity.UserDetailsEntity;
import com.trendscope.backend.domain.user.entity.UserEntity;
import com.trendscope.backend.domain.user.dto.UserDetailsRequestDTO;
import com.trendscope.backend.domain.user.dto.UserDetailsResponseDTO;
import com.trendscope.backend.domain.user.repository.UserDetailsRepository;
import com.trendscope.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserDetailService {

    private final UserRepository userRepository;
    private final UserDetailsRepository userDetailsRepository;

    @Transactional
    public void saveUserDetails(String username, UserDetailsRequestDTO dto) {

        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(()->new IllegalArgumentException("존재하지 않는 사용자입니다."));

        UserDetailsEntity existingDetail = userDetailsRepository.findByUser(user).orElse(null);

        if(existingDetail == null || !existingDetail.getNickname().equals(dto.getNickname())){
            if(userDetailsRepository.existsByNickname(dto.getNickname())){
                throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
            }

            if(existingDetail == null){
                UserDetailsEntity newDetail = UserDetailsEntity.builder()
                        .user(user)
                        .gender(dto.getGender())
                        .height(dto.getHeight())
                        .weight(dto.getWeight())
                        .nickname(dto.getNickname())
                        .build();

                userDetailsRepository.save(newDetail);
            }
        }
    }


    public Boolean existNickname(String nickname) {
        return userDetailsRepository.existsByNickname(nickname);
    }




}
