package com.kitchenai.recipe;

import com.kitchenai.common.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recipe-history")
public class RecipeHistoryController {
  private final RecipeRepository recipes;

  public RecipeHistoryController(RecipeRepository recipes) {
    this.recipes = recipes;
  }

  public record RecipeHistoryItem(UUID id, String title, Instant createdAt) {}
  public record PageResponse<T>(List<T> content, int page, int size, long totalElements,
      int totalPages, boolean hasNext) {
    static <T> PageResponse<T> from(Page<T> page) {
      return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
          page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }
  }

  @GetMapping
  public PageResponse<RecipeHistoryItem> list(
      @AuthenticationPrincipal UUID userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to) {
    if (page < 0 || size < 1 || size > 50) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid pagination parameters");
    }
    if ((from == null) != (to == null) || (from != null && from.isAfter(to))) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "A valid from/to date range is required");
    }
    Page<Recipe> result = from == null
        ? recipes.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
        : recipes.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            userId, from, to, PageRequest.of(page, size));
    return PageResponse.from(result.map(recipe ->
        new RecipeHistoryItem(recipe.getId(), recipe.getTitle(), recipe.getCreatedAt())));
  }
}
