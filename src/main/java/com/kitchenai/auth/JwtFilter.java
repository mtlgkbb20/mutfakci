package com.kitchenai.auth;

import com.kitchenai.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final UserRepository users;

  public JwtFilter(JwtService jwtService, UserRepository users) {
    this.jwtService = jwtService;
    this.users = users;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String authorization = request.getHeader("Authorization");

    if (authorization != null && authorization.startsWith("Bearer ")) {
      try {
        var userId = jwtService.parse(authorization.substring(7));
        boolean verified =
            users.findById(userId).map(user -> user.isEmailVerified()).orElse(false);

        if (verified) {
          var authentication =
              new UsernamePasswordAuthenticationToken(
                  userId, null, AuthorityUtils.NO_AUTHORITIES);
          SecurityContextHolder.getContext().setAuthentication(authentication);
        } else {
          SecurityContextHolder.clearContext();
        }
      } catch (RuntimeException exception) {
        SecurityContextHolder.clearContext();
      }
    }

    chain.doFilter(request, response);
  }
}
