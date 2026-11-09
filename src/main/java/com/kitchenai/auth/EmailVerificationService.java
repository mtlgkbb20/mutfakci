package com.kitchenai.auth;

import com.kitchenai.common.ApiException;
import com.kitchenai.user.User;
import com.kitchenai.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int CODE_LENGTH = 6;
  private static final int MAX_ATTEMPTS = 5;
  private static final Duration CODE_TTL = Duration.ofMinutes(10);
  private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

  private final JdbcTemplate jdbc;
  private final UserRepository users;
  private final JavaMailSender mailSender;
  private final String senderAddress;
  private final byte[] hashSecret;

  public EmailVerificationService(
      JdbcTemplate jdbc,
      UserRepository users,
      JavaMailSender mailSender,
      @Value("${app.email.from}") String senderAddress,
      @Value("${security.otp.hash-secret:${jwt.secret}}") String hashSecret) {
    this.jdbc = jdbc;
    this.users = users;
    this.mailSender = mailSender;
    this.senderAddress = senderAddress;
    this.hashSecret = hashSecret.getBytes(StandardCharsets.UTF_8);
  }

  @Transactional(noRollbackFor = ApiException.class)
  public void sendCode(User user, String clientIp) {
    enforceRateLimit("email:" + user.getEmail().toLowerCase(), 3);
    enforceRateLimit("ip:" + safeIp(clientIp), 10);

    Instant now = Instant.now();
    var existing = jdbc.query(
        """
        SELECT resend_after, consumed_at
        FROM email_verification
        WHERE user_id = ?
        FOR UPDATE
        """,
        (resultSet, rowNumber) ->
            new VerificationCooldown(
                resultSet.getTimestamp("resend_after").toInstant(),
                resultSet.getTimestamp("consumed_at") == null
                    ? null
                    : resultSet.getTimestamp("consumed_at").toInstant()),
        user.getId());

    if (!existing.isEmpty()) {
      VerificationCooldown cooldown = existing.get(0);
      if (cooldown.consumedAt() == null && cooldown.resendAfter().isAfter(now)) {
        throw tooManyRequests();
      }
    }

    String code = String.format("%0" + CODE_LENGTH + "d", RANDOM.nextInt(1_000_000));
    Instant expiresAt = now.plus(CODE_TTL);
    Instant resendAfter = now.plus(RESEND_COOLDOWN);

    jdbc.update(
        """
        INSERT INTO email_verification (
          user_id, code_hash, expires_at, attempts, resend_after, consumed_at, created_at
        )
        VALUES (?, ?, ?, 0, ?, NULL, ?)
        ON CONFLICT (user_id) DO UPDATE SET
          code_hash = EXCLUDED.code_hash,
          expires_at = EXCLUDED.expires_at,
          attempts = 0,
          resend_after = EXCLUDED.resend_after,
          consumed_at = NULL,
          created_at = EXCLUDED.created_at
        """,
        user.getId(),
        hash(code),
        java.sql.Timestamp.from(expiresAt),
        java.sql.Timestamp.from(resendAfter),
        java.sql.Timestamp.from(now));

    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(senderAddress);
    message.setTo(user.getEmail());
    message.setSubject("Kitchen AI e-posta doğrulama kodun");
    message.setText(
        "Kitchen AI hesabını doğrulamak için kodun: "
            + code
            + "\n\nBu kod 10 dakika içinde sona erer. Bu isteği sen yapmadıysan bu e-postayı yok say.");

    mailSender.send(message);
  }

  @Transactional(noRollbackFor = ApiException.class)
  public User verify(String email, String code, String clientIp) {
    enforceRateLimit("verify-ip:" + safeIp(clientIp), 20);

    User user =
        users.findByEmailIgnoreCase(email == null ? "" : email.trim())
            .orElseThrow(this::invalidCode);

    // A verified account must never receive a JWT from this endpoint again.
    if (user.isEmailVerified()) {
      throw invalidCode();
    }

    var records = jdbc.query(
        """
        SELECT code_hash, expires_at, attempts, consumed_at
        FROM email_verification
        WHERE user_id = ?
        FOR UPDATE
        """,
        (resultSet, rowNumber) ->
            new VerificationRecord(
                resultSet.getString("code_hash"),
                resultSet.getTimestamp("expires_at").toInstant(),
                resultSet.getInt("attempts"),
                resultSet.getTimestamp("consumed_at") == null
                    ? null
                    : resultSet.getTimestamp("consumed_at").toInstant()),
        user.getId());

    if (records.isEmpty()) {
      throw invalidCode();
    }

    VerificationRecord verification = records.get(0);
    Instant now = Instant.now();

    if (verification.consumedAt() != null
        || verification.expiresAt().isBefore(now)
        || verification.attempts() >= MAX_ATTEMPTS) {
      throw invalidCode();
    }

    String suppliedCode = code == null ? "" : code.trim();
    boolean matches =
        MessageDigest.isEqual(
            verification.codeHash().getBytes(StandardCharsets.US_ASCII),
            hash(suppliedCode).getBytes(StandardCharsets.US_ASCII));

    if (!matches) {
      int attempts = verification.attempts() + 1;
      jdbc.update(
          """
          UPDATE email_verification
          SET attempts = ?,
              consumed_at = CASE WHEN ? >= ? THEN now() ELSE consumed_at END
          WHERE user_id = ?
          """,
          attempts,
          attempts,
          MAX_ATTEMPTS,
          user.getId());

      throw invalidCode();
    }

    jdbc.update(
        "UPDATE email_verification SET consumed_at = now() WHERE user_id = ?",
        user.getId());
    jdbc.update(
        "UPDATE app_user SET email_verified = TRUE, updated_at = now() WHERE id = ?",
        user.getId());

    return users.findById(user.getId()).orElseThrow(this::invalidCode);
  }

  @Transactional(
      propagation = Propagation.REQUIRES_NEW,
      noRollbackFor = ApiException.class)
  public void checkAuthRateLimit(String email, String clientIp) {
    enforceRateLimit("auth-email:" + normalizeEmail(email), 10);
    enforceRateLimit("auth-ip:" + safeIp(clientIp), 20);
  }

  @Transactional(noRollbackFor = ApiException.class)
  public void resend(String email, String clientIp) {
    if (email == null || email.isBlank()) {
      return;
    }

    users.findByEmailIgnoreCase(email.trim())
        .filter(user -> !user.isEmailVerified())
        .ifPresent(user -> sendCode(user, clientIp));
  }

  private void enforceRateLimit(String key, int limit) {
    Integer count =
        jdbc.queryForObject(
            """
            INSERT INTO auth_rate_limit (bucket_key, window_started_at, request_count)
            VALUES (?, now(), 1)
            ON CONFLICT (bucket_key) DO UPDATE SET
              request_count = CASE
                WHEN auth_rate_limit.window_started_at < now() - interval '15 minutes'
                  THEN 1
                ELSE auth_rate_limit.request_count + 1
              END,
              window_started_at = CASE
                WHEN auth_rate_limit.window_started_at < now() - interval '15 minutes'
                  THEN now()
                ELSE auth_rate_limit.window_started_at
              END
            RETURNING request_count
            """,
            Integer.class,
            hash(key));

    if (count != null && count > limit) {
      throw tooManyRequests();
    }
  }

  private String hash(String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(hashSecret, "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to protect verification data", exception);
    }
  }

  private String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase();
  }

  private String safeIp(String ip) {
    if (ip == null || ip.isBlank()) {
      return "unknown";
    }
    return ip.substring(0, Math.min(ip.length(), 64));
  }

  private ApiException invalidCode() {
    return new ApiException(
        HttpStatus.BAD_REQUEST,
        "Verification could not be completed. Check the details or request a new code.");
  }

  private ApiException tooManyRequests() {
    return new ApiException(
        HttpStatus.TOO_MANY_REQUESTS,
        "Too many requests. Please try again later.");
  }

  private record VerificationCooldown(Instant resendAfter, Instant consumedAt) {}

  private record VerificationRecord(
      String codeHash, Instant expiresAt, int attempts, Instant consumedAt) {}
}
