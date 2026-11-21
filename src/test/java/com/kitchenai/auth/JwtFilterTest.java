package com.kitchenai.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtFilterTest {
  private final JwtService jwt = new JwtService("test-secret-that-is-long-enough-for-hs256-signing", 60);
  private final JwtFilter filter = new JwtFilter(jwt);

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private void run(String header) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/recipes");
    if (header != null) request.addHeader("Authorization", header);
    FilterChain chain = mock(FilterChain.class);
    filter.doFilter(request, new MockHttpServletResponse(), chain);
    verify(chain).doFilter(any(), any());
  }

  @Test
  void validBearerTokenAuthenticatesTheUserIdAsPrincipal() throws Exception {
    UUID user = UUID.randomUUID();
    run("Bearer " + jwt.create(user));
    assertEquals(user, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
  }

  @Test
  void missingHeaderLeavesRequestAnonymous() throws Exception {
    run(null);
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void invalidTokenLeavesRequestAnonymousAndContinuesChain() throws Exception {
    run("Bearer not-a-jwt");
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  void nonBearerSchemeIsIgnored() throws Exception {
    run("Basic " + jwt.create(UUID.randomUUID()));
    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }
}
