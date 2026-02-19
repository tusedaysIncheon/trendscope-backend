package com.trendscope.backend.domain.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.SesClientBuilder;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailOtpDeliveryService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.auth.email-otp.delivery-mode:log}")
    private String deliveryMode;

    @Value("${app.auth.email-otp.from:no-reply@trendscope.local}")
    private String fromAddress;

    @Value("${app.auth.email-otp.ses.access-key:}")
    private String sesAccessKey;

    @Value("${app.auth.email-otp.ses.secret-key:}")
    private String sesSecretKey;

    @Value("${app.auth.email-otp.ses.region:ap-northeast-2}")
    private String sesRegion;

    private volatile SesClient sesClient;

    public void sendOtpCode(String email, String code) {
        if ("ses".equalsIgnoreCase(deliveryMode)) {
            try {
                sendViaSes(email, code);
                return;
            } catch (Exception e) {
                log.error("SES 이메일 전송 실패. log 모드로 fallback 합니다. email={}", maskEmail(email), e);
            }
        }

        if ("smtp".equalsIgnoreCase(deliveryMode)) {
            JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
            if (mailSender != null) {
                try {
                    SimpleMailMessage message = new SimpleMailMessage();
                    message.setFrom(fromAddress);
                    message.setTo(email);
                    message.setSubject("[TrendScope] 이메일 인증 코드");
                    message.setText("인증 코드는 " + code + " 입니다. 5분 내에 입력해주세요.");
                    mailSender.send(message);
                    return;
                } catch (Exception e) {
                    log.error("SMTP 이메일 전송 실패. log 모드로 fallback 합니다. email={}", maskEmail(email), e);
                }
            } else {
                log.warn("JavaMailSender 빈이 없어 SMTP 전송 불가. log 모드로 fallback 합니다.");
            }
        }

        // 개발/로컬 fallback: 콘솔 로그로 OTP 확인
        log.info("[EMAIL_OTP] email={} code={}", maskEmail(email), code);
    }

    private void sendViaSes(String email, String code) {
        SesClient client = getOrCreateSesClient();
        SendEmailRequest request = SendEmailRequest.builder()
                .source(fromAddress)
                .destination(Destination.builder().toAddresses(email).build())
                .message(Message.builder()
                        .subject(Content.builder().charset("UTF-8").data("[TrendScope] 이메일 인증 코드").build())
                        .body(Body.builder()
                                .text(Content.builder()
                                        .charset("UTF-8")
                                        .data("인증 코드는 " + code + " 입니다. 5분 내에 입력해주세요.")
                                        .build())
                                .build())
                        .build())
                .build();

        SendEmailResponse response = client.sendEmail(request);
        log.info("SES OTP email sent. email={} messageId={}", maskEmail(email), response.messageId());
    }

    private SesClient getOrCreateSesClient() {
        if (sesClient != null) {
            return sesClient;
        }

        synchronized (this) {
            if (sesClient != null) {
                return sesClient;
            }

            SesClientBuilder builder = SesClient.builder()
                    .region(Region.of(sesRegion));

            if (hasText(sesAccessKey) && hasText(sesSecretKey)) {
                builder.credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(sesAccessKey, sesSecretKey)
                        )
                );
            } else {
                builder.credentialsProvider(DefaultCredentialsProvider.create());
            }

            sesClient = builder.build();
            return sesClient;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        int at = normalized.indexOf("@");
        if (at <= 1) {
            return "***";
        }
        return normalized.substring(0, 1) + "***" + normalized.substring(at - 1);
    }

    @PreDestroy
    public void closeSesClient() {
        if (sesClient != null) {
            sesClient.close();
        }
    }
}
