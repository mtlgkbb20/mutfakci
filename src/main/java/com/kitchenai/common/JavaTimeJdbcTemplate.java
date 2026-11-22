package com.kitchenai.common;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;

/**
 * JdbcTemplate that binds {@link Instant} and {@link ZonedDateTime} parameters as
 * {@link OffsetDateTime} (UTC). The PostgreSQL driver rejects {@code setObject(Instant)} with
 * "Can't infer the SQL type to use for an instance of java.time.Instant", while H2 accepts it,
 * so the H2-backed unit tests never caught it. OffsetDateTime maps to TIMESTAMPTZ on both.
 */
public class JavaTimeJdbcTemplate extends JdbcTemplate {
  public JavaTimeJdbcTemplate(DataSource dataSource) {
    super(dataSource);
  }

  @Override
  protected PreparedStatementSetter newArgPreparedStatementSetter(Object[] args) {
    return super.newArgPreparedStatementSetter(bindable(args));
  }

  @Override
  protected PreparedStatementSetter newArgTypePreparedStatementSetter(Object[] args, int[] argTypes) {
    return super.newArgTypePreparedStatementSetter(bindable(args), argTypes);
  }

  @Override
  public int[] batchUpdate(String sql, List<Object[]> batchArgs, int[] argTypes) {
    return super.batchUpdate(sql, batchArgs.stream().map(JavaTimeJdbcTemplate::bindable).toList(), argTypes);
  }

  static Object[] bindable(Object[] args) {
    if (args == null) return null;
    Object[] converted = null;
    for (int i = 0; i < args.length; i++) {
      Object value = bindable(args[i]);
      if (value != args[i]) {
        if (converted == null) converted = args.clone();
        converted[i] = value;
      }
    }
    return converted == null ? args : converted;
  }

  static Object bindable(Object value) {
    if (value instanceof Instant instant) return instant.atOffset(ZoneOffset.UTC);
    if (value instanceof ZonedDateTime zoned) return zoned.toOffsetDateTime();
    return value;
  }
}
