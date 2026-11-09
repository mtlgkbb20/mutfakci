package com.kitchenai.auth;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
public class SecurityConfig {

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityFilterChain filterChain(
      HttpSecurity http,
      JwtFilter jwtFilter,
      @Value("${app.cors.allowed-origins:http://localhost:5173}") String allowedOrigins)
      throws Exception {

    List<String> origins =
        Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toList();

    CorsConfigurationSource corsConfigurationSource =
        request -> {
          CorsConfiguration configuration = new CorsConfiguration();
          configuration.setAllowedOrigins(origins);
          configuration.setAllowedMethods(
              List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
          configuration.setAllowedHeaders(
              List.of("Authorization", "Content-Type", "Accept", "X-Request-Id"));
          configuration.setExposedHeaders(List.of("X-Request-Id"));
          configuration.setAllowCredentials(false);
          configuration.setMaxAge(3600L);
          return configuration;
        };

    return http
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .headers(
            headers ->
                headers
                    .contentTypeOptions(contentTypeOptions -> {})
                    .frameOptions(frameOptions -> frameOptions.deny())
                    .httpStrictTransportSecurity(
                        hsts ->
                            hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                    .referrerPolicy(
                        referrer ->
                            referrer.policy(
                                org.springframework.security.web.header.writers
                                    .ReferrerPolicyHeaderWriter.ReferrerPolicy
                                    .STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/verify-email",
                        "/api/v1/auth/resend-verification",
                        "/api/v1/payments/paytr/callback",
                        "/actuator/health")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
