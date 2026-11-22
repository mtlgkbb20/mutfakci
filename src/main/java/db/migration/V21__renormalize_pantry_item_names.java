package db.migration;

import com.kitchenai.common.IngredientNames;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Recomputes pantry_item.normalized_name with {@link IngredientNames#normalize}, the rule zero-waste
 * remainders and the ingredient alias table already use. Before this, the pantry dropped
 * punctuation ("domates-salçası" -> "domatessalcasi") while zero-waste turned it into a space
 * ("domates salcasi"), so remainder transfers created duplicates instead of merging.
 *
 * <p>Only the derived key changes; display names, quantities and movement history are untouched.
 * Items that now share a key are intentionally not merged: their units may differ, and merging
 * would rewrite user stock without consent. Lookups pick the oldest matching item.
 */
public class V21__renormalize_pantry_item_names extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    renormalize(context.getConnection());
  }

  /** Returns the number of rows whose normalized_name changed. */
  static int renormalize(Connection connection) throws SQLException {
    int changed = 0;
    try (Statement select = connection.createStatement();
        ResultSet rows = select.executeQuery("SELECT id, name, normalized_name FROM pantry_item");
        PreparedStatement update = connection.prepareStatement(
            "UPDATE pantry_item SET normalized_name = ? WHERE id = ?")) {
      while (rows.next()) {
        String normalized = IngredientNames.normalize(rows.getString("name"));
        if (normalized.length() > 160) normalized = normalized.substring(0, 160).trim();
        if (normalized.equals(rows.getString("normalized_name"))) continue;
        update.setString(1, normalized);
        update.setObject(2, rows.getObject("id"));
        update.addBatch();
        changed++;
      }
      if (changed > 0) update.executeBatch();
    }
    return changed;
  }
}
