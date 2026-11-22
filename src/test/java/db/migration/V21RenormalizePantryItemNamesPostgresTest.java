package db.migration;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Runs the real Flyway path on PostgreSQL: migrate to V20, seed rows with legacy pantry keys, then
 * let Flyway apply the Java migration V21. Uses its own schema so it cannot disturb other tests.
 */
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION", matches = "true")
class V21RenormalizePantryItemNamesPostgresTest {
  private static final String SCHEMA = "v21_migration_check";

  private Flyway flyway(String target) {
    return Flyway.configure()
        .dataSource(System.getenv("DATABASE_URL"), System.getenv("DATABASE_USERNAME"), System.getenv("DATABASE_PASSWORD"))
        .schemas(SCHEMA)
        .cleanDisabled(false)
        .target(target)
        .load();
  }

  @Test
  void flywayAppliesV21ToLegacyRowsWithoutTouchingStock() throws Exception {
    flyway("latest").clean();
    flyway("20").migrate();

    try (Connection c = DriverManager.getConnection(System.getenv("DATABASE_URL"),
            System.getenv("DATABASE_USERNAME"), System.getenv("DATABASE_PASSWORD"));
        Statement s = c.createStatement()) {
      s.execute("SET search_path TO " + SCHEMA);
      s.execute("INSERT INTO app_user(id,email,password_hash,display_name,created_at,updated_at,is_admin) "
          + "VALUES ('00000000-0000-0000-0000-000000000021','v21@example.com','x','V21',now(),now(),false)");
      s.execute("INSERT INTO pantry_item(id,user_id,name,normalized_name,category,quantity,unit,archived,version,created_at,updated_at) VALUES "
          + "(gen_random_uuid(),'00000000-0000-0000-0000-000000000021','Domates-salçası','domatessalcasi','OTHER',200,'g',false,0,now(),now()),"
          + "(gen_random_uuid(),'00000000-0000-0000-0000-000000000021','Domates salçası','domates salcasi','OTHER',1,'piece',false,0,now(),now()),"
          + "(gen_random_uuid(),'00000000-0000-0000-0000-000000000021','Tavuk/göğüs','tavukgogus','OTHER',1,'kg',true,0,now(),now()),"
          + "(gen_random_uuid(),'00000000-0000-0000-0000-000000000021','Iğdır inciri','igdir inciri','OTHER',3,'piece',false,0,now(),now())");

      var result = flyway("latest").migrate();
      assertTrue(result.success);
      assertEquals("21", result.targetSchemaVersion);

      Map<String, String> rows = new LinkedHashMap<>();
      try (ResultSet rs = s.executeQuery("SELECT name, normalized_name, quantity, unit FROM pantry_item ORDER BY name")) {
        while (rs.next()) rows.put(rs.getString(1), rs.getString(2) + "|" + rs.getBigDecimal(3).stripTrailingZeros().toPlainString() + "|" + rs.getString(4));
      }
      assertEquals(Map.of(
          "Domates-salçası", "domates salcasi|200|g",
          "Domates salçası", "domates salcasi|1|piece",
          "Tavuk/göğüs", "tavuk gogus|1|kg",
          "Iğdır inciri", "igdir inciri|3|piece"), rows);
    } finally {
      flyway("latest").clean();
    }
  }
}
