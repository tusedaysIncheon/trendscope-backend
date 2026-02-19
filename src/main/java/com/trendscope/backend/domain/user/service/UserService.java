package com.trendscope.backend.domain.user.service;

import com.trendscope.backend.domain.user.dto.UserResponseDTO;
import com.trendscope.backend.global.jwt.service.RedisService;
import com.trendscope.backend.domain.user.entity.UserEntity;
import com.trendscope.backend.domain.user.entity.enums.SocialProviderType;
import com.trendscope.backend.domain.user.entity.enums.UserRoleType;
import com.trendscope.backend.domain.user.dto.UserRequestDTO;
import com.trendscope.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;


@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RedisService redisService;


    //자체 로그인 회원 가입 (존재 여부 체크)
    @Transactional(readOnly = true)
    public Boolean existUser(UserRequestDTO dto) {
        return userRepository.existsByUsername(dto.getUsername());
    }

    //자체 로그인 회원 가입
    @Transactional
    public UserResponseDTO addUser(UserRequestDTO dto) {
        if (userRepository.existsByUsername(dto.getUsername())) {                 //또 검증하는 이유는 db에 접근하지 않고 거르는 existUser 메서드가 있음에도 불구하고 db에 바로 접근을 하는 경우도 있음
            throw new IllegalArgumentException("이미 유저가 존재합니다.");
        }
        UserEntity entity = UserEntity.builder()                                  //new 생성자로 만들면 @Setter로 접근 가능함(불변성 x) 빌더가 훨씬 간단하고 불변성을 지킬 수 있음.
                .username(dto.getUsername())
                .socialProviderType(SocialProviderType.EMAIL_OTP)
                .providerUserId(dto.getEmail().trim().toLowerCase(Locale.ROOT))
                .roleType(UserRoleType.USER)
                .quickTicketBalance(1)
                .premiumTicketBalance(0)
                .email(dto.getEmail().trim().toLowerCase(Locale.ROOT))
                .isLock(false)
                .build();

        UserEntity saved = userRepository.save(entity);

        return new UserResponseDTO(
                saved.getUsername(),
                saved.isSocialAccount(),
                saved.getEmail(),
                saved.getTicketBalance()
        );

    }

    //자체 로그인 회원 정보 수정
    @Transactional
    public Long updateUser(UserRequestDTO dto) {

        //본인만 수정 가능 검증
        String sessionUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!sessionUsername.equals(dto.getUsername())) {
            throw new AccessDeniedException("본인 계정만 수정가능합니다.");
        }
        //조회
        UserEntity entity = userRepository.findByUsernameAndIsLock(dto.getUsername(), false)
                .orElseThrow(() -> new UsernameNotFoundException(dto.getUsername()));

        //회원정보 수정
        entity.updateUser(dto);
        return userRepository.save(entity).getId();

    }

    //자체/소셜 로그인 회원 탈퇴
    @Transactional
    public void deleteUser(UserRequestDTO dto) throws AccessDeniedException {
        //본인 또는 어드민만 가능하게 검증 작업
        SecurityContext context = SecurityContextHolder.getContext();
        String sessionUsername = context.getAuthentication().getName();
        String sessionRole = context.getAuthentication().getAuthorities().iterator().next().getAuthority();

        boolean isOwner = sessionUsername.equals(dto.getUsername());
        boolean isAdmin = sessionRole.equals("ROLE_" + UserRoleType.ADMIN.name());

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("본인 혹은 관리자만 삭제할 수 있습니다.");
        }

        //Refresh 토큰 제거 (참조 제약 or 캐시 일관성 문제로 먼저 삭제)
        redisService.deleteAllRefreshTokens(dto.getUsername());

        //유저 삭제
        userRepository.deleteByUsername(dto.getUsername());


    }

    //자체 /소셜 유저 정보조회
    @Transactional(readOnly = true)
    public UserResponseDTO readUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        if(username == null){
            throw new AccessDeniedException("존재하지 않는 유저입니다.");
        }

        UserEntity entity = userRepository.findByUsernameAndIsLock(username, false)
                .orElseThrow(() -> new UsernameNotFoundException("해당 유저를 찾을 수 없습니다" + username));

        return new UserResponseDTO(
                username,
                entity.isSocialAccount(),
                entity.getEmail(),
                entity.getTicketBalance()
        );
    }



}
