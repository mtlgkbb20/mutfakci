package com.kitchenai.auth;

import static org.junit.jupiter.api.Assertions.*;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
  private static final String SECRET = "test-secret-that-is-long-enough-for-hs256-signing";

  @Test
  void createdTokenRoundTripsToTheSameUser() {
    JwtService jwt = new JwtService(SECRET, 60);
    UUID user = UUID.randomUUID();
    assertEquals(user, jwt.parse(jwt.create(user)));
  }

  @Test
  void tokenSignedWithAnotherSecretIsRejected() {
    String foreign = new JwtService(SECRET + "-other", 60).create(UUID.randomUUID());
    assertThrows(SignatureException.class, () -> new JwtService(SECRET, 60).parse(foreign));
  }

  @Test
  void expiredTokenIsRejected() {
    JwtService alreadyExpired = new JwtService(SECRET, -1);
    String token = alreadyExpired.create(UUID.randomUUID());
    assertThrows(ExpiredJwtException.class, () -> alreadyExpired.parse(token));
  }

  @Test
  void tamperedTokenIsRejected() {
    JwtService jwt = new JwtService(SECRET, 60);
    String token = jwt.create(UUID.randomUUID());
    String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");
    assertThrows(Exception.class, () -> jwt.parse(tampered));
  }

  @Test
  void tooShortSecretIsRejectedAtStartup() {
    assertThrows(Exception.class, () -> new JwtService("short", 60));
  }
}
