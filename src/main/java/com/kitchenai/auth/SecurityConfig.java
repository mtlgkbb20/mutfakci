package com.kitchenai.auth;
import java.util.*; import org.springframework.beans.factory.annotation.Value; import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain; import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
@Configuration public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
 @Bean SecurityFilterChain filterChain(HttpSecurity http,JwtFilter filter,
  @Value("${app.cors.allowed-origins:http://localhost:5173}") String allowed) throws Exception {
  List<String> origins=Arrays.stream(allowed.split(",")).map(String::trim).filter(s->!s.isEmpty()).toList();
  CorsConfigurationSource source=request->{CorsConfiguration c=new CorsConfiguration();
   c.setAllowedOrigins(origins); c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
   c.setAllowedHeaders(List.of("Authorization","Content-Type","Accept","X-Request-Id"));
   c.setExposedHeaders(List.of("X-Request-Id")); c.setAllowCredentials(false); c.setMaxAge(3600L); return c;};
  return http.csrf(csrf->csrf.disable()).cors(cors->cors.configurationSource(source))
   .headers(h->h.contentTypeOptions(c->{}).frameOptions(f->f.deny())
    .httpStrictTransportSecurity(s->s.includeSubDomains(true).maxAgeInSeconds(31536000))
    .referrerPolicy(r->r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
   .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a->a.requestMatchers("/api/v1/auth/register","/api/v1/auth/login",
    "/api/v1/auth/verify-email","/api/v1/auth/resend-verification",
    "/api/v1/payments/paytr/callback","/actuator/health").permitAll().anyRequest().authenticated())
   .addFilterBefore(filter,UsernamePasswordAuthenticationFilter.class).build();
 }
}