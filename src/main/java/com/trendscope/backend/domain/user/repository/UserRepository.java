package com.trendscope.backend.domain.user.repository;

import com.trendscope.backend.domain.user.entity.UserEntity;
import com.trendscope.backend.domain.user.entity.enums.SocialProviderType;
import com.trendscope.backend.domain.user.entity.enums.UserRoleType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;


public interface UserRepository extends JpaRepository<UserEntity, Long> {

    boolean existsByUsername(String username);

    Optional<UserEntity> findByUsernameAndIsLock(String username, Boolean isLock);
    Optional<UserEntity> findByUsername(String username);
    Optional<UserEntity> findFirstByEmailOrderByIdAsc(String email);
    Optional<UserEntity> findBySocialProviderTypeAndProviderUserId(SocialProviderType socialProviderType, String providerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserEntity u WHERE u.username = :username AND u.isLock = false")
    Optional<UserEntity> findByUsernameForUpdate(String username);

    //JWT 발급용 -> 롤타입 확인용
    @Query("SELECT u.roleType FROM UserEntity u WHERE u.username = :username")
    Optional<UserRoleType> findRoleTypeByUsername(String username);

    void deleteByUsername(String username);

}
