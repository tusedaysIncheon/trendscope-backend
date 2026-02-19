package com.trendscope.backend.global.security.custom;

import com.trendscope.backend.domain.user.entity.UserEntity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

@Getter
@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;

    private final UserEntity userEntity;

    public Long getUserId() {
        return userEntity.getId();
    }

    public String getEmail() {
        return userEntity.getEmail();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (userEntity.getRoleType() == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + userEntity.getRoleType().name())
        );
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return userEntity.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return userEntity.getIsLock() == null || !userEntity.getIsLock();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        CustomUserDetails that = (CustomUserDetails) obj;
        return Objects.equals(getUserId(), that.getUserId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUserId());
    }

}