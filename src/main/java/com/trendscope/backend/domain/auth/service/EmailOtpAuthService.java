package com.trendscope.backend.domain.auth.service;

import com.trendscope.backend.domain.auth.dto.EmailOtpRequestDTO;
import com.trendscope.backend.domain.auth.dto.EmailOtpVerifyRequestDTO;
import com.trendscope.backend.domain.user.dto.AuthLoginResponseDTO;
import com.trendscope.backend.domain.user.dto.UserResponseDTO;
import com.trendscope.backend.domain.user.entity.UserEntity;
import com.trendscope.backend.domain.user.entity.enums.SocialProviderType;
import com.trendscope.backend.domain.user.entity.enums.UserRoleType;
import com.trendscope.backend.domain.user.repository.UserRepository;
import com.trendscope.backend.global.jwt.service.JwtService;
import com.trendscope.backend.global.jwt.service.RedisService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailOtpAuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RedisService redisService;
    private final EmailOtpDeliveryService emailOtpDeliveryService;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Value("${app.auth.email-otp.ttl-seconds:300}")
    private long otpTtlSeconds;

    @Value("${app.auth.email-otp.cooldown-seconds:60}")
    private long otpCooldownSeconds;

    @Value("${app.auth.email-otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.auth.email-otp.pepper:change-me-in-production}")
    private String otpPepper;

    @Value("${JWT_REFRESH_EXPIRATION_MS}")
    private long refreshTokenExpirationMs;

    public void requestOtp(EmailOtpRequestDTO dto) {
        String normalizedEmail = normalizeEmail(dto.getEmail());
        String cooldownKey = otpCooldownKey(normalizedEmail);
        if (redisService.getStringValue(cooldownKey) != null) {
            throw new IllegalArgumentException("인증 코드는 잠시 후 다시 요청해주세요.");
        }

        String otpCode = generateOtpCode();
        String otpHash = hashOtp(normalizedEmail, otpCode);

        redisService.setStringValue(otpKey(normalizedEmail), otpHash, Duration.ofSeconds(otpTtlSeconds));
        redisService.setStringValue(cooldownKey, "1", Duration.ofSeconds(otpCooldownSeconds));
        redisService.deleteKey(otpAttemptKey(normalizedEmail));

        emailOtpDeliveryService.sendOtpCode(normalizedEmail, otpCode);
    }

    @Transactional
    public AuthLoginResponseDTO verifyOtp(EmailOtpVerifyRequestDTO dto, HttpServletResponse response) {
        String normalizedEmail = normalizeEmail(dto.getEmail());
        String savedHash = redisService.getStringValue(otpKey(normalizedEmail));
        if (savedHash == null) {
            throw new IllegalArgumentException("인증 코드가 만료되었거나 존재하지 않습니다.");
        }

        String attemptKey = otpAttemptKey(normalizedEmail);
        String inputHash = hashOtp(normalizedEmail, dto.getCode());
        if (!savedHash.equals(inputHash)) {
            Long attempts = redisService.increment(attemptKey, Duration.ofSeconds(otpTtlSeconds));
            if (attempts != null && attempts >= maxAttempts) {
                redisService.deleteKey(otpKey(normalizedEmail));
                redisService.deleteKey(attemptKey);
                throw new IllegalArgumentException("인증 시도 횟수를 초과했습니다. 다시 요청해주세요.");
            }
            throw new IllegalArgumentException("인증 코드가 올바르지 않습니다.");
        }

        redisService.deleteKey(otpKey(normalizedEmail));
        redisService.deleteKey(attemptKey);

        UserEntity user = findOrCreateOtpUser(normalizedEmail);

        String username = user.getUsername();
        String accessToken = jwtService.createAccessToken(username);
        String refreshToken = jwtService.createRefreshToken(username);
        String deviceId = normalizeDeviceId(dto.getDeviceId());
        jwtService.addRefresh(username, refreshToken, deviceId);
        setRefreshCookie(response, refreshToken);

        UserResponseDTO userResponse = new UserResponseDTO(
                user.getUsername(),
                user.isSocialAccount(),
                user.getEmail(),
                user.getTicketBalance()
        );
        return new AuthLoginResponseDTO(accessToken, userResponse);
    }

    private UserEntity findOrCreateOtpUser(String email) {
        return userRepository.findBySocialProviderTypeAndProviderUserId(SocialProviderType.EMAIL_OTP, email)
                .orElseGet(() -> {
            String username = generateOtpUsername();
            UserEntity newUser = UserEntity.builder()
                    .username(username)
                    .isLock(false)
                    .socialProviderType(SocialProviderType.EMAIL_OTP)
                    .providerUserId(email)
                    .roleType(UserRoleType.USER)
                    .email(email)
                    .quickTicketBalance(1)
                    .premiumTicketBalance(0)
                    .build();
            return userRepository.save(newUser);
        });
    }

    private String generateOtpCode() {
        int number = SECURE_RANDOM.nextInt(1_000_000);
        return String.format("%06d", number);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("이메일을 입력해주세요.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return "unknown-device-id";
        }
        return deviceId;
    }

    private String generateOtpUsername() {
        for (int i = 0; i < 8; i++) {
            String candidate = "OTP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("OTP 사용자명 생성에 실패했습니다.");
    }

    private String hashOtp(String email, String code) {
        String raw = email + ":" + code + ":" + otpPepper;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("OTP 해시 알고리즘 초기화 실패", e);
        }
    }

    private String otpKey(String email) {
        return "OTP:" + email;
    }

    private String otpAttemptKey(String email) {
        return "OTP_ATTEMPT:" + email;
    }

    private String otpCooldownKey(String email) {
        return "OTP_COOLDOWN:" + email;
    }

    private void setRefreshCookie(HttpServletResponse response, String refreshToken) {
        Cookie refreshCookie = new Cookie("refresh_token", refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(false);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge((int) (refreshTokenExpirationMs / 1000));
        response.addCookie(refreshCookie);
    }
}
