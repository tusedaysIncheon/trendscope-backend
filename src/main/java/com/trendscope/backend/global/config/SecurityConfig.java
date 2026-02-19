package com.trendscope.backend.global.config;

import com.trendscope.backend.domain.user.entity.enums.UserRoleType;
import com.trendscope.backend.global.filter.JWTFilter;
import com.trendscope.backend.global.filter.RateLimitFilter;
import com.trendscope.backend.global.security.service.CustomOAuth2UserService;
import com.trendscope.backend.global.security.service.CustomUserDetailsService;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        private final AuthenticationSuccessHandler socialLoginSuccessHandler;
        private final LettuceBasedProxyManager<byte[]> proxyManager;
        private final RedisTemplate<String, Object> redisTemplate;
        private final CustomUserDetailsService customUserDetailsService;
        private final CustomOAuth2UserService customOAuth2UserService;

        @Value("${cors.allowed-origins}")
        private List<String> allowedOrigins;

        public SecurityConfig(
                        @Qualifier("socialSuccessHandler") AuthenticationSuccessHandler socialLoginSuccessHandler,
                        LettuceBasedProxyManager<byte[]> proxyManager, RedisTemplate<String, Object> redisTemplate,
                        CustomUserDetailsService customUserDetailsService,
                        CustomOAuth2UserService customOAuth2UserService) {
                this.socialLoginSuccessHandler = socialLoginSuccessHandler;
                this.proxyManager = proxyManager;
                this.redisTemplate = redisTemplate;
                this.customUserDetailsService = customUserDetailsService;
                this.customOAuth2UserService = customOAuth2UserService;
        }

        @Bean
        public DaoAuthenticationProvider daoAuthenticationProvider() {
                DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
                provider.setUserDetailsService(customUserDetailsService);
                provider.setPasswordEncoder(passwordEncoder());
                return provider;
        }

        // 커스텀 자체 로그인 필터를 위한 AuthenticationManager Bean 수동 등록
        @Bean
        public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
                return configuration.getAuthenticationManager();
        }

        // 권한 계층 나누는 Bean
        @Bean
        public RoleHierarchy roleHierarchy() {
                return RoleHierarchyImpl.withRolePrefix("ROLE_")
                                .role(UserRoleType.ADMIN.name()).implies(UserRoleType.USER.name())
                                .build();
        }

        // 비밀번호 단방향(BCrypt) 암호화용 Bean
        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        // CORS 설정
        @Bean
        CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOrigins(allowedOrigins);
                configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
                configuration.setAllowedHeaders(List.of("*"));
                configuration.setAllowCredentials(true);
                configuration.setExposedHeaders(List.of("Authorization", "Set-Cookie"));
                configuration.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }

        // securityFilterChain
        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

                // CSRF 보안필터 x
                http
                                .csrf(AbstractHttpConfigurer::disable);

                // CORS 설정
                // FE와 BE가 다른 오리진을 가진 경우 셋팅해야함 (react + spring)
                http
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()));
                // 기본 로그아웃 필터 + 커스텀 Refresh 토큰 삭제 핸들러 추가
                // http
                // .logout(logout -> logout
                // .addLogoutHandler(new RefreshTokenLogoutHandler(jwtService)));
                //

                // 기본 form 기반 인증 필터 disable
                http
                                .formLogin(AbstractHttpConfigurer::disable);

                // 기본 basic 인증 필터 disable ->

                http
                                .httpBasic(AbstractHttpConfigurer::disable);

                http
                                .requestCache(cache -> cache
                                                .requestCache(new NullRequestCache()));

                // OAuth2 인증용
                http
                                .oauth2Login(oauth2 -> oauth2
                                                .userInfoEndpoint(userInfo -> userInfo
                                                                .userService(customOAuth2UserService))
                                                .successHandler(socialLoginSuccessHandler));

                // 인가

                http
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(
                                                                "/swagger-ui.html",
                                                                "/swagger-ui/**",
                                                                "/v3/api-docs/**")
                                                .permitAll()
                                                .requestMatchers("/jwt/refresh", "/jwt/exchange").permitAll()
                                                .requestMatchers(HttpMethod.POST, "/v*/auth/email-otp/**").permitAll()
                                                .requestMatchers(HttpMethod.POST, "/v*/payments/creem/webhook").permitAll()
                                                .requestMatchers(HttpMethod.POST, "/v*/payments/creem/checkout").hasRole(UserRoleType.USER.name())
                                                .requestMatchers(HttpMethod.POST, "/v*/user/exist", "/v*/user",
                                                                "/v*/user/login")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.POST, "/v*/s3/**")
                                                .hasRole(UserRoleType.USER.name())
                                                .requestMatchers(HttpMethod.POST, "/v*/tickets/**")
                                                .hasRole(UserRoleType.USER.name())
                                                .requestMatchers(HttpMethod.GET, "/v*/tickets/**")
                                                .hasRole(UserRoleType.USER.name())
                                                .requestMatchers(HttpMethod.POST, "/v*/dev/analyze/one-shot")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.POST, "/v*/analyze/**")
                                                .hasRole(UserRoleType.USER.name())
                                                .requestMatchers(HttpMethod.GET, "/v*/analyze/**")
                                                .hasRole(UserRoleType.USER.name())
                                                .requestMatchers(HttpMethod.GET, "/v*/mypage/**")
                                                .hasRole(UserRoleType.USER.name())
                                                .requestMatchers(HttpMethod.POST, "/v*/measurement/**")
                                                .hasRole(UserRoleType.USER.name())
                                                .requestMatchers(HttpMethod.GET, "/v*/measurement/**")
                                                .hasRole(UserRoleType.USER.name())
                                                .requestMatchers(HttpMethod.GET, "/v*/user", "/v*/user-details")
                                                .hasRole(UserRoleType.USER.name())
                                                .requestMatchers(HttpMethod.PUT, "/v*/user", "/v*/user-details")
                                                .hasRole(UserRoleType.USER.name())
                                                .requestMatchers(HttpMethod.DELETE, "/v*/user", "/v*/user-details")
                                                .hasRole(UserRoleType.USER.name())
                                                .requestMatchers(HttpMethod.PATCH, "/v*/user", "/v*/user-details")
                                                .hasRole(UserRoleType.USER.name())
                                                .anyRequest().authenticated());
                http
                                .exceptionHandling(
                                                e -> e.authenticationEntryPoint((request, response, authException) -> {
                                                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                                                })
                                                                .accessDeniedHandler((request, response,
                                                                                accessDeniedException) -> {
                                                                        response.sendError(
                                                                                        HttpServletResponse.SC_FORBIDDEN);
                                                                })

                                );

                // bucket 필터 추가
                http
                                .addFilterBefore(new RateLimitFilter(proxyManager, redisTemplate),
                                                UsernamePasswordAuthenticationFilter.class);

                // 커스텀 필터 추가
                http
                                .addFilterBefore(new JWTFilter(customUserDetailsService),
                                                UsernamePasswordAuthenticationFilter.class);

                // http
                // .addFilterBefore(new
                // LoginFilter(authenticationManager(authenticationConfiguration),
                // loginSuccessHandler), UsernamePasswordAuthenticationFilter.class);

                http
                                .securityContext(context -> context
                                                .securityContextRepository(new NullSecurityContextRepository()));

                // 세션필터 설정 -> JWT 방식은 세션을 꺼야됌 클라이언트의 세션정보를 서버에 저장하지않음 토큰을 계속 확인하면서 처리하는 API

                http
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                                                .sessionFixation(sessionFixation -> sessionFixation.none()));

                return http.build();

        }

}
