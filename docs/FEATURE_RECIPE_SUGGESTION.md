# Feature: New Recipe Suggestions

**Status:** Ready for implementation  
**Scope:** Curated recipe lookup, AI recipe generation, and multi-stage recipe composition.

## 1. Goal

Implement the **New Recipe** action. A user describes a dish (for example, "Havuçlu tarçınlı kek yapmak istiyorum"). The backend first searches the curated recipe catalog. If a confident match exists, it returns that recipe without calling the AI provider. Otherwise, it generates the recipe through a controlled, schema-constrained AI pipeline.

The client receives one complete recipe. Intermediate planning and component-generation responses are internal only and must never be streamed to or exposed to the frontend.

## 2. User flow

1. User selects **Yeni Tarif**.
2. The UI collects a natural-language dish request.
3. Backend normalizes the request and searches the curated catalog.
4. On a confident match, return the curated recipe; do not invoke AI.
5. If no confident match exists, run the AI pipeline in section 4.
6. Validate and assemble the complete recipe.
7. Persist it using the existing recipe ownership/persistence rules.
8. Return the canonical recipe response and open the existing recipe detail experience. The user can then start a cooking session as they do today.

Do not silently substitute a different dish when the request is ambiguous or the catalog match is weak. Ask a concise clarification where necessary, or preserve the original request for the AI path.

## 3. Curated recipe catalog: storage decision

### Decision: JSON source files + database runtime index

Use structured JSON files as the curated catalog's source of truth, and load/index those records into PostgreSQL for runtime lookup. Do not use plain-text TXT files: recipes contain nested data (ingredients, quantities, units, stages, ordered instructions, and doneness cues) that should be represented and validated structurally.

This hybrid approach gives us:
- Version-controlled and reviewable recipe content in Git.
- A strict schema for every curated recipe.
- Database indexes and efficient lookup at runtime.
- No AI cost/latency for recipes already in the catalog.
- A future path to an admin catalog editor without changing the client contract.

Suggested resource layout (adapt to existing backend conventions):

~~~text
src/main/resources/recipe-catalog/
  manifest.json
  cakes.json
  pastries.json
  desserts.json
~~~

Each recipe should have a stable ID and schema version. Example (illustrative, not a complete recipe):

~~~json
{
  "schemaVersion": 1,
  "id": "carrot-cinnamon-cake",
  "title": "Havuçlu Tarçınlı Kek",
  "aliases": ["havuçlu kek", "tarçınlı havuç keki", "carrot cinnamon cake"],
  "locale": "tr-TR",
  "category": "CAKE",
  "searchTerms": ["havuç", "tarçın", "ceviz", "kek"],
  "defaultServings": 8,
  "estimatedTotalMinutes": 55,
  "ingredients": [
    {
      "name": "Havuç",
      "quantity": 2,
      "unit": "piece",
      "preparation": "Rendelenmiş"
    }
  ],
  "steps": [
    {
      "order": 1,
      "title": "Kuru malzemeleri karıştır",
      "instruction": "Un, kabartma tozu ve tarçını homojen olana kadar karıştır.",
      "completionCue": "Tarçın her yere eşit dağılmış olmalı."
    }
  ],
  "source": "CURATED"
}
~~~

The production catalog schema should reuse the canonical ingredient and instruction model already used by the recipe detail API. Do not create two incompatible recipe formats.

### Database and synchronization rules

- Add or reuse catalog tables/entities following the existing persistence conventions.
- Distinguish shared curated records from user-generated records (for example, source type CURATED or AI_GENERATED).
- Use a stable catalog ID; never use a mutable title as identity.
- Load JSON idempotently at startup or through a dedicated import operation. Re-running import must update the matching catalog record, not create duplicates.
- Never overwrite user-owned/generated recipes during catalog synchronization.
- Index normalized titles and aliases. Use existing PostgreSQL search capabilities if appropriate; do not add a separate search service for this feature.
- Lookup order: normalized exact title/alias first, then optional fuzzy matching with a defined confidence threshold and ambiguity handling. Never select the first vaguely similar result.
- Start with a small, manually curated catalog. Add recipes incrementally.

## 4. AI generation pipeline

### Core rule

Do not request the entire final recipe as one unconstrained model response. The backend orchestrates typed operations. Each model result is parsed into a dedicated DTO, validated, and passed to the next operation. Raw model text is never forwarded to the frontend.

The pipeline has three logical phases:
1. Recipe decomposition.
2. Per-component recipe generation.
3. Deterministic assembly and final validation.

### 4.1 Decompose the dish into preparation components

The first model operation identifies the meaningful, independently prepared parts of the requested dish.

Examples:

| Dish request | Expected components |
|---|---|
| Simple cake | Cake batter / cake (one component) |
| Profiterole | Choux pastry, pastry cream, chocolate ganache |
| Minced-meat rose börek | Filling and pastry assembly; include homemade dough only if requested |

Do not split every dish into multiple components. The number depends on the dish and on assumptions such as ready-made yufka versus homemade dough.

Use a dedicated structured output, conceptually:

~~~json
{
  "dishTitle": "Profiterol",
  "servings": 6,
  "components": [
    {
      "key": "choux-pastry",
      "name": "Profiterol Hamuru",
      "purpose": "Profiterolün kabuğunu oluşturur",
      "order": 1,
      "dependsOn": []
    },
    {
      "key": "pastry-cream",
      "name": "Pastacı Kreması",
      "purpose": "İç dolguyu oluşturur",
      "order": 2,
      "dependsOn": []
    },
    {
      "key": "chocolate-ganache",
      "name": "Çikolata Ganajı",
      "purpose": "Üst kaplamayı oluşturur",
      "order": 3,
      "dependsOn": []
    }
  ],
  "assemblyRequired": true
}
~~~

This is an illustrative contract. Implement it as a typed backend DTO and a strict JSON schema supported by the selected AI provider.

Decomposition rules:
- Components represent meaningful preparation units, not arbitrary instruction groups.
- Include only components required by the requested recipe.
- Respect the user's wording and assumptions (ready-made yufka vs homemade dough).
- Use stable machine-friendly component keys.
- Preserve logical order and explicit dependencies where relevant.
- Enforce sensible limits (initially, for example, 1-6 components).
- Reject duplicate keys, invalid order values, self-dependencies, unknown dependencies, and dependency cycles.
- If the request is too ambiguous to decompose reliably, ask for clarification instead of inventing a specific interpretation.

### 4.2 Generate each component recipe separately

After the plan is validated, generate each component recipe separately.

Every component-generation request receives:
- Original user request.
- Validated dish title and serving count.
- Complete validated component plan, so the model understands the role of the current component in the final dish.
- Current component key, name, purpose, order, and dependencies.
- Relevant user constraints already captured by the application, if available.

Each response must conform to a strict component recipe schema and contain:
- Component key (must exactly match the requested key).
- Component title.
- Ingredients with numeric quantity, unit, ingredient name, and optional preparation note.
- Ordered instructions.
- Practical completion cues (texture, color, consistency, temperature, or other observable signs).
- Component-specific timing where meaningful.

Instructions should be operational, not vague. For example, a cake should not merely say "bake until done"; include an observable cue such as the skewer test, alongside temperature and estimated time where reasonably determinable.

Independent components may be generated in parallel after decomposition. Respect dependency order where a component genuinely needs another component's output. Do not introduce parallelism if it complicates provider integration or error handling; correctness is more important than latency.

### 4.3 Assemble the final recipe

Assemble validated component recipes in deterministic backend code into the existing canonical recipe response model. Do not use another free-form LLM response for assembly.

Assembly must:
- Preserve the original dish title and serving count.
- Preserve component order and stable keys.
- Combine ingredient lists without losing component ownership.
- Keep instructions grouped under component headings.
- Add final assembly/plating instructions when required.
- Include timing only when it can be derived or safely represented.
- Produce the same response shape used by curated recipes wherever possible.

Run final domain validation before persistence and before returning the response.

## 5. Structured output contracts and validation

Define separate DTOs/schemas for each boundary. Do not deserialize all AI operations into one oversized generic object.

Suggested conceptual contracts (adapt names/packages to the existing codebase):
- RecipeDecompositionResponse
- RecipeComponentGenerationResponse
- GeneratedRecipe (canonical assembled model)
- Existing public recipe detail response DTO

Validate at every boundary:
1. Provider output is valid JSON and conforms to the expected schema.
2. Required fields are present and non-blank.
3. Component keys match the requested key.
4. Ingredient quantities are positive when present; units belong to a supported unit set or are represented as a safe custom unit.
5. Instruction order is unique and sequential after normalization.
6. Ingredient and instruction counts are within configured limits.
7. Temperature, duration, and quantity values are within reasonable domain bounds.
8. The final recipe contains every planned component exactly once.
9. No prompts, raw model output, provider metadata, or debug trace is exposed in the public API response.

Use provider-native structured output / JSON Schema where available. Backend validation is still mandatory; provider schema enforcement is not a substitute for application validation.

### Retry and failure policy

- Allow a small bounded retry for malformed or schema-invalid output (for example, one corrective retry per operation).
- A retry prompt should contain validation errors and request a correction, not regenerate unrelated fields.
- Never retry indefinitely.
- If decomposition fails, stop the pipeline.
- If a component fails after retries, fail generation cleanly. Do not return a recipe with a missing component.
- Do not persist a partially assembled recipe as completed.
- Return a stable, user-safe API error. Keep diagnostic details in server logs without logging secrets or unnecessary personal data.

## 6. Backend orchestration

Implement this through the existing recipe-generation abstraction and provider configuration. Provider-specific SDK types must remain behind the adapter boundary.

~~~text
New Recipe request
       |
       v
Normalize and validate request
       |
       v
CuratedRecipeLookup
       |
       +---- confident match ----> canonical curated recipe response
       |                           (no AI call)
       |
       +---- no confident match --> RecipeGenerationOrchestrator
                                      |
                                      v
                               Decompose recipe
                                      |
                                      v
                               Validate plan
                                      |
                                      v
                               Generate component recipes
                                      |
                                      v
                               Validate each component
                                      |
                                      v
                               Assemble canonical recipe
                                      |
                                      v
                               Final validation
                                      |
                                      v
                               Persist completed recipe
       |
       v
Return one complete recipe response
~~~

Keep these responsibilities separate:
- Catalog lookup and match confidence.
- AI provider adapter.
- Decomposition operation.
- Component generation operation.
- Schema/domain validation.
- Deterministic assembly.
- Persistence.

Do not put the whole pipeline in a controller or one large service method. Reuse existing service/entity/DTO conventions and avoid duplicating recipe persistence.

## 7. API and frontend behavior

Follow existing API route and response conventions. Choose the exact endpoint only after inspecting the current recipe controller and frontend API client; do not introduce a competing recipe API shape.

The request should carry the natural-language dish request and any preferences already supported by the application. Keep the first version minimal.

The response should:
- Identify source as CURATED or AI_GENERATED.
- Use the same canonical recipe detail shape for both sources.
- Include ordered components/steps required by the existing recipe detail and cooking-session screens.
- Never include intermediate decomposition or component-generation payloads.

Frontend requirements:
- Connect the **Yeni Tarif** button to the request flow.
- Provide a clear input for the dish request.
- Show a loading state until the complete assembled recipe is ready.
- Display a concise, user-safe error and allow retry if generation fails.
- On success, navigate to or render the existing recipe detail view.
- Preserve the existing cooking-session start flow.

Do not stream internal model responses in this iteration. A later feature may add meaningful progress states (e.g. "Tarif planı hazırlanıyor") without exposing raw intermediate content.

## 8. Persistence and ownership

- Curated catalog records are global/shared, not user-owned.
- User-generated recipes must follow the existing ownership rules.
- Preserve the current behavior for saving generated recipes to the requesting user's account.
- Store source type and, where useful, catalog ID or generation metadata.
- Do not persist raw prompts or provider output without a clear product requirement and privacy-safe retention policy.
- A failed generation must not create a visible incomplete recipe.

## 9. Testing requirements

### Catalog
- Exact canonical title match returns the curated recipe.
- Alias match returns the curated recipe.
- Normalization handles case, Turkish characters, punctuation, and redundant whitespace consistently.
- Weak/ambiguous matches do not select unrelated recipes.
- A curated match does not invoke the AI provider.
- Catalog import is idempotent and does not overwrite user-generated recipes.

### Decomposition
- Simple cake produces one component.
- Profiterole produces pastry, cream, and ganache components.
- Rose börek with ready-made yufka does not invent a homemade dough component.
- Homemade-dough wording can add a dough component.
- Duplicate keys, invalid dependencies, dependency cycles, and excessive component counts are rejected.

### Component generation and assembly
- Every planned component is generated exactly once.
- Each component response is independently validated.
- A component key mismatch is rejected.
- Invalid quantities, missing ingredients, empty instructions, and invalid step ordering are rejected.
- Assembly preserves component order and all component instructions.
- A failed component does not produce or persist a partial completed recipe.
- The assembled recipe maps correctly to the existing recipe detail response.

### API/frontend
- New Recipe submits the dish request.
- Loading, success, and failure states render correctly.
- Curated and AI-generated recipes render in the same detail experience.
- A completed recipe can start a cooking session.
- Intermediate AI payloads are never exposed to the client.

Use deterministic fake provider responses. Automated tests must not require live AI credentials or network access.

## 10. Non-goals

Do not include these unless the current implementation makes them necessary:
- Admin UI for editing curated recipes.
- A huge automatically generated default catalog.
- Vector database or external semantic-search infrastructure.
- Streaming intermediate model output.
- Advanced nutrition estimation or medical/dietary claims.
- Multi-agent orchestration.
- A separate cooking-session workflow unrelated to displaying and starting a recipe.

## 11. Definition of done

- [ ] Yeni Tarif button opens the dish request flow.
- [ ] Curated recipes are structured, version-controlled JSON and indexed in the database.
- [ ] Exact/alias catalog match returns the curated recipe without an AI call.
- [ ] No confident match triggers the AI pipeline.
- [ ] AI decomposes the dish into meaningful preparation components.
- [ ] Each component is generated through its own typed, validated response contract.
- [ ] Backend assembles one canonical recipe response.
- [ ] No intermediate/raw AI output reaches the frontend.
- [ ] Only complete, validated recipes are persisted.
- [ ] Curated and AI-generated recipes use the same detail experience.
- [ ] Tests cover catalog matching, decomposition, component generation, validation, assembly, and failure paths.
- [ ] Existing backend tests and frontend build pass.

## 12. Instructions for the coding agent

Before changing code:
1. Inspect the current recipe controller, generation service/provider interface, recipe entities, DTOs, repositories, frontend API client, and recipe detail flow.
2. Reuse existing architecture and persistence conventions. This document defines behavior and boundaries; it is not permission to create duplicate abstractions.
3. Identify the current development stub and replace/extend it behind the existing provider interface. Do not couple provider-specific SDK types to controllers or domain entities.
4. Implement in small vertical slices: catalog lookup, decomposition, component generation/assembly, then UI integration.
5. Add tests with fake providers before enabling a real provider.
6. Update docs/IMPLEMENTATION_STATUS.md only after verifying what is implemented. Do not mark a phase complete merely because code was added.
7. Run backend tests and frontend build. Report environment-blocked verification separately from code failures.

The implementation must remain compatible with the current Java 21 / Spring Boot backend, PostgreSQL persistence, and React/TypeScript/Vite web client. Keep the Expo client compatible with the same API contract; do not build a separate mobile-only generation implementation.
