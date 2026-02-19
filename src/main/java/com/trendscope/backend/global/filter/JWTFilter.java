package com.trendscope.backend.global.filter;

import com.trendscope.backend.global.util.JWTUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JWTFilter extends OncePerRequestFilter {

    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");

        if (authorization == null || !authorization.startsWith("Bearer ")) {                    //인가가 없으면 다음필터로 넘김
            filterChain.doFilter(request,response);

            return;
        }

        String accessToken = authorization.split(" ")[1];

        if(JWTUtil.isValid(accessToken, true)){

            String username = JWTUtil.getUsername(accessToken);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } else {

            log.warn("유효하지 않은 토큰입니다. (URI: {}) - 익명 사용자로 처리됩니다.", request.getRequestURI());

        }

        filterChain.doFilter(request,response);
    }
}
