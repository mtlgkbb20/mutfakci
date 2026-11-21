package com.kitchenai.auth;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kitchenai.membership.EntitlementService;
import com.kitchenai.support.WebTestSupport;
import com.kitchenai.user.User;
import com.kitchenai.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

class AuthControllerTest {
  private final UserRepository users = mock(UserRepository.class);
  private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
  private final JwtService jwt = new JwtService("test-secret-that-is-long-enough-for-hs256-signing", 60);
  private final EntitlementService entitlements = mock(EntitlementService.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = WebTestSupport.mvc(new AuthController(users, encoder, jwt, entitlements));
    when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @AfterEach
  void tearDown() {
    WebTestSupport.clearAuthentication();
  }

  private static String json(String email, String password, String name) {
    return "{\"email\":\"%s\",\"password\":\"%s\",\"displayName\":\"%s\"}".formatted(email, password, name);
  }

  @Test
  void registerNormalizesEmailHashesPasswordCreatesMembershipAndReturnsToken() throws Exception {
    when(users.findByEmailIgnoreCase("Ada@Example.com")).thenReturn(Optional.empty());

    mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(json("Ada@Example.com", "supersecret", "Ada")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token", not(emptyString())))
        .andExpect(jsonPath("$.user.email").value("ada@example.com"))
        .andExpect(jsonPath("$.user.displayName").value("Ada"))
        .andExpect(jsonPath("$.user.isAdmin").value(false))
        .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

    verify(users).save(argThat(u -> encoder.matches("supersecret", u.getPasswordHash())
        && !"supersecret".equals(u.getPasswordHash())));
    verify(entitlements).ensureMembership(any());
  }

  @Test
  void registerRejectsDuplicateEmailWith409() throws Exception {
    when(users.findByEmailIgnoreCase("ada@example.com"))
        .thenReturn(Optional.of(new User("ada@example.com", "x", "Ada")));

    mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(json("ada@example.com", "supersecret", "Ada")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("Email already registered"))
        .andExpect(jsonPath("$.errorId").exists());
    verify(users, never()).save(any());
    verifyNoInteractions(entitlements);
  }

  @Test
  void registerRejectsInvalidPayloadWith400() throws Exception {
    mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(json("not-an-email", "short", "")))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(users, entitlements);
  }

  @Test
  void loginWithCorrectPasswordReturnsTokenForThatUser() throws Exception {
    User ada = new User("ada@example.com", encoder.encode("supersecret"), "Ada");
    when(users.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(ada));

    String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"ada@example.com\",\"password\":\"supersecret\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.id").value(ada.getId().toString()))
        .andReturn().getResponse().getContentAsString();
    String token = com.jayway.jsonpath.JsonPath.read(body, "$.token");
    org.junit.jupiter.api.Assertions.assertEquals(ada.getId(), jwt.parse(token));
  }

  @Test
  void loginWithWrongPasswordReturns401WithoutRevealingWhichFieldFailed() throws Exception {
    User ada = new User("ada@example.com", encoder.encode("supersecret"), "Ada");
    when(users.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(ada));

    mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"ada@example.com\",\"password\":\"wrong-password\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Invalid credentials"));
  }

  @Test
  void loginWithUnknownEmailReturnsTheSame401() throws Exception {
    when(users.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
    mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"ghost@example.com\",\"password\":\"whatever1\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Invalid credentials"));
  }

  @Test
  void meReturnsTheAuthenticatedUserOnly() throws Exception {
    User ada = new User("ada@example.com", "hash", "Ada");
    when(users.findById(ada.getId())).thenReturn(Optional.of(ada));
    WebTestSupport.authenticate(ada.getId());

    mvc.perform(get("/api/v1/auth/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ada.getId().toString()))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
    verify(users).findById(ada.getId());
  }
}
