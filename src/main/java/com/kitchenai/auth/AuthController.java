package com.kitchenai.auth;

import com.kitchenai.common.ApiException;
import com.kitchenai.membership.EntitlementService;
import com.kitchenai.user.User;
import com.kitchenai.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private static final String GENERIC_REGISTRATION_MESSAGE =
      "If the address can be registered, a verification email will be sent.";

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final EntitlementService entitlements;
  private final EmailVerificationService emailVerificationService;

  public AuthController(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      EntitlementService entitlements,
      EmailVerificationService emailVerificationService) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.entitlements = entitlements;
    this.emailVerificationService = emailVerificationService;
  }

  public record Register(
      @Email @NotBlank String email,
      @NotBlank @Size(min = 12, max = 128) String password,
      @NotBlank @Size(max = 120) String displayName) {}

  public record Login(@Email @NotBlank String email, @NotBlank String password) {}

  public record VerifyEmail(
      @Email @NotBlank String email,
      @NotBlank @Pattern(regexp = "\\d{6}") String code) {}

  public record ResendCode(@Email @NotBlank String email) {}

  @Transactional
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, Object> register(
      @Valid @RequestBody Register request, HttpServletRequest httpRequest) {
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    emailVerificationService.checkAuthRateLimit(email, httpRequest.getRemoteAddr());

    Optional<User> existingUser = users.findByEmailIgnoreCase(email);
    if (existingUser.isPresent()) {
      if (!existingUser.get().isEmailVerified()) {
        try {
          emailVerificationService.sendCode(existingUser.get(), httpRequest.getRemoteAddr());
        } catch (ApiException ignored) {
          // Keep the response generic to prevent account enumeration.
        }
      }
      return Map.of("message", GENERIC_REGISTRATION_MESSAGE);
    }

    User user =
        users.save(
            new User(
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim()));

    entitlements.ensureMembership(user.getId());
    emailVerificationService.sendCode(user, httpRequest.getRemoteAddr());

    return Map.of("message", GENERIC_REGISTRATION_MESSAGE);
  }

  @PostMapping("/verify-email")
  public Map<String, Object> verifyEmail(
      @Valid @RequestBody VerifyEmail request, HttpServletRequest httpRequest) {
    User user =
        emailVerificationService.verify(
            request.email(), request.code(), httpRequest.getRemoteAddr());

    return createAuthenticationResponse(user);
  }

  @PostMapping("/resend-verification")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, Object> resendVerification(
      @Valid @RequestBody ResendCode request, HttpServletRequest httpRequest) {
    try {
      emailVerificationService.resend(request.email(), httpRequest.getRemoteAddr());
    } catch (ApiException ignored) {
      // Keep the response generic to prevent account enumeration.
    }

    return Map.of("message", GENERIC_REGISTRATION_MESSAGE);
  }

  @PostMapping("/login")
  public Map<String, Object> login(
      @Valid @RequestBody Login request, HttpServletRequest httpRequest) {
    emailVerificationService.checkAuthRateLimit(
        request.email(), httpRequest.getRemoteAddr());

    User user =
        users.findByEmailIgnoreCase(request.email().trim())
            .filter(
                candidate ->
                    passwordEncoder.matches(
                        request.password(), candidate.getPasswordHash()))
            .orElseThrow(
                () -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

    if (!user.isEmailVerified()) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "Invalid credentials or verification status.");
    }

    return createAuthenticationResponse(user);
  }

  @GetMapping("/me")
  public Map<String, Object> currentUser(@AuthenticationPrincipal UUID userId) {
    User user =
        users.findById(userId)
            .orElseThrow(
                () -> new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required"));

    if (!user.isEmailVerified()) {
      throw new ApiException(HttpStatus.FORBIDDEN, "Email verification required.");
    }

    return toUserResponse(user);
  }

  private Map<String, Object> createAuthenticationResponse(User user) {
    Map<String, Object> response = new HashMap<>();
    response.put("token", jwtService.create(user.getId()));
    response.put("user", toUserResponse(user));
    return response;
  }

  private Map<String, Object> toUserResponse(User user) {
    return Map.of(
        "id", user.getId(),
        "email", user.getEmail(),
        "displayName", user.getDisplayName(),
        "isAdmin", user.isAdmin());
  }
}
