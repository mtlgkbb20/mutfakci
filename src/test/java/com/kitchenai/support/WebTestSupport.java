package com.kitchenai.support;

import com.kitchenai.common.GlobalExceptionHandler;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Builds a standalone MockMvc for a controller with the real exception handler, bean validation,
 * Jackson serialization and {@code @AuthenticationPrincipal UUID} resolution, without a database.
 */
public final class WebTestSupport {
  private WebTestSupport() {}

  public static MockMvc mvc(Object... controllers) {
    return MockMvcBuilders.standaloneSetup(controllers)
        .setControllerAdvice(new GlobalExceptionHandler())
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .build();
  }

  /** Authenticates the current thread as {@code userId}, mirroring what JwtFilter does. */
  public static void authenticate(UUID userId) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(userId, null, AuthorityUtils.NO_AUTHORITIES));
  }

  public static void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }
}
