package com.kitchenai.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final SecretKey key;
  private final long expiry;

  public JwtService(
      @Value("${jwt.secret}") String secret, @Value("${jwt.expiration-minutes}") long mins) {
    key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    expiry = mins * 60;
  }

  public String create(UUID id) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(id.toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(expiry)))
        .signWith(key)
        .compact();
  }

  public UUID parse(String token) {
    return UUID.fromString(
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject());
  }
}
