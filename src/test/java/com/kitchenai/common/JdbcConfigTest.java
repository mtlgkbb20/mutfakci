package com.kitchenai.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcConfigTest {
  @Test
  void applicationJdbcTemplateReplacesBootsDefault() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class))
        .withUserConfiguration(JdbcConfig.class)
        .withPropertyValues("spring.datasource.url=jdbc:h2:mem:jdbcconfig;DB_CLOSE_DELAY=-1")
        .run(context -> {
          assertThat(context).hasSingleBean(JdbcTemplate.class);
          assertThat(context.getBean(JdbcTemplate.class)).isInstanceOf(JavaTimeJdbcTemplate.class);
        });
  }
}
