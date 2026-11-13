package com.kitchenai.recipe;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class RecipeCatalogService {
  public record Ingredient(String name, double quantity, String unit, String preparation) {}

  public record Step(
      int order,
      String title,
      String instruction,
      String completionCue,
      Integer temperature,
      Integer durationSeconds) {}

  public record CatalogRecipe(
      int schemaVersion,
      String id,
      String title,
      List<String> aliases,
      String locale,
      String category,
      List<String> searchTerms,
      int defaultServings,
      int estimatedTotalMinutes,
      List<Ingredient> ingredients,
      List<Step> steps,
      String description,
      String sourceName,
      String sourceAuthor,
      String sourceUrl,
      String sourcePublishedAt,
      String sourceRightsStatus) {}

  private final List<CatalogRecipe> recipes;

  public RecipeCatalogService(ObjectMapper mapper) {
    try {
      recipes =
          List.of(
              mapper.readValue(
                  new ClassPathResource("recipe-catalog/recipes.json").getInputStream(),
                  CatalogRecipe[].class));
      validateCatalog(recipes);
    } catch (IOException e) {
      throw new IllegalStateException("Recipe catalog could not be loaded", e);
    }
  }

  private void validateCatalog(List<CatalogRecipe> catalog) {
    Set<String> ids = new HashSet<>();
    for (CatalogRecipe recipe : catalog) {
      if (recipe.id() == null || recipe.id().isBlank() || !ids.add(recipe.id())) {
        throw new IllegalStateException("Recipe catalog contains a missing or duplicate id");
      }
      if (recipe.sourceName() == null || recipe.sourceName().isBlank()
          || recipe.sourceAuthor() == null || recipe.sourceAuthor().isBlank()
          || recipe.sourceUrl() == null || recipe.sourceUrl().isBlank()
          || recipe.sourceRightsStatus() == null || recipe.sourceRightsStatus().isBlank()) {
        throw new IllegalStateException("Every featured recipe must have source attribution: " + recipe.id());
      }
      try {
        URI uri = URI.create(recipe.sourceUrl());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
          throw new IllegalStateException("Recipe source URL must be HTTPS: " + recipe.id());
        }
      } catch (IllegalArgumentException exception) {
        throw new IllegalStateException("Recipe source URL is invalid: " + recipe.id(), exception);
      }
      if (recipe.ingredients() == null || recipe.ingredients().isEmpty()
          || recipe.steps() == null || recipe.steps().isEmpty()) {
        throw new IllegalStateException("Featured recipe must include sourced ingredients and steps: " + recipe.id());
      }
    }
  }

  public List<CatalogRecipe> findAll() {
    return recipes;
  }

  public Optional<CatalogRecipe> findById(String id) {
    return recipes.stream().filter(recipe -> recipe.id().equals(id)).findFirst();
  }

  public Optional<CatalogRecipe> find(String request) {
    String query = RecipeText.normalize(request);
    if (query.isBlank()) {
      return Optional.empty();
    }

    for (CatalogRecipe recipe : recipes) {
      boolean exactTitle = RecipeText.normalize(recipe.title()).equals(query);
      boolean exactAlias =
          recipe.aliases().stream().anyMatch(alias -> RecipeText.normalize(alias).equals(query));
      if (exactTitle || exactAlias) {
        return Optional.of(recipe);
      }
    }

    Set<String> words = Set.of(query.split(" "));
    List<Candidate> candidates = new ArrayList<>();
    for (CatalogRecipe recipe : recipes) {
      Set<String> terms = new HashSet<>();
      terms.addAll(Arrays.asList(RecipeText.normalize(recipe.title()).split(" ")));
      recipe
          .aliases()
          .forEach(alias -> terms.addAll(Arrays.asList(RecipeText.normalize(alias).split(" "))));
      recipe.searchTerms().forEach(term -> terms.add(RecipeText.normalize(term)));

      long matchingWords = words.stream().filter(terms::contains).count();
      double score = (double) matchingWords / Math.max(1, words.size());
      if (score >= 0.75) {
        candidates.add(new Candidate(recipe, score));
      }
    }

    candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
    boolean singleMatch = candidates.size() == 1;
    boolean clearWinner =
        candidates.size() > 1 && candidates.get(0).score() > candidates.get(1).score();
    return singleMatch || clearWinner ? Optional.of(candidates.get(0).recipe()) : Optional.empty();
  }

  private record Candidate(CatalogRecipe recipe, double score) {}
}
