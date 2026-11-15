# Pantry recipe discovery

## Data model

Recipe discovery is backed by PostgreSQL tables introduced in Flyway V19:

- `ingredient`: one canonical identity per ingredient.
- `ingredient_alias`: curated exact/variant names mapped to a canonical ingredient. Normalized aliases are unique, so one pantry name cannot silently map to multiple ingredients.
- `recipe_catalog`: active recipe metadata and one of the public discovery categories: `MEAL`, `DESSERT`, or `DRINK`.
- `recipe_ingredient`: required ingredient, quantity, unit, and preparation for each catalog recipe.

The migration seeds the current nine curated recipes plus Sütlaç, Ev Yapımı Limonata, and Çilekli Smoothie. Recipe generation still uses the existing JSON catalog; the new relational catalog is the source for pantry matching and should be updated together when curated recipes are added or edited.

## Matching rules

The query is scoped to the authenticated user's active pantry rows and one requested category. Ingredient matching is exact against a curated normalized alias; there is no fuzzy substring matching that could accidentally equate unrelated foods. Alias confidence is used as a ranking tie-breaker, not as a hidden multiplier in the displayed coverage percentage.

A pantry ingredient is counted as available only when its stock is sufficient and its unit is compatible. Supported conversions are g/kg, ml/l, and adet/piece/pcs. Other units must match exactly. Missing ingredients include both absent ingredients and ingredients with insufficient/incompatible stock.

Results are ordered by:
1. Available ingredient coverage (descending).
2. Number of available ingredients (descending).
3. Curated alias confidence (descending).
4. Number of missing ingredients (ascending).
5. Recipe title and id (stable deterministic tie-breakers).

The API fetches four rows per request, returns three, and uses the fourth row only to set `hasMore`. Page size is fixed at three to keep the initial result set predictable. The UI requests subsequent pages only after the user taps “3 tarif daha göster”.

## API

`GET /api/v1/pantry/recipe-matches?category=MEAL&page=0`

Allowed categories: `MEAL`, `DESSERT`, `DRINK`. The response includes `category`, `page`, `size`, `hasMore`, and `recipes`. Each recipe contains its coverage, available pantry ingredients (including pantry item IDs for the existing confirmed-consumption flow), and missing ingredients.

The previous POST endpoint remains available for older mobile clients and returns the first three meal matches.

## Performance and operations

Indexes cover category/active recipe lookup, ingredient-to-recipe lookup, normalized alias lookup, and the existing user/normalized pantry name index. The SQL query filters by user and category before aggregating only the candidate category's ingredient rows. No AI call is made during discovery.

Alias data is curated intentionally. Do not add broad or ambiguous substitutions (for example, equating every dairy product or every poultry cut). Add aliases only when the ingredient is a safe match for recipe use, and use lower confidence for valid but less-specific variants. Keep normalized values aligned with `RecipeText.normalize` and `PantryController.normalize`.

## Validation

Backend unit tests cover category validation, three-item pagination, the has-more signal, JSON ingredient payload mapping, and existing pantry stock-consumption behavior. Flyway migration and SQL query execution must also be validated against PostgreSQL in CI/ a disposable database; the H2 unit-test profile intentionally disables Flyway.
