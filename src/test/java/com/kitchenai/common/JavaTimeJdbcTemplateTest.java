package com.kitchenai.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.*;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JavaTimeJdbcTemplateTest {
  private static final Instant INSTANT = Instant.parse("2026-11-22T10:15:30Z");
  private static final OffsetDateTime UTC = OffsetDateTime.parse("2026-11-22T10:15:30Z");

  private PreparedStatement ps;
  private JavaTimeJdbcTemplate jdbc;

  @BeforeEach
  void setUp() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    ps = mock(PreparedStatement.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(ps);
    when(ps.executeUpdate()).thenReturn(1);
    when(ps.executeBatch()).thenReturn(new int[] {1, 1});
    java.sql.DatabaseMetaData metaData = mock(java.sql.DatabaseMetaData.class);
    when(metaData.supportsBatchUpdates()).thenReturn(true);
    when(connection.getMetaData()).thenReturn(metaData);
    when(ps.getConnection()).thenReturn(connection);
    jdbc = new JavaTimeJdbcTemplate(dataSource);
  }

  @Test
  void bindsInstantAsUtcOffsetDateTime() throws Exception {
    jdbc.update("UPDATE t SET at = ?", INSTANT);
    verify(ps).setObject(1, UTC);
    verify(ps, never()).setObject(eq(1), any(Instant.class));
  }

  @Test
  void bindsZonedDateTimeAsTheSameInstant() throws Exception {
    jdbc.update("UPDATE t SET at = ?", INSTANT.atZone(ZoneId.of("Europe/Istanbul")));
    verify(ps).setObject(eq(1), argThat((OffsetDateTime v) -> v.toInstant().equals(INSTANT)));
  }

  @Test
  void convertsTypedArguments() throws Exception {
    jdbc.update("UPDATE t SET at = ?", new Object[] {INSTANT}, new int[] {Types.TIMESTAMP_WITH_TIMEZONE});
    verify(ps).setObject(1, UTC, Types.TIMESTAMP_WITH_TIMEZONE);
  }

  @Test
  void convertsBatchArguments() throws Exception {
    jdbc.batchUpdate("UPDATE t SET at = ?", List.<Object[]>of(new Object[] {INSTANT}, new Object[] {INSTANT.plusSeconds(60)}));
    verify(ps).setObject(1, UTC);
    verify(ps).setObject(1, UTC.plusSeconds(60));
  }

  @Test
  void convertsArgumentsOfQueries() throws Exception {
    when(ps.executeQuery()).thenReturn(mock(java.sql.ResultSet.class));
    jdbc.query("SELECT id FROM t WHERE a < ? AND b > ?", (rs, n) -> rs.getString(1), INSTANT, INSTANT);
    verify(ps).setObject(1, UTC);
    verify(ps).setObject(2, UTC);
  }

  @Test
  void leavesOtherValuesUntouchedAndAvoidsCopyingWhenNothingChanges() {
    UUID id = UUID.randomUUID();
    LocalDate day = LocalDate.of(2026, 11, 22);
    Object[] args = {id, "x", 3, day, null, UTC};
    assertSame(args, JavaTimeJdbcTemplate.bindable(args));
    assertNull(JavaTimeJdbcTemplate.bindable((Object[]) null));
  }

  @Test
  void doesNotMutateTheCallersArray() {
    Object[] args = {"x", INSTANT};
    Object[] bound = JavaTimeJdbcTemplate.bindable(args);
    assertNotSame(args, bound);
    assertSame(INSTANT, args[1]);
    assertEquals(UTC, bound[1]);
    assertEquals("x", bound[0]);
  }
}
