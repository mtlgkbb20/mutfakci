package com.kitchenai.common;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class JdbcConfig {
  /** Replaces Boot's default JdbcTemplate so java.time parameters bind on PostgreSQL. */
  @Bean
  public JdbcTemplate jdbcTemplate(DataSource dataSource) {
    return new JavaTimeJdbcTemplate(dataSource);
  }
}
