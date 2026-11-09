package com.kitchenai.ai;

import static com.kitchenai.ai.RecipeContracts.ComponentIngredient;
import static com.kitchenai.ai.RecipeContracts.ComponentInstruction;
import static com.kitchenai.ai.RecipeContracts.ComponentPlan;
import static com.kitchenai.ai.RecipeContracts.RecipeComponentGenerationResponse;
import static com.kitchenai.ai.RecipeContracts.RecipeDecompositionResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kitchenai.common.ApiException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Runware adapter. Provider JSON never leaves this class. */
@Service
@Profile("runware")
public class RunwareRecipeGenerationService
    implements RecipeGenerationService, RecipeReviewService {
  private static final String API_URL = "https://api.runware.ai/v1/chat/completions";
  private static final Logger log = LoggerFactory.getLogger(RunwareRecipeGenerationService.class);
  private final ObjectMapper mapper;
  private final HttpClient http;
  private final String apiKey;
  private final String model;
  private final AiOutputNormalizer outputNormalizer;

  public RunwareRecipeGenerationService(
      ObjectMapper mapper, org.springframework.core.env.Environment environment,
      AiOutputNormalizer outputNormalizer) {
    this.mapper = mapper;
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    this.apiKey = environment.getProperty("app.ai.runware.api-key", "");
    this.model = environment.getProperty("app.ai.runware.model", "openai:gpt@5.4-mini");
    this.outputNormalizer = outputNormalizer;
  }

  @Override
  public RecipeResult generate(RecipeRequest request) {
    return generateWithTrace(request).recipe();
  }

  @Override
  public GenerationTrace generateWithTrace(RecipeRequest request) {
    if (apiKey.isBlank()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI provider is not configured");
    }
    RecipeDecompositionResponse plan = decomposition(request);
    List<ComponentPlan> ordered =
        plan.components().stream().sorted(Comparator.comparingInt(ComponentPlan::order)).toList();
    log.info(
        "Runware recipe plan model={} componentCount={} componentKeys={}",
        model,
        ordered.size(),
        ordered.stream().map(ComponentPlan::key).toList());
    List<RecipeComponentGenerationResponse> components = new ArrayList<>();
    for (ComponentPlan component : ordered) {
      RecipeComponentGenerationResponse generated = generateComponent(request, plan, component);
      log.info(
          "Runware component generated key={} instructionCount={}",
          generated.componentKey(),
          generated.instructions().size());
      components.add(generated);
    }
    RecipeResult result = assemble(request, plan, components);
    log.info(
        "Runware recipe assembled source=AI_GENERATED stageCount={} ingredientCount={}",
        result.stages().size(),
        result.ingredients().size());
    return new GenerationTrace(result, plan, components);
  }

  @Override
  public RecipeResult review(RecipeResult recipe) {
    String prompt =
        """
        Review and finalize this Turkish recipe before it is shown to a user.
        Return JSON only and follow the provided schema.
        Correct awkward or incorrect Turkish cooking terminology and stage names.
        For example, replace 'yufkalandırma' with 'yufkayı açma' when that is what
        the instruction actually means. Preserve the recipe's dish identity,
        ingredient quantities, cooking safety, and practical instructions.
        Do not add unsupported ingredients or invent a different recipe.
        Keep all human-readable fields in Turkish.
        Recipe to review:
        %s
        """
            .formatted(safeJson(recipe));
    try {
      ObjectNode schema = reviewSchema();
      ModelPayload modelPayload = requestJson(prompt, schema, "recipe-review");
      JsonNode payload = modelPayload.json();
      try {
        RecipeResult reviewed = mapper.treeToValue(payload, RecipeResult.class);
        validateReviewedRecipe(reviewed);
        log.info("Runware chef review completed title={} stageCount={}",
            reviewed.title(), reviewed.stages().size());
        return reviewed;
      } catch (Exception validationFailure) {
        if (modelPayload.normalized()) throw new ApiException(HttpStatus.BAD_GATEWAY, "Recipe review returned an invalid structure");
        JsonNode normalized = normalizeParsedPayload(payload, schema, "recipe-review",
            "Preserve the reviewed recipe content. Do not invent ingredients, amounts, temperatures, or timings. Use null only where the target schema permits it.");
        RecipeResult reviewed = mapper.treeToValue(normalized, RecipeResult.class);
        validateReviewedRecipe(reviewed);
        log.info("Runware chef review output normalized and validated");
        return reviewed;
      }
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      log.warn(
          "Runware chef review failed type={} message={}",
          e.getClass().getSimpleName(),
          e.getMessage());
      throw new ApiException(HttpStatus.BAD_GATEWAY, "Recipe review failed");
    }
  }

  private void validateReviewedRecipe(RecipeResult recipe) {
    if (recipe == null
        || recipe.title() == null
        || recipe.title().isBlank()
        || recipe.ingredients() == null
        || recipe.ingredients().isEmpty()
        || recipe.stages() == null
        || recipe.stages().isEmpty()) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "Recipe review returned an incomplete recipe");
    }
    int order = 1;
    for (Stage stage : recipe.stages()) {
      if (stage.order() != order++
          || stage.instructions() == null
          || stage.instructions().isBlank()) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "Recipe review returned invalid stages");
      }
    }
  }

  private ObjectNode reviewSchema() {
    return (ObjectNode)
        readSchema(
            """
{"type":"object","additionalProperties":false,"required":["title","description","productType","servings","preparationTime","cookingTime","ingredients","stages"],"properties":{"title":{"type":"string","minLength":1},"description":{"type":"string"},"productType":{"type":"string","minLength":1},"servings":{"type":"integer","minimum":1,"maximum":30},"preparationTime":{"type":"integer","minimum":1},"cookingTime":{"type":"integer","minimum":1},"ingredients":{"type":"array","minItems":1,"items":{"type":"object","additionalProperties":false,"required":["name","quantity","unit","optional"],"properties":{"name":{"type":"string","minLength":1},"quantity":{"type":"number","exclusiveMinimum":0},"unit":{"type":"string","minLength":1},"optional":{"type":"boolean"}}}},"stages":{"type":"array","minItems":1,"items":{"type":"object","additionalProperties":false,"required":["order","title","instructions","targetState","temperature","durationSeconds"],"properties":{"order":{"type":"integer","minimum":1},"title":{"type":"string","minLength":1},"instructions":{"type":"string","minLength":1},"targetState":{"type":"string","minLength":1},"temperature":{"type":["integer","null"]},"durationSeconds":{"type":["integer","null"]}}}}}}
""");
  }

  private RecipeDecompositionResponse decomposition(RecipeRequest request) {
    String prompt =
        """
        Decompose the requested dish into meaningful preparation components.
        Return JSON only and follow the provided schema exactly.
        Do not create a component for every individual instruction.
        Include only components required by the requested dish.
        If the user says ready-made yufka, do not invent a homemade dough component.
        Use stable machine-friendly component keys in kebab-case.
        Preserve the user's assumptions and do not silently substitute another dish.
        Keep the dish title and all human-readable values in Turkish.
        Do not return a stage count. Each component will provide its own ordered
        instructions later, and the backend will derive the final stage count.

        User request: %s
        Requested servings: %s
        Conditions: %s
        Dietary restrictions: %s
        Available ingredients: %s
        """
            .formatted(
                request.desiredFood(),
                value(request.servings(), 4),
                value(request.conditions(), "belirtilmedi"),
                value(request.dietaryRestrictions(), "belirtilmedi"),
                value(request.availableIngredients(), "belirtilmedi"));
    ObjectNode schema = decompositionSchema();
    ModelPayload modelPayload = requestJsonSafely(prompt, schema, "decomposition");
    JsonNode payload = modelPayload.json();
    try {
      return RecipePipelineValidator.decomposition(parseDecomposition(payload, request));
    } catch (Exception validationFailure) {
      if (modelPayload.normalized()) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "Recipe planning returned an invalid structure");
      }
      try {
        JsonNode normalized = normalizeParsedPayload(payload, schema, "decomposition",
            "Preserve dish identity, component relationships, and source facts. Do not invent components or dependencies.");
        return RecipePipelineValidator.decomposition(parseDecomposition(normalized, request));
      } catch (Exception normalizationFailure) {
        log.warn("Runware decomposition remained invalid after normalization: validationType={}, normalizationType={}",
            validationFailure.getClass().getSimpleName(), normalizationFailure.getClass().getSimpleName());
        throw new ApiException(HttpStatus.BAD_GATEWAY, "Recipe planning returned an invalid structure");
      }
    }
  }

  private RecipeComponentGenerationResponse generateComponent(
      RecipeRequest request, RecipeDecompositionResponse plan, ComponentPlan component) {
    String prompt =
        """
        Generate one complete recipe component.
        Return JSON only and follow the provided schema exactly.
        The componentKey must match '%s' exactly.
        Generate practical, sequential cooking instructions, not vague advice.
        Each instruction is one observable cooking stage and must include a useful
        completion cue such as texture, color, consistency, temperature, or a test.
        Include temperature and duration when they are reasonably determinable;
        otherwise use null. Do not invent unsafe precision.
        Keep all human-readable values in Turkish.
        Do not return a separate stage count: the number of instructions is the
        number of stages for this component, and the backend assembles the final list.

        Original user request: %s
        Dish title: %s
        Servings: %d
        Complete component plan: %s
        Current component: %s
        """
            .formatted(
                component.key(),
                request.desiredFood(),
                plan.dishTitle(),
                plan.servings(),
                safeJson(plan),
                safeJson(component));
    ObjectNode schema = componentSchema();
    ModelPayload modelPayload = requestJsonSafely(prompt, schema, "component-" + component.key());
    JsonNode payload = modelPayload.json();
    try {
      RecipeComponentGenerationResponse result = mapper.treeToValue(payload, RecipeComponentGenerationResponse.class);
      return RecipePipelineValidator.component(result, component.key());
    } catch (Exception validationFailure) {
      if (modelPayload.normalized()) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "Recipe component returned an invalid structure");
      }
      try {
        JsonNode normalized = normalizeParsedPayload(payload, schema, "component-" + component.key(),
            "Preserve the requested componentKey exactly. Do not invent ingredients, quantities, temperatures, or timings.");
        RecipeComponentGenerationResponse result = mapper.treeToValue(normalized, RecipeComponentGenerationResponse.class);
        return RecipePipelineValidator.component(result, component.key());
      } catch (Exception normalizationFailure) {
        log.warn("Runware component remained invalid after normalization: key={}, validationType={}, normalizationType={}",
            component.key(), validationFailure.getClass().getSimpleName(),
            normalizationFailure.getClass().getSimpleName());
        throw new ApiException(HttpStatus.BAD_GATEWAY, "Recipe component generation returned an invalid structure");
      }
    }
  }

  private RecipeDecompositionResponse parseDecomposition(JsonNode payload, RecipeRequest request)
      throws IOException {
    JsonNode normalized = payload;
    if (payload.isArray()) {
      ObjectNode wrapper = mapper.createObjectNode();
      wrapper.put("dishTitle", request.desiredFood());
      wrapper.put("servings", request.servings() == null ? 4 : request.servings());
      wrapper.set("components", payload);
      wrapper.put("assemblyRequired", true);
      normalized = wrapper;
      log.info("Runware decomposition normalized array response componentCount={}", payload.size());
    }
    return mapper.treeToValue(normalized, RecipeDecompositionResponse.class);
  }

  private RecipeResult assemble(
      RecipeRequest request,
      RecipeDecompositionResponse plan,
      List<RecipeComponentGenerationResponse> components) {
    List<Ingredient> ingredients = new ArrayList<>();
    List<Stage> stages = new ArrayList<>();
    int stageOrder = 1;
    int maxDuration = 0;
    for (RecipeComponentGenerationResponse component : components) {
      for (ComponentIngredient ingredient : component.ingredients()) {
        ingredients.add(
            new Ingredient(ingredient.name(), ingredient.quantity(), ingredient.unit(), false));
      }
      for (ComponentInstruction instruction : component.instructions()) {
        stages.add(
            new Stage(
                stageOrder++,
                component.title(),
                instruction.instruction(),
                instruction.completionCue(),
                instruction.temperature(),
                instruction.durationSeconds()));
        if (instruction.durationSeconds() != null) {
          maxDuration = Math.max(maxDuration, instruction.durationSeconds());
        }
      }
    }
    return new RecipeResult(
        plan.dishTitle(),
        "İsteğine göre hazırlanmış tarif.",
        "AI_GENERATED",
        plan.servings(),
        15,
        Math.max(1, maxDuration / 60),
        ingredients,
        stages);
  }

  private ModelPayload requestJsonSafely(String prompt, ObjectNode schema, String operation) {
    try {
      return requestJson(prompt, schema, operation);
    } catch (ApiException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Runware provider operation={} was interrupted", operation);
      throw new ApiException(HttpStatus.BAD_GATEWAY, "AI request was interrupted");
    } catch (Exception e) {
      log.warn("Runware provider operation={} failed before a usable response: type={}, message={}",
          operation, e.getClass().getSimpleName(), e.getMessage());
      throw new ApiException(HttpStatus.BAD_GATEWAY, "AI request failed");
    }
  }

  private ModelPayload requestJson(String prompt, ObjectNode schema, String operation)
      throws IOException, InterruptedException {
    String lastError = "";
    // Provider calls are billable. Never automatically retry after a response.
    for (int attempt = 0; attempt < 1; attempt++) {
      ObjectNode body = mapper.createObjectNode();
      body.put("model", model);
      body.put("max_completion_tokens", 1800);
      body.put("reasoning_effort", "none");
      var messages = body.putArray("messages");
      messages
          .addObject()
          .put("role", "system")
          .put(
              "content",
              "You are a strict recipe JSON generator. "
                  + "Return only valid JSON, never markdown or explanations. "
                  + "Follow the requested schema and keep human-readable recipe "
                  + "content in Turkish.");
      messages
          .addObject()
          .put("role", "user")
          .put("content", prompt + "\nExpected JSON schema:\n" + schema);
      ObjectNode responseFormat = body.putObject("response_format");
      // json_object is supported more consistently across Runware's compatible models.
      // The application schema remains enforced by RecipePipelineValidator.
      responseFormat.put("type", "json_object");

      HttpRequest httpRequest =
          HttpRequest.newBuilder(URI.create(API_URL))
              .timeout(Duration.ofSeconds(45))
              .header("Authorization", "Bearer " + apiKey)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
              .build();
      HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        lastError = providerError(response.body(), response.statusCode());
        log.warn(
            "Runware provider operation={} attempt={} failed={}",
            operation,
            attempt + 1,
            lastError);
        if (response.statusCode() == 429) {
          throw new ApiException(
              HttpStatus.TOO_MANY_REQUESTS,
              "AI provider balance or capacity is temporarily unavailable");
        }
        continue;
      }
      JsonNode root = mapper.readTree(response.body());
      JsonNode choice = root.path("choices").path(0);
      JsonNode message = choice.path("message");
      String contentText = responseText(root, choice, message);
      if (contentText != null && !contentText.isBlank()) {
        try {
          return new ModelPayload(parseJsonPayload(contentText), false);
        } catch (Exception parseFailure) {
          lastError = "response was not valid JSON";
          log.warn(
              "Runware provider operation={} returned invalid JSON; attempting one normalization " +
                  "length={} preview={}",
              operation, contentText.length(), preview(contentText));
          if (outputNormalizer.available()) {
            try {
              String normalized = outputNormalizer.normalize(operation, contentText,
                  mapper.writeValueAsString(schema),
                  "Convert this recipe-generation response to the target schema without adding unsupported facts. Preserve source quantities and instructions. Unknown values must remain null or cause validation failure.");
              return new ModelPayload(mapper.readTree(normalized), true);
            } catch (Exception normalizationFailure) {
              lastError = "response JSON normalization failed";
              log.warn("Runware provider operation={} normalization failed type={} detail={}",
                  operation, normalizationFailure.getClass().getSimpleName(),
                  normalizationFailure.getMessage());
            }
          }
        }
      } else {
        String refusal = message.path("refusal").asText("");
        String finishReason = choice.path("finish_reason").asText("unknown");
        lastError =
            refusal.isBlank()
                ? "provider returned no JSON content"
                : "provider refused the request";
        log.warn(
            "Runware provider operation={} attempt={} returned no content "
                + "finishReason={} messageFields={} refusalPresent={}",
            operation,
            attempt + 1,
            finishReason,
            fieldNames(message),
            !refusal.isBlank());
      }
    }
    log.warn(
        "Runware provider operation={} exhausted without usable JSON reason={}",
        operation,
        lastError);
    throw new ApiException(HttpStatus.BAD_GATEWAY, "AI recipe generation failed");
  }

  private record ModelPayload(JsonNode json, boolean normalized) {}

  private JsonNode normalizeParsedPayload(JsonNode payload, ObjectNode schema,
      String operation, String rules) throws IOException {
    if (!outputNormalizer.available()) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "AI output does not match the required structure");
    }
    String normalized = outputNormalizer.normalize(operation,
        mapper.writeValueAsString(payload), mapper.writeValueAsString(schema), rules);
    return mapper.readTree(normalized);
  }

  private String providerError(String responseBody, int statusCode) {
    try {
      JsonNode root = mapper.readTree(responseBody);
      String message = root.path("error").path("message").asText("");
      if (!message.isBlank()) {
        return statusCode + " " + message.substring(0, Math.min(message.length(), 240));
      }
    } catch (Exception ignored) {
      // Keep provider response details out of logs when the response is not JSON.
    }
    return "provider status " + statusCode;
  }

  private String responseText(JsonNode root, JsonNode choice, JsonNode message) {
    String topLevel = textValue(root.path("output_text"));
    if (topLevel != null) {
      return topLevel;
    }
    String dataText = textValue(root.path("data").path(0).path("text"));
    if (dataText != null) {
      return dataText;
    }
    String choiceText = textValue(choice.path("text"));
    if (choiceText != null) {
      return choiceText;
    }
    String content = contentText(message.path("content"));
    if (content != null) {
      return content;
    }
    String reasoning = textValue(message.path("reasoning_content"));
    return reasoning != null ? reasoning : findNestedText(root);
  }

  private String contentText(JsonNode content) {
    if (content.isTextual()) {
      return content.asText();
    }
    if (content.isArray()) {
      StringBuilder text = new StringBuilder();
      for (JsonNode item : content) {
        if ((item.path("type").asText("").equals("text")
                || item.path("type").asText("").equals("output_text"))
            && item.path("text").isTextual()) {
          text.append(item.path("text").asText());
        }
      }
      return text.toString();
    }
    return null;
  }

  private String textValue(JsonNode node) {
    return node.isTextual() ? node.asText() : null;
  }

  private String findNestedText(JsonNode node) {
    if (node.isObject()) {
      for (String field : List.of("output_text", "content", "text", "reasoning_content")) {
        JsonNode value = node.get(field);
        if (value != null && value.isTextual() && !value.asText().isBlank()) {
          return value.asText();
        }
      }
      var fields = node.fields();
      while (fields.hasNext()) {
        String nested = findNestedText(fields.next().getValue());
        if (nested != null) {
          return nested;
        }
      }
    } else if (node.isArray()) {
      for (JsonNode item : node) {
        String nested = findNestedText(item);
        if (nested != null) {
          return nested;
        }
      }
    }
    return null;
  }

  private String fieldNames(JsonNode node) {
    List<String> names = new ArrayList<>();
    node.fieldNames().forEachRemaining(names::add);
    return names.toString();
  }

  private JsonNode parseJsonPayload(String value) throws IOException {
    String payload =
        value.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
    try {
      return mapper.readTree(payload);
    } catch (IOException firstFailure) {
      for (int start = 0; start < payload.length(); start++) {
        char first = payload.charAt(start);
        if (first != '{' && first != '[') {
          continue;
        }
        int end = balancedEnd(payload, start, first);
        if (end > start) {
          try {
            return mapper.readTree(payload.substring(start, end + 1));
          } catch (IOException ignored) {
            // Try the next possible JSON root in the response text.
          }
        }
      }
      throw firstFailure;
    }
  }

  private int balancedEnd(String value, int start, char opening) {
    char closing = opening == '{' ? '}' : ']';
    int depth = 0;
    boolean quoted = false;
    boolean escaped = false;
    for (int index = start; index < value.length(); index++) {
      char current = value.charAt(index);
      if (quoted) {
        if (escaped) {
          escaped = false;
        } else if (current == '\\') {
          escaped = true;
        } else if (current == '"') {
          quoted = false;
        }
        continue;
      }
      if (current == '"') {
        quoted = true;
      } else if (current == opening) {
        depth++;
      } else if (current == closing && --depth == 0) {
        return index;
      }
    }
    return -1;
  }

  private String preview(String value) {
    String normalized =
        value
            .replaceAll("\\s+", " ")
            .replaceAll(
                "(?i)(api[_ -]?key|authorization|bearer)\\s*[:=]\\s*[^ ,}]+", "$1=[redacted]")
            .trim();
    return normalized.substring(0, Math.min(normalized.length(), 240));
  }

  private ObjectNode decompositionSchema() {
    return (ObjectNode)
        readSchema(
            """
{"type":"object","additionalProperties":false,"required":["dishTitle","servings","components","assemblyRequired"],"properties":{"dishTitle":{"type":"string","minLength":1},"servings":{"type":"integer","minimum":1,"maximum":30},"components":{"type":"array","minItems":1,"maxItems":6,"items":{"type":"object","additionalProperties":false,"required":["key","name","purpose","order","dependsOn"],"properties":{"key":{"type":"string","minLength":1},"name":{"type":"string","minLength":1},"purpose":{"type":"string","minLength":1},"order":{"type":"integer","minimum":1,"maximum":6},"dependsOn":{"type":"array","items":{"type":"string"}}}}},"assemblyRequired":{"type":"boolean"}}}
""");
  }

  private ObjectNode componentSchema() {
    return (ObjectNode)
        readSchema(
            """
{"type":"object","additionalProperties":false,"required":["componentKey","title","ingredients","instructions"],"properties":{"componentKey":{"type":"string","minLength":1},"title":{"type":"string","minLength":1},"ingredients":{"type":"array","minItems":1,"items":{"type":"object","additionalProperties":false,"required":["name","quantity","unit","preparation"],"properties":{"name":{"type":"string","minLength":1},"quantity":{"type":"number","exclusiveMinimum":0},"unit":{"type":"string","minLength":1},"preparation":{"type":"string"}}}},"instructions":{"type":"array","minItems":1,"items":{"type":"object","additionalProperties":false,"required":["order","instruction","completionCue","temperature","durationSeconds"],"properties":{"order":{"type":"integer","minimum":1},"instruction":{"type":"string","minLength":1},"completionCue":{"type":"string","minLength":1},"temperature":{"type":["integer","null"]},"durationSeconds":{"type":["integer","null"]}}}}}}
""");
  }

  private JsonNode readSchema(String schema) {
    try {
      return mapper.readTree(schema);
    } catch (IOException e) {
      throw new IllegalStateException("Invalid internal AI schema", e);
    }
  }

  private String safeJson(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (IOException e) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Recipe serialization failed");
    }
  }

  private String value(Object value, Object fallback) {
    return value == null ? String.valueOf(fallback) : String.valueOf(value);
  }
}
