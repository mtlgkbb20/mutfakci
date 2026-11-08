package com.kitchenai.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtFilter extends OncePerRequestFilter {
  private final JwtService jwt;

  public JwtFilter(JwtService j) {
    jwt = j;
  }

  protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s, FilterChain c)
      throws ServletException, IOException {
    String h = r.getHeader("Authorization");
    if (h != null && h.startsWith("Bearer "))
      try {
        var a =
            new UsernamePasswordAuthenticationToken(
                jwt.parse(h.substring(7)), null, AuthorityUtils.NO_AUTHORITIES);
        SecurityContextHolder.getContext().setAuthentication(a);
      } catch (Exception ignored) {
      }
    c.doFilter(r, s);
  }
}
