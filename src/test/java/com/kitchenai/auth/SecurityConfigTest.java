package com.kitchenai.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

/** Loads only the real security chain (SecurityConfig + JwtFilter + JwtService) around a probe controller. */
@WebMvcTest(controllers = SecurityConfigTest.Probe.class)
@Import({SecurityConfig.class, JwtFilter.class, JwtService.class, SecurityConfigTest.Probe.class})
@TestPropertySource(properties = {
  "jwt.secret=test-secret-that-is-long-enough-for-hs256-signing",
  "jwt.expiration-minutes=60",
  "app.frontend-url=https://app.example.com"
})
class SecurityConfigTest {

  @RestController
  static class Probe {
    @PostMapping({"/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/payments/paytr/callback"})
    String open() { return "open"; }
    @GetMapping("/api/v1/recipes")
    String recipes(@AuthenticationPrincipal UUID user) { return user.toString(); }
  }

  @Autowired MockMvc mvc;
  @Autowired JwtService jwt;

  @Test
  void publicEndpointsDoNotRequireAToken() throws Exception {
    mvc.perform(post("/api/v1/auth/login")).andExpect(status().isOk());
    mvc.perform(post("/api/v1/auth/register")).andExpect(status().isOk());
    mvc.perform(post("/api/v1/payments/paytr/callback")).andExpect(status().isOk());
  }

  @Test
  void protectedEndpointRejectsAnonymousRequests() throws Exception {
    mvc.perform(get("/api/v1/recipes")).andExpect(status().isForbidden());
  }

  @Test
  void protectedEndpointRejectsAnInvalidToken() throws Exception {
    mvc.perform(get("/api/v1/recipes").header("Authorization", "Bearer forged.token.value"))
        .andExpect(status().isForbidden());
  }

  @Test
  void validTokenReachesTheControllerAsThatUser() throws Exception {
    UUID user = UUID.randomUUID();
    mvc.perform(get("/api/v1/recipes").header("Authorization", "Bearer " + jwt.create(user)))
        .andExpect(status().isOk())
        .andExpect(content().string(user.toString()));
  }

  @Test
  void corsAllowsOnlyTheConfiguredFrontendOrigin() throws Exception {
    mvc.perform(options("/api/v1/recipes").header("Origin", "https://app.example.com")
            .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"));
    mvc.perform(options("/api/v1/recipes").header("Origin", "https://evil.example.com")
            .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden());
  }

  @Test
  void noServerSideSessionIsCreated() throws Exception {
    mvc.perform(post("/api/v1/auth/login")).andExpect(request().sessionAttributeDoesNotExist("SPRING_SECURITY_CONTEXT"));
  }
}
