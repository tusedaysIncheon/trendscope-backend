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
import java.util.concurrent.TimeUnit;

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

    @Value("${app.auth.email-otp.ttl-seconds:300}")
    private long otpTtlSeconds;

    @Value("${app.auth.email-otp.brand-name:TrendScope}")
    private String brandName;

    @Value("${app.auth.email-otp.brand-logo-url:}")
    private String brandLogoUrl;

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

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
                    message.setSubject(buildSubject());
                    message.setText(buildTextBody(code));
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
                        .subject(Content.builder().charset("UTF-8").data(buildSubject()).build())
                        .body(Body.builder()
                                .text(Content.builder()
                                        .charset("UTF-8")
                                        .data(buildTextBody(code))
                                        .build())
                                .html(Content.builder()
                                        .charset("UTF-8")
                                        .data(buildHtmlBody(code))
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

    private String buildSubject() {
        return "[" + brandName + "] 이메일 인증 코드";
    }

    private String buildTextBody(String code) {
        long ttlMinutes = Math.max(1L, TimeUnit.SECONDS.toMinutes(Math.max(1L, otpTtlSeconds)));
        return String.format(
                "%s 이메일 인증 코드 안내\n\n인증 코드: %s\n유효 시간: %d분\n\n본인이 요청하지 않았다면 이 메일을 무시해주세요.",
                brandName,
                code,
                ttlMinutes
        );
    }

    private String buildHtmlBody(String code) {
        long ttlMinutes = Math.max(1L, TimeUnit.SECONDS.toMinutes(Math.max(1L, otpTtlSeconds)));
        String brandWordmark = brandName.trim().isEmpty()
                ? "TRENDSCOPE"
                : brandName.trim().toUpperCase(Locale.ROOT);
        String logoUrl = buildBrandLogoUrl();
        return """
                <!doctype html>
                <html lang="ko">
                  <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>%s 이메일 인증 코드</title>
                  </head>
                  <body style="margin:0;padding:0;background:#f7faf9;font-family:'Inter','Noto Sans KR',-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;color:#1f2937;">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:24px 12px;">
                      <tr>
                        <td align="center">
                          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:560px;background:#ffffff;border:1px solid #e5e7eb;border-radius:14px;overflow:hidden;">
                            <tr>
                              <td style="padding:16px 24px;background:linear-gradient(135deg,#3c91e6,#2f7fd4);color:#ffffff;">
                                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">
                                  <tr>
                                    <td style="vertical-align:middle;">
                                      <img src="%s" alt="%s Logo" width="34" height="34" style="display:inline-block;width:34px;height:34px;vertical-align:middle;border-radius:6px;object-fit:contain;background:#ffffff;padding:3px;box-sizing:border-box;" />
                                      <span style="display:inline-block;vertical-align:middle;margin-left:10px;font-size:17px;font-weight:900;letter-spacing:0.04em;line-height:1;">%s</span>
                                    </td>
                                  </tr>
                                </table>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:24px;">
                                <p style="margin:0 0 12px 0;font-size:15px;line-height:1.6;">
                                  아래 인증 코드를 입력해 로그인을 완료해주세요.
                                </p>
                                <div style="margin:20px 0;padding:16px 20px;border:1px dashed #99f6e4;border-radius:10px;background:#f0fdfa;text-align:center;">
                                  <span style="font-size:30px;letter-spacing:6px;font-weight:700;color:#0f766e;">%s</span>
                                </div>
                                <p style="margin:0 0 8px 0;font-size:14px;color:#374151;">
                                  유효 시간: %d분
                                </p>
                                <p style="margin:0;font-size:13px;color:#6b7280;line-height:1.6;">
                                  본인이 요청하지 않은 인증 메일이라면 이 메일을 무시해주세요.
                                </p>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """.formatted(brandName, logoUrl, brandName, brandWordmark, code, ttlMinutes);
    }

    private String buildBrandLogoUrl() {
        if (hasText(brandLogoUrl)) {
            return brandLogoUrl.trim();
        }
        String base = (frontendBaseUrl == null ? "" : frontendBaseUrl.trim());
        if (base.isEmpty()) {
            return "";
        }
        if (base.endsWith("/")) {
            return base + "logo1.png";
        }
        return base + "/logo1.png";
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
