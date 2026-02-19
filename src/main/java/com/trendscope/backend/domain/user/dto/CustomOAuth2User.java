package com.trendscope.backend.domain.user.dto;

import com.trendscope.backend.domain.user.entity.UserEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class CustomOAuth2User implements OAuth2User {

    private final Map<String, Object> attributes;
    private final Collection<? extends GrantedAuthority> authorities;
    private final String username;
    private final UserEntity userEntity;

    public CustomOAuth2User(Map<String, Object> attributes,
            List<GrantedAuthority> authorities,
            String username,
            UserEntity userEntity) {
        this.attributes = attributes;
        this.authorities = authorities;
        this.username = username;
        this.userEntity = userEntity;

    }

    public UserEntity getUserEntity() {
        return userEntity;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return username;
    }
}
