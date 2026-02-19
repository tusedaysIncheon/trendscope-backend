package com.trendscope.backend.domain.user.repository;

import com.trendscope.backend.domain.user.entity.UserDetailsEntity;
import com.trendscope.backend.domain.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface UserDetailsRepository extends JpaRepository<UserDetailsEntity,Long> {

    //ID-> 상세정보
    Optional<UserDetailsEntity> findByUser(UserEntity user);

    boolean existsByNickname(String nickname);

    UserEntity user(UserEntity user);
}
