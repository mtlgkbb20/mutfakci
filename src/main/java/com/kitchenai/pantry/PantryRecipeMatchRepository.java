package com.kitchenai.pantry;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface PantryRecipeMatchProjection {
  String getRecipeId();
  String getTitle();
  Integer getServings();
  Double getCoverage();
  String getAvailableJson();
  String getMissingJson();
}

interface PantryRecipeMatchRepository extends Repository<PantryItem, java.util.UUID> {
  @Query(value = """
      SELECT
        r.id AS "recipeId",
        r.title AS "title",
        r.servings AS "servings",
        (COUNT(*) FILTER (WHERE pm.pantry_item_id IS NOT NULL)::double precision
          / NULLIF(COUNT(*), 0)) AS "coverage",
        COALESCE(
          json_agg(json_build_object(
            'name', i.canonical_name,
            'pantryItemId', pm.pantry_item_id,
            'required', ri.quantity,
            'unit', ri.unit,
            'stock', pm.stock_quantity,
            'stockUnit', pm.stock_unit
          )) FILTER (WHERE pm.pantry_item_id IS NOT NULL),
          '[]'::json
        )::text AS "availableJson",
        COALESCE(
          json_agg(json_build_object(
            'name', i.canonical_name,
            'required', ri.quantity,
            'unit', ri.unit
          )) FILTER (WHERE pm.pantry_item_id IS NULL),
          '[]'::json
        )::text AS "missingJson"
      FROM recipe_catalog r
      JOIN recipe_ingredient ri ON ri.recipe_id = r.id
      JOIN ingredient i ON i.id = ri.ingredient_id
      LEFT JOIN LATERAL (
        SELECT
          pi.id AS pantry_item_id,
          pi.quantity AS stock_quantity,
          pi.unit AS stock_unit,
          ia.confidence AS match_confidence
        FROM pantry_item pi
        JOIN ingredient_alias ia
          ON ia.normalized_alias = pi.normalized_name
         AND ia.ingredient_id = ri.ingredient_id
         AND ia.enabled = TRUE
        WHERE pi.user_id = :userId
          AND pi.archived = FALSE
          AND CASE
            WHEN lower(pi.unit) = lower(ri.unit) THEN pi.quantity >= ri.quantity
            WHEN lower(pi.unit) IN ('g', 'kg') AND lower(ri.unit) IN ('g', 'kg') THEN
              (CASE lower(pi.unit) WHEN 'kg' THEN pi.quantity * 1000 ELSE pi.quantity END)
              >=
              (CASE lower(ri.unit) WHEN 'kg' THEN ri.quantity * 1000 ELSE ri.quantity END)
            WHEN lower(pi.unit) IN ('ml', 'l') AND lower(ri.unit) IN ('ml', 'l') THEN
              (CASE lower(pi.unit) WHEN 'l' THEN pi.quantity * 1000 ELSE pi.quantity END)
              >=
              (CASE lower(ri.unit) WHEN 'l' THEN ri.quantity * 1000 ELSE ri.quantity END)
            WHEN lower(pi.unit) IN ('adet', 'piece', 'pcs')
             AND lower(ri.unit) IN ('adet', 'piece', 'pcs') THEN pi.quantity >= ri.quantity
            ELSE FALSE
          END
        ORDER BY
          CASE WHEN lower(pi.unit) = lower(ri.unit) THEN 0 ELSE 1 END,
          pi.quantity DESC,
          pi.created_at DESC
        LIMIT 1
      ) pm ON TRUE
      WHERE r.active = TRUE
        AND r.category = :category
      GROUP BY r.id, r.title, r.servings
      ORDER BY "coverage" DESC,
        COUNT(*) FILTER (WHERE pm.pantry_item_id IS NOT NULL) DESC,
        SUM(COALESCE(pm.match_confidence, 0)) DESC,
        COUNT(*) FILTER (WHERE pm.pantry_item_id IS NULL) ASC,
        r.title ASC,
        r.id ASC
      LIMIT :limit OFFSET :offset
      """, nativeQuery = true)
  List<PantryRecipeMatchProjection> findMatches(
      @Param("userId") java.util.UUID userId,
      @Param("category") String category,
      @Param("limit") int limit,
      @Param("offset") int offset);
}
