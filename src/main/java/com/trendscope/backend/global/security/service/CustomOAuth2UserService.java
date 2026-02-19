package com.trendscope.backend.global.security.service;

import com.trendscope.backend.domain.user.dto.CustomOAuth2User;
import com.trendscope.backend.domain.user.entity.UserEntity;
import com.trendscope.backend.domain.user.entity.enums.SocialProviderType;
import com.trendscope.backend.domain.user.entity.enums.UserRoleType;
import com.trendscope.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @SuppressWarnings("unchecked")
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        OAuth2User oAuth2User = super.loadUser(userRequest);

        Map<String, Object> attributes;
        String providerUserId;
        String username;
        String email;
        String role = UserRoleType.USER.name();
        String registrationId = userRequest.getClientRegistration().getRegistrationId().toUpperCase();
        SocialProviderType providerType;

        if (registrationId.equals(SocialProviderType.NAVER.name())) {
            attributes = (Map<String, Object>) oAuth2User.getAttributes().get("response");
            providerUserId = safeToString(attributes.get("id"));
            email = safeToString(attributes.get("email"));
        } else if (registrationId.equals(SocialProviderType.GOOGLE.name())) {
            attributes = (Map<String, Object>) oAuth2User.getAttributes();
            providerUserId = safeToString(attributes.get("sub"));
            email = safeToString(attributes.get("email"));
        } else if (registrationId.equals(SocialProviderType.KAKAO.name())) {
            attributes = (Map<String, Object>) oAuth2User.getAttributes();
            providerUserId = safeToString(attributes.get("id"));
            Object accountObject = attributes.get("kakao_account");
            if (!(accountObject instanceof Map<?, ?> accountMap)) {
                throw new OAuth2AuthenticationException("Kakao account payload is missing.");
            }
            email = safeToString(accountMap.get("email"));
        } else {
            throw new OAuth2AuthenticationException("Unsupported social login provider: " + registrationId);
        }

        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException("Social account email is required.");
        }
        if (providerUserId == null || providerUserId.isBlank()) {
            throw new OAuth2AuthenticationException("Social provider user id is required.");
        }

        providerType = SocialProviderType.valueOf(registrationId);
        email = email.trim().toLowerCase();
        providerUserId = providerUserId.trim();
        username = registrationId + "_" + providerUserId;

        UserEntity userEntity;

        Optional<UserEntity> entity = userRepository.findBySocialProviderTypeAndProviderUserId(providerType, providerUserId);

        if (entity.isPresent()) {
            userEntity = entity.get();
            if (!email.equals(userEntity.getEmail())) {
                userEntity.updateEmail(email);
            }
            role = userEntity.getRoleType().name();
        } else {
            userEntity = UserEntity.builder()
                    .username(username)
                    .isLock(false)
                    .socialProviderType(providerType)
                    .providerUserId(providerUserId)
                    .roleType(UserRoleType.USER)
                    .email(email)
                    .quickTicketBalance(1)
                    .premiumTicketBalance(0)
                    .build();
            userRepository.save(userEntity);
        }
        return new CustomOAuth2User(attributes, List.of(new SimpleGrantedAuthority(role)), username, userEntity);
    }

    private String safeToString(Object value) {
        return value == null ? null : value.toString();
    }
}
