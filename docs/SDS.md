# KITCHEN AI
# Software Development Specification & Agent Implementation Brief

**Document Version:** 1.0  
**Project Type:** AI-Powered Cooking Assistant  
**Development Stage:** MVP  
**Primary Goal:** Build a fully functional, end-to-end application.

---

# 0. INSTRUCTIONS FOR CODEX AGENT

You are responsible for designing, implementing, testing, and delivering a functional MVP of Kitchen AI.

Do not treat this document as a conceptual proposal. Treat it as the authoritative product and technical specification.

You must:

1. Analyze the existing repository before making changes.
2. Identify existing technologies, conventions, and project structure.
3. Preserve useful existing code and avoid unnecessary rewrites.
4. Implement both frontend and backend.
5. Implement database persistence.
6. Integrate a multimodal AI provider for image analysis and recipe generation.
7. Enforce structured AI output contracts.
8. Implement authentication and user-specific data isolation.
9. Write automated tests.
10. Provide setup instructions and environment configuration.
11. Ensure the application can run locally.
12. Do not leave critical features as mock implementations or TODO comments.

If the repository is empty, use the architecture specified below.

If an existing technology choice conflicts with this specification, prefer preserving the existing working architecture unless there is a strong technical reason to change it.

Do not silently omit requirements. Document any blocked functionality or external dependency.

## Definition of Done

The MVP is complete only when a user can:

- Register and log in.
- Open their dashboard.
- Start a new recipe recommendation.
- Describe a desired food outcome.
- Receive an AI-generated recipe.
- Start a cooking session.
- Upload a food image.
- Receive a short, structured AI assessment.
- Follow a corrective recommendation.
- Upload another image and receive a reassessment.
- Save and revisit cooking sessions.
- Earn XP and view progress.

---

# 1. PRODUCT OVERVIEW

## 1.1 Product Vision

Kitchen AI is an intelligent cooking companion that helps users achieve their desired food outcomes.

It is not a conventional recipe application.

Traditional recipe applications provide instructions. Kitchen AI evaluates the user's current food state and helps them reach the desired result.

The application has two primary capabilities:

### A. Recipe Suggestion

The user describes what they want to prepare.

AI generates a recipe based on:
- Desired food/product.
- Texture preferences.
- Taste preferences.
- Dietary restrictions.
- Special conditions.
- Available ingredients (optional).

Examples:

- Soft apple cinnamon protein cake.
- Low-sugar dessert for guests.
- Gluten-free pancakes.
- Mildly spicy chicken.
- A soft meal suitable for a specified age group.

### B. Recipe Recovery

The user has already started preparing something and encounters a problem.

They upload a photo and optionally describe the desired outcome or problem.

AI analyzes the visual state and suggests the most appropriate corrective action.

Examples:

- Batter is too thick.
- Sauce is too watery.
- Dough is too dry.
- Food is burning.
- Cream is separating.
- Cake is not rising.

The AI must prioritize immediate recovery rather than providing a new recipe.

## 1.2 Core Product Philosophy

**Complex AI reasoning, minimal user interaction, concise feedback.**

The user is actively cooking.

The application must never require unnecessary reading, typing, or configuration.

---

# 2. TECHNOLOGY STACK

Use the following architecture unless the existing repository requires otherwise.

## 2.1 Frontend

- React
- TypeScript
- Vite
- Tailwind CSS
- React Router
- TanStack Query
- React Hook Form
- Zod
- Axios
- Lucide React

Requirements:
- Responsive desktop and mobile layouts.
- Mobile-first interaction design.
- Camera and image upload support.
- Loading and error states.
- Accessible controls.
- Reusable component architecture.

## 2.2 Backend

- Java 21
- Spring Boot 3.x
- Spring Security
- Spring Data JPA
- Hibernate
- PostgreSQL
- Flyway
- Bean Validation
- JWT authentication
- Maven

Requirements:
- RESTful API.
- Clear service/repository/controller separation.
- DTO-based request and response handling.
- Global exception handling.
- Request validation.
- Transaction management.
- User-level authorization.

## 2.3 AI Integration

Create a provider-independent AI integration layer.

Do not couple business logic directly to a specific AI provider.

Suggested interfaces:

- `RecipeGenerationService`
- `FoodVisionService`
- `FoodAssessmentService`
- `RecommendationService`

Implement a configurable multimodal AI adapter.

Provider configuration must be managed through environment variables.

AI capabilities:
- Image understanding.
- Structured JSON generation.
- Recipe generation.
- Food state classification.
- Context-aware recommendations.

Use structured output or constrained JSON generation whenever supported by the selected provider.

Never assume an LLM response is valid simply because it contains JSON.

## 2.4 Storage

- PostgreSQL for application data.
- S3-compatible object storage for uploaded images in production.
- Local filesystem storage for development.

Images must not be stored directly as Base64 strings in PostgreSQL.

Store object keys and metadata.

---

# 3. APPLICATION ARCHITECTURE

Organize the application into the following logical modules.

## Frontend Modules

```text
src/
  app/
  components/
    common/
    dashboard/
    recipe/
    recovery/
    cooking-session/
    gamification/
  features/
    authentication/
    recipe-generation/
    recipe-recovery/
    cooking-history/
    user-profile/
  hooks/
  services/
  types/
  utils/
  pages/
  styles/
```

## Backend Modules

```text
com.kitchenai/
  auth/
  user/
  dashboard/
  recipe/
  cooking/
  recovery/
  vision/
  recommendation/
  gamification/
  storage/
  ai/
  common/
  config/
```

Each module should have appropriate controllers, services, repositories, DTOs and entities.

Keep AI provider integration separate from business logic.

---

# 4. USER EXPERIENCE & APPLICATION FLOW

## 4.1 Authentication

Implement:

- Registration.
- Login.
- Logout.
- JWT access and refresh tokens.
- Password hashing.
- Protected routes.

User data must be isolated by authenticated user ID.

## 4.2 Dashboard

The dashboard is the application's primary screen.

It should contain:

1. Greeting.
2. A prominent new-session action.
3. Two primary actions:
   - Suggest a Recipe.
   - Save My Recipe.
4. Recent cooking sessions.
5. XP and current cooking level.

The new-session button should use a plus icon.

### Dashboard Navigation

```text
Dashboard
  |
  +-- Suggest a Recipe
  |
  +-- Save My Recipe
  |
  +-- Cooking History
  |
  +-- Profile & Progress
```

Avoid unnecessary navigation complexity.

## 4.3 Recipe Suggestion Flow

User selects:

`Suggest a Recipe`

Screen fields:

- What do you want to make? (required)
- Additional conditions (optional).
- Dietary restrictions (optional).
- Available ingredients (optional).
- Number of servings (optional).

Additional conditions should use selectable chips.

Example conditions:

- Soft texture.
- Crispy.
- Moist.
- Low sugar.
- High protein.
- Gluten-free.
- Dairy-free.
- Mild flavor.
- Spicy.
- For guests.
- Suitable for a specified age group.

The user should be able to submit a natural-language description without selecting additional conditions.

AI should ask a question only when an essential detail is missing.

### Generated Recipe Screen

Display:

- Recipe name.
- Cover image or food illustration.
- Preparation time.
- Cooking time.
- Serving size.
- Ingredient quantities.
- Preparation stages.
- Target state for relevant stages.

Allow the user to start a cooking session.

Do not require generated recipes to contain AI-generated images. Food imagery can be added later.

---

# 5. RECIPE RECOVERY FLOW

This is the most important feature of the MVP.

User selects:

`Save My Recipe`

## 5.1 Input Screen

Required:
- Food image.

Optional:
- What is the desired outcome?
- What seems wrong?
- Additional context.

The user must be able to submit an image with minimal text.

Do not force users to fill out extensive forms.

Allow:
- Camera capture.
- Image upload.
- Image preview.
- Image replacement.

Supported image types:
- JPEG.
- PNG.
- WebP.

Apply configurable image size limits.

## 5.2 Recovery Processing

The backend must:

1. Authenticate the user.
2. Validate the uploaded image.
3. Store the image.
4. Retrieve existing session context if available.
5. Send image and contextual data to the AI vision service.
6. Obtain structured observations.
7. Compare observations with the target state.
8. Generate a corrective action.
9. Validate the action.
10. Return a concise UI response.

If the image cannot be reliably interpreted, return `UNCERTAIN`.

Never fabricate an observation.

---

# 6. AI OUTPUT CONTRACT

This is a critical architectural requirement.

The AI must not directly control the frontend through unrestricted text generation.

The backend owns the response contract.

## 6.1 Primary Response DTO

Create a strict response model.

```java
public record CookingFeedbackResponse(
    FeedbackStatus status,
    FeedbackAction action,
    String message,
    FeedbackDetails details,
    Double confidence,
    boolean requiresUserInput,
    NextStep nextStep
) {}
```

Suggested enums:

```java
public enum FeedbackStatus {
    OK,
    NEEDS_ADJUSTMENT,
    CONTINUE,
    WAIT,
    STOP,
    ASK_USER,
    UNCERTAIN,
    SAFETY_WARNING
}
```

```java
public enum FeedbackAction {
    ADD_INGREDIENT,
    REMOVE_INGREDIENT,
    MIX,
    REST,
    HEAT,
    COOL,
    BAKE,
    CONTINUE,
    STOP,
    WAIT,
    NO_ACTION,
    ASK,
    SAFETY_CHECK
}
```

```java
public enum NextStep {
    REASSESS,
    NEXT_STAGE,
    COMPLETE,
    WAIT_FOR_USER,
    STOP_PROCESS
}
```

`FeedbackDetails` should support:

```java
public record FeedbackDetails(
    String ingredient,
    BigDecimal quantity,
    String unit,
    Integer durationSeconds,
    String reasonCode
) {}
```

Optional values must be null when not applicable.

## 6.2 Message Generation

IMPORTANT:

The backend must not display arbitrary AI-generated explanations.

Use predefined message templates.

Example:

| Status / Action | Template |
|---|---|
| ADD_INGREDIENT | `{quantity} {unit} {ingredient} ekle.` |
| MIX | `{duration} saniye daha karıştır.` |
| REST | `{duration} dakika dinlendir.` |
| CONTINUE | `Devam edebilirsin.` |
| NO_ACTION | `Her şey yolunda, müdahale etme.` |
| WAIT | `{duration} dakika bekle.` |
| STOP | `İşlemi durdur.` |
| UNCERTAIN | `Daha net bir fotoğraf gönder.` |

Use localization-ready message keys.

The AI should return structured action parameters and reason codes.

The backend resolves the final user-facing message.

Do not expose internal reasoning.

## 6.3 Output Constraints

- Main message: maximum 12 words.
- Secondary message: maximum 15 words.
- Maximum two recommendations.
- Maximum one question.
- No unnecessary introductions.
- No long paragraphs.
- No generic cooking explanations.

Examples:

GOOD:
- `15 g süt ekle.`
- `30 saniye daha karıştır.`
- `Şimdi fırına verebilirsin.`
- `Kıvam harika, devam et!`

BAD:
- Long explanations.
- Multiple alternative actions without prioritization.
- Unnecessary recipe theory.
- Unsupported claims.

## 6.4 AI Response Validation

Implement server-side validation.

Validation must check:

- Valid JSON.
- Valid enum values.
- Required fields.
- Valid quantity ranges.
- Valid duration ranges.
- Message template availability.
- Action compatibility with current stage.
- Food safety constraints.

If validation fails:
1. Attempt one controlled structured-output retry.
2. If the retry fails, return a safe fallback.
3. Log the provider error.
4. Never display malformed AI output.

Do not let an invalid recommendation reach the user.

---

# 7. FOOD INTELLIGENCE MODEL

The application must represent food products and their expected states.

Create a reusable food knowledge model.

## 7.1 Food Categories

Initial supported categories:

- CAKE_BATTER
- PANCAKE_BATTER
- CREPE_BATTER
- BREAD_DOUGH
- SAUCE
- CREAM
- SOUP

These are the initial MVP categories.

Unsupported categories must not receive fabricated, category-specific corrective instructions.

The architecture must allow adding new food categories without rewriting the AI orchestration layer.

## 7.2 Target State

A target state describes the expected condition of a food product at a specific cooking stage.

Example:

```json
{
  "productType": "CAKE_BATTER",
  "stage": "BATTER_PREPARATION",
  "targetState": {
    "consistency": "IDEAL",
    "homogeneity": "HOMOGENEOUS",
    "surface": "NORMAL",
    "visibleLumps": false
  }
}
```

## 7.3 Observed State

The visual analysis service returns:

```json
{
  "productType": "CAKE_BATTER",
  "observations": {
    "consistency": "TOO_THICK",
    "homogeneity": "GOOD",
    "surface": "NORMAL",
    "visibleLumps": false
  },
  "confidence": 0.87
}
```

Use product-specific attributes.

Not every attribute applies to every category.

The system must distinguish:
- Observable properties.
- Partially observable properties.
- Non-observable properties.

Never infer internal temperature, exact salt concentration or food safety solely from a photograph.

---

# 8. AI ORCHESTRATION

Implement separate AI workflows.

## 8.1 Recipe Generation Workflow

```text
USER INPUT
    |
    v
INPUT NORMALIZATION
    |
    v
CONDITION EXTRACTION
    |
    v
RECIPE GENERATION
    |
    v
STRUCTURED OUTPUT VALIDATION
    |
    v
RECIPE PERSISTENCE
    |
    v
FRONTEND RESPONSE
```

Recipe generation output should contain:

- Recipe title.
- Description.
- Serving count.
- Ingredients.
- Exact quantities.
- Preparation stages.
- Stage-specific target states.
- Cooking temperature and duration where applicable.
- Safety notes when relevant.

## 8.2 Recipe Recovery Workflow

```text
USER IMAGE
    |
    v
IMAGE VALIDATION & STORAGE
    |
    v
CONTEXT RETRIEVAL
    |
    v
VISION ANALYSIS
    |
    v
OBSERVED STATE
    |
    v
TARGET STATE COMPARISON
    |
    v
RECOMMENDATION ENGINE
    |
    v
ACTION VALIDATION
    |
    v
MESSAGE TEMPLATE RESOLUTION
    |
    v
SHORT USER FEEDBACK
    |
    v
USER ACTION
    |
    v
NEW IMAGE & REASSESSMENT
```

Separate visual observation from recommendation generation.

This separation makes the system easier to test, evaluate and improve.

## 8.3 Provider Abstraction

Implement an interface similar to:

```java
public interface MultimodalAIProvider {

    RecipeGenerationResult generateRecipe(
        RecipeGenerationRequest request
    );

    FoodObservation analyzeFood(
        FoodVisionRequest request
    );

    RecoveryDecision generateRecoveryDecision(
        RecoveryContext context
    );
}
```

The exact implementation may use separate provider adapters or model configurations.

AI models and provider-specific parameters must be configurable.

Never hardcode API credentials.

---

# 9. FOOD SAFETY

Safety must be implemented as a separate validation layer.

Examples:

- Meat doneness cannot be verified solely through visual analysis.
- Internal food temperatures require appropriate measurements.
- Potentially spoiled ingredients must not be treated as recoverable.
- Allergen constraints must be preserved.
- Dietary restrictions must not be silently ignored.
- Baby-related recipes require additional age and ingredient safety considerations.
- Unsafe heating, cooling and storage advice must be rejected.

When safety cannot be established, ask for the necessary measurement or recommend stopping the process.

The application must not claim that food is safe based solely on appearance.

---

# 10. DATABASE DESIGN

Use PostgreSQL and Flyway migrations.

Suggested entities:

## User

```text
id
email
password_hash
display_name
created_at
updated_at
```

## Recipe

```text
id
user_id
title
description
product_type
servings
preparation_time
cooking_time
source_type
created_at
```

`source_type`:
- AI_GENERATED
- USER_CREATED
- RECOVERY

## RecipeIngredient

```text
id
recipe_id
name
quantity
unit
optional
```

## RecipeStage

```text
id
recipe_id
stage_order
title
instructions
target_state_json
temperature
duration_seconds
```

## CookingSession

```text
id
user_id
recipe_id
session_type
status
current_stage
desired_outcome
context_json
started_at
completed_at
```

`session_type`:
- RECIPE
- RECOVERY

`status`:
- ACTIVE
- COMPLETED
- ABANDONED

## CookingObservation

```text
id
session_id
image_object_key
observed_state_json
target_comparison_json
confidence
created_at
```

## CookingFeedback

```text
id
session_id
observation_id
status
action
action_details_json
reason_code
message_key
resolved_message
created_at
```

## CookingAction

```text
id
session_id
feedback_id
action_description
user_confirmed
created_at
```

## UserProgress

```text
id
user_id
total_xp
current_level
updated_at
```

## XPTransaction

```text
id
user_id
session_id
amount
reason
created_at
```

All user-owned entities must enforce ownership checks.

Use JSONB for flexible target-state and observation attributes, but maintain stable identifiers and typed DTOs in application code.

Add appropriate foreign keys, indexes and uniqueness constraints.

---

# 11. REST API DESIGN

Base path:

`/api/v1`

## Authentication

```text
POST /auth/register
POST /auth/login
POST /auth/refresh
POST /auth/logout
GET  /auth/me
```

## Dashboard

```text
GET /dashboard
GET /dashboard/recent-sessions
GET /dashboard/progress
```

## Recipe Generation

```text
POST /recipes/generate
GET  /recipes/{id}
GET  /recipes
DELETE /recipes/{id}
```

## Cooking Sessions

```text
POST /cooking-sessions
GET  /cooking-sessions/{id}
GET  /cooking-sessions
POST /cooking-sessions/{id}/complete
POST /cooking-sessions/{id}/abandon
```

## Recovery

```text
POST /cooking-sessions/{id}/observations
GET  /cooking-sessions/{id}/observations
GET  /cooking-sessions/{id}/feedback
POST /cooking-sessions/{id}/actions
```

The observation endpoint must accept multipart image upload and optional contextual information.

Return the structured feedback response.

## Gamification

```text
GET /gamification/progress
GET /gamification/transactions
GET /gamification/badges
```

Use consistent API error responses.

Never expose internal stack traces to clients.

---

# 12. GAMIFICATION

Implement a simple XP system.

Suggested initial rules:

| Activity | XP |
|---|---:|
| Complete a recipe | +10 |
| Successful recovery | +20 |
| Try a new product category | +15 |
| Submit session feedback | +5 |
| Improve a previous result | +10 |

XP must be awarded only once per qualifying event.

Use database transactions to prevent duplicate rewards.

Do not award successful recovery points merely because the AI suggested an action.

A successful recovery should require a subsequent assessment or explicit user confirmation, according to the event type.

Initial levels:

- Beginner Chef
- Developing Chef
- Home Cook
- Skilled Cook
- Kitchen Master

Implement the level thresholds as configurable application settings.

Badges can be introduced with simple achievement rules.

---

# 13. FRONTEND DESIGN SYSTEM

The UI should feel modern, friendly, minimal and premium.

It should not resemble an enterprise dashboard.

Design priorities:

- Large food imagery.
- Clear hierarchy.
- Generous spacing.
- Rounded cards.
- Minimal navigation.
- Simple action buttons.
- Short text.
- Mobile-first layouts.

## Required Pages

1. Login.
2. Registration.
3. Dashboard.
4. Recipe suggestion form.
5. Generated recipe.
6. Recovery upload screen.
7. Recovery result screen.
8. Cooking session screen.
9. Cooking history.
10. Session detail.
11. User progress/profile.

## Important UX Requirements

### Dashboard

The two primary actions must be immediately visible.

### Recipe Suggestion

Natural-language input should be the primary interaction.

Condition chips are optional.

### Recipe Recovery

Image upload must be the primary interaction.

Additional text must be optional.

### Feedback Screen

Display:
- Status.
- One primary recommendation.
- Optional secondary instruction.
- Reassessment button.

Do not display raw JSON, model confidence values or internal reasoning.

### Loading States

AI processing should have clear loading indicators.

Example:

- Fotoğraf inceleniyor...
- Ürün durumu değerlendiriliyor...
- En uygun çözüm hazırlanıyor...

Do not invent fake progress percentages.

---

# 14. IMAGE HANDLING

Implement secure image processing.

Requirements:

- Validate actual image format.
- Enforce maximum file size.
- Reject unsupported formats.
- Generate unique storage keys.
- Avoid trusting client-provided filenames.
- Use temporary signed URLs where appropriate.
- Restrict image access to authorized users.
- Configure image retention policy.

For development, provide local storage.

For production, provide an S3-compatible storage adapter.

Do not expose private storage credentials or unrestricted object URLs.

---

# 15. SECURITY & RELIABILITY

Implement:

- Password hashing with BCrypt or Argon2.
- JWT authentication.
- Refresh-token rotation.
- User-level authorization.
- Request validation.
- Rate limiting on AI endpoints.
- Upload size limits.
- Secure CORS configuration.
- Environment-based secrets.
- AI request timeouts.
- Controlled retry policies.
- Structured application logging.

Do not log:
- Passwords.
- Authentication tokens.
- API keys.
- Sensitive user information.

AI calls must have configurable timeouts and bounded retries.

Avoid duplicate AI processing when users accidentally submit the same request multiple times.

Use request identifiers or idempotency keys where appropriate.

---

# 16. TESTING STRATEGY

Implement automated tests at multiple levels.

## Backend Unit Tests

Test:
- Recipe generation validation.
- Visual observation parsing.
- Target state comparison.
- Recommendation selection.
- Message template resolution.
- XP calculations.
- Authorization rules.

## Backend Integration Tests

Test:
- Authentication.
- Recipe persistence.
- Cooking session lifecycle.
- Image upload.
- AI provider integration through mocks.
- Feedback persistence.
- User data isolation.

Use Testcontainers for PostgreSQL integration tests where practical.

## Frontend Tests

Test:
- Authentication flows.
- Dashboard navigation.
- Recipe generation form.
- Image upload.
- Feedback rendering.
- Loading and error states.

## AI Evaluation Tests

Create a versioned evaluation dataset containing representative cooking images and expected observations.

Include:

- Correct product states.
- Minor deviations.
- Severe deviations.
- Ambiguous images.
- Incorrect product identification.
- Unsupported food categories.
- Safety-related edge cases.

Track:

- Observation accuracy.
- Recommendation correctness.
- False intervention rate.
- Unsafe recommendation rate.
- Structured output validity.
- Average response length.

AI evaluation must be separate from ordinary unit tests.

Do not claim visual accuracy without measured evaluation results.

---

# 17. DEVELOPMENT PHASES

Implement the project incrementally.

## Phase 1 — Project Foundation

- Inspect repository.
- Establish frontend and backend structure.
- Configure PostgreSQL.
- Configure Flyway.
- Implement authentication.
- Implement dashboard.
- Implement basic navigation.

Deliverable: Running authenticated application.

## Phase 2 — Recipe Generation

- Recipe input form.
- Condition selection.
- AI provider integration.
- Structured recipe output.
- Recipe persistence.
- Recipe detail screen.
- Cooking session creation.

Deliverable: End-to-end recipe generation.

## Phase 3 — Visual Recovery Engine

- Image upload.
- Storage abstraction.
- Vision integration.
- Visual observation schema.
- Target state comparison.
- Recommendation engine.
- Feedback templates.
- Recovery screen.

Deliverable: End-to-end food recovery workflow.

## Phase 4 — Closed-Loop Cooking

- Cooking observations.
- User action recording.
- Reassessment.
- Session context management.
- Session completion.

Deliverable: Multi-step cooking assistance.

## Phase 5 — History & Gamification

- Cooking history.
- Session details.
- XP system.
- Levels.
- Basic badges.

Deliverable: Persistent user progress.

## Phase 6 — Quality & Delivery

- Automated tests.
- Error handling.
- Security review.
- Responsive UI improvements.
- Documentation.
- Local startup instructions.

Deliverable: Working MVP.

Do not skip directly to UI polishing before the core workflows work.

---

# 18. ENVIRONMENT CONFIGURATION

Provide a complete `.env.example` or equivalent configuration template.

Expected variables:

```properties
DATABASE_URL=
DATABASE_USERNAME=
DATABASE_PASSWORD=

JWT_SECRET=

AI_PROVIDER=
AI_API_KEY=
AI_RECIPE_MODEL=
AI_VISION_MODEL=

STORAGE_PROVIDER=
STORAGE_LOCAL_PATH=

S3_ENDPOINT=
S3_BUCKET=
S3_ACCESS_KEY=
S3_SECRET_KEY=

FRONTEND_URL=
```

Never commit real credentials.

Document how to configure the AI provider.

Provide Docker Compose for local PostgreSQL and any required infrastructure.

---

# 19. REQUIRED PROJECT DOCUMENTATION

Create the following files:

```text
README.md
docs/
  PRODUCT_SPEC.md
  SYSTEM_ARCHITECTURE.md
  AI_OUTPUT_CONTRACT.md
  FOOD_INTELLIGENCE_MODEL.md
  API_SPECIFICATION.md
  DATABASE_SCHEMA.md
  SECURITY.md
  TESTING.md
  IMPLEMENTATION_STATUS.md
```

The documentation must reflect the actual implementation.

Do not document unimplemented features as completed.

---

# 20. MVP ACCEPTANCE CRITERIA

The following scenarios must work.

### Scenario A — Recipe Generation

Given:
- User wants a soft apple cinnamon protein cake.

When:
- User submits the request.

Then:
- AI generates a structured recipe.
- Ingredient quantities are provided.
- Cooking stages are available.
- Relevant target states are defined.
- Recipe is saved to the user's history.

### Scenario B — Food Recovery

Given:
- User uploads a photo of overly thick cake batter.
- User indicates that the desired result is a soft cake.

When:
- AI analyzes the image.

Then:
- The system identifies observable characteristics.
- The system compares the current state with the target.
- A valid corrective action is generated.
- The user receives a concise recommendation.

Example:

`15 g süt ekle.`

### Scenario C — Successful Reassessment

Given:
- User follows the recommendation.
- User uploads another image.

When:
- AI evaluates the new state.

Then:
- The previous context is preserved.
- The new state is evaluated.
- The system determines whether another correction is needed.
- If the target state is acceptable, it returns:

`Kıvam harika, devam et!`

### Scenario D — Insufficient Image

Given:
- User uploads an unclear photograph.

When:
- AI cannot reliably determine the food state.

Then:
- It must not invent a diagnosis.
- It must request a better image or ask one relevant question.

### Scenario E — User Isolation

Given:
- Two different users have cooking sessions.

When:
- One user attempts to access the other's session.

Then:
- The backend must reject the request.

### Scenario F — Gamification

Given:
- User completes a qualifying cooking session.

When:
- Completion is recorded.

Then:
- XP is awarded once.
- Progress is persisted.
- Dashboard progress is updated.

---

# 21. DEVELOPMENT RULES FOR THE AGENT

1. Follow clean code principles.
2. Prefer small, cohesive services.
3. Avoid unnecessary abstractions.
4. Keep AI orchestration separate from domain logic.
5. Keep frontend components reusable.
6. Use typed DTOs and validation.
7. Use database migrations.
8. Do not hardcode credentials.
9. Do not silently swallow exceptions.
10. Do not use mock AI responses in production workflows.
11. Do not let unrestricted LLM text reach the user-facing feedback component.
12. Do not generate arbitrary corrective quantities without contextual validation.
13. Do not claim that a food product is safe based solely on a photograph.
14. Keep the application runnable throughout development.
15. Update IMPLEMENTATION_STATUS.md after every development phase.

If a real AI provider credential is unavailable, implement the provider adapter and a clearly isolated development stub. Make it impossible to mistake stub responses for real AI assessments in the UI.

---

# 22. FINAL DELIVERY REQUIREMENTS

At completion, provide:

1. Complete source code.
2. Working frontend.
3. Working backend.
4. Database migrations.
5. AI provider integration.
6. Environment configuration template.
7. Docker Compose configuration.
8. Automated tests.
9. Project documentation.
10. Local startup instructions.
11. A summary of implemented features.
12. A list of remaining limitations.

Report:
- Which phases are complete.
- Which tests passed.
- Which tests failed.
- Which features require external API credentials.
- Which features remain incomplete.

Do not report successful implementation without running the relevant tests.

---

# FINAL PRODUCT PRINCIPLE

Kitchen AI is not a chatbot that happens to know recipes.

It is a cooking decision-support system.

Its intelligence comes from understanding the desired outcome, interpreting the current food state, and selecting an appropriate next action.

**The AI should think deeply. The user should barely need to think about the interface.**

Build the application around this principle.
