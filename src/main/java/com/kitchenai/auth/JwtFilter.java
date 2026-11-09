package com.kitchenai.auth;
import com.kitchenai.user.UserRepository;
import jakarta.servlet.*; import jakarta.servlet.http.*; import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component public class JwtFilter extends OncePerRequestFilter {
 private final JwtService jwt; private final UserRepository users;
 public JwtFilter(JwtService jwt,UserRepository users){this.jwt=jwt;this.users=users;}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
  throws ServletException,IOException {
  String h=request.getHeader("Authorization");
  if(h!=null&&h.startsWith("Bearer ")) try {
   var id=jwt.parse(h.substring(7));
   if(users.findById(id).filter(User->User.isEmailVerified()).isPresent())
    SecurityContextHolder.getContext().setAuthentication(
     new UsernamePasswordAuthenticationToken(id,null,AuthorityUtils.NO_AUTHORITIES));
  } catch(RuntimeException ignored){SecurityContextHolder.clearContext();}
  chain.doFilter(request,response);
 }
}