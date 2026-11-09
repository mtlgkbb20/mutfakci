package com.kitchenai.auth;

import com.kitchenai.common.ApiException;
import com.kitchenai.membership.EntitlementService;
import com.kitchenai.user.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private static final Logger log = LoggerFactory.getLogger(AuthController.class);
  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final JwtService jwt;
  private final EntitlementService entitlements;

  public AuthController(UserRepository u, PasswordEncoder e, JwtService j, EntitlementService entitlements) {
    users = u;
    encoder = e;
    jwt = j;
    this.entitlements = entitlements;
  }

  public record Register(
      @Email String email, @Size(min = 8) String password, @NotBlank String displayName) {}

  public record Login(@Email String email, @NotBlank String password) {}

  @Transactional
  @PostMapping("/register")
  public Map<String, Object> register(@Valid @RequestBody Register r) {
    log.info("Register request email={}", r.email());
    if (users.findByEmailIgnoreCase(r.email()).isPresent()) {
      log.warn("Register rejected email already exists email={}", r.email());
      throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
    }
    User u =
        users.save(
            new User(r.email().toLowerCase(), encoder.encode(r.password()), r.displayName()));
    entitlements.ensureMembership(u.getId());
    log.info("Register success userId={} email={}", u.getId(), u.getEmail());
    return response(u);
  }

  @PostMapping("/login")
  public Map<String, Object> login(@Valid @RequestBody Login r) {
    log.info("Login request email={}", r.email());
    User u =
        users
            .findByEmailIgnoreCase(r.email())
            .filter(x -> encoder.matches(r.password(), x.getPasswordHash()))
            .orElseThrow(
                () -> {
                  log.warn("Login rejected invalid credentials email={}", r.email());
                  return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
                });
    log.info("Login success userId={} email={} responseStatus=200", u.getId(), u.getEmail());
    return response(u);
  }

  @GetMapping("/me")
  public Map<String, Object> me(
      @org.springframework.security.core.annotation.AuthenticationPrincipal UUID id) {
    return userResponse(users.findById(id).orElseThrow());
  }

  private Map<String, Object> response(User u) {
    var m = new HashMap<String, Object>();
    m.put("token", jwt.create(u.getId()));
    m.put("user", userResponse(u));
    return m;
  }

  private Map<String, Object> userResponse(User u) {
    return Map.of("id", u.getId(), "email", u.getEmail(), "displayName", u.getDisplayName());
  }
}
