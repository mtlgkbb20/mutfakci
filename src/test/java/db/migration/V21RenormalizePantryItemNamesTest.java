package db.migration;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class V21RenormalizePantryItemNamesTest {
  private Connection connection;

  @BeforeEach
  void setUp() throws Exception {
    connection = DriverManager.getConnection(
        "jdbc:h2:mem:v21_" + UUID.randomUUID() + ";MODE=PostgreSQL", "sa", "");
    try (Statement s = connection.createStatement()) {
      s.execute("CREATE TABLE pantry_item (id UUID PRIMARY KEY, name VARCHAR(160) NOT NULL, "
          + "normalized_name VARCHAR(160) NOT NULL, quantity NUMERIC(12,3) NOT NULL, unit VARCHAR(20) NOT NULL)");
    }
  }

  @AfterEach
  void tearDown() throws Exception {
    connection.close();
  }

  @Test
  void rewritesLegacyKeysAndLeavesEverythingElseUntouched() throws Exception {
    UUID hyphen = insert("Domates-salçası", "domatessalcasi", "200", "g");
    UUID slash = insert("Tavuk/göğüs", "tavukgogus", "1", "kg");
    UUID paren = insert("Zeytinyağı (sızma)", "zeytinyagi sizma", "1", "l");
    UUID plain = insert("Süt", "sut", "2", "l");

    assertEquals(2, V21__renormalize_pantry_item_names.renormalize(connection));
    assertEquals("tavuk gogus", key(slash));

    assertEquals("domates salcasi", key(hyphen));
    assertEquals("zeytinyagi sizma", key(paren));
    assertEquals("sut", key(plain));
    try (Statement s = connection.createStatement();
        ResultSet rs = s.executeQuery("SELECT name, quantity, unit FROM pantry_item WHERE id = '" + hyphen + "'")) {
      assertTrue(rs.next());
      assertEquals("Domates-salçası", rs.getString("name"));
      assertEquals(0, rs.getBigDecimal("quantity").compareTo(new java.math.BigDecimal("200")));
      assertEquals("g", rs.getString("unit"));
    }
  }

  @Test
  void keepsItemsThatNowShareAKeyAsSeparateRows() throws Exception {
    insert("Domates-salçası", "domatessalcasi", "200", "g");
    insert("Domates salçası", "domates salcasi", "1", "piece");

    V21__renormalize_pantry_item_names.renormalize(connection);

    try (Statement s = connection.createStatement();
        ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM pantry_item WHERE normalized_name = 'domates salcasi'")) {
      rs.next();
      assertEquals(2, rs.getInt(1));
    }
  }

  @Test
  void isIdempotent() throws Exception {
    insert("Domates-salçası", "domatessalcasi", "200", "g");
    assertEquals(1, V21__renormalize_pantry_item_names.renormalize(connection));
    assertEquals(0, V21__renormalize_pantry_item_names.renormalize(connection));
  }

  @Test
  void emptyTableIsANoOp() throws Exception {
    assertEquals(0, V21__renormalize_pantry_item_names.renormalize(connection));
  }

  private UUID insert(String name, String normalized, String quantity, String unit) throws Exception {
    UUID id = UUID.randomUUID();
    try (PreparedStatement ps = connection.prepareStatement(
        "INSERT INTO pantry_item(id, name, normalized_name, quantity, unit) VALUES (?, ?, ?, ?, ?)")) {
      ps.setObject(1, id);
      ps.setString(2, name);
      ps.setString(3, normalized);
      ps.setBigDecimal(4, new java.math.BigDecimal(quantity));
      ps.setString(5, unit);
      ps.executeUpdate();
    }
    return id;
  }

  private String key(UUID id) throws Exception {
    try (PreparedStatement ps = connection.prepareStatement("SELECT normalized_name FROM pantry_item WHERE id = ?")) {
      ps.setObject(1, id);
      try (ResultSet rs = ps.executeQuery()) {
        assertTrue(rs.next());
        return rs.getString(1);
      }
    }
  }
}
