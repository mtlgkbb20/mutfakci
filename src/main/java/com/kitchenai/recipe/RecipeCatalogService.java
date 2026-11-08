package com.kitchenai.recipe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class RecipeCatalogService {
    public record Ingredient(String name, double quantity, String unit, String preparation) {
    }

    public record Step(int order, String title, String instruction, String completionCue,
                       Integer temperature, Integer durationSeconds) {
    }

    public record CatalogRecipe(int schemaVersion, String id, String title, List<String> aliases,
                                String locale, String category, List<String> searchTerms,
                                int defaultServings, int estimatedTotalMinutes,
                                List<Ingredient> ingredients, List<Step> steps,
                                String description) {
    }

    private final List<CatalogRecipe> recipes;

    public RecipeCatalogService(ObjectMapper mapper) {
        try {
            recipes = List.of(mapper.readValue(
                    new ClassPathResource("recipe-catalog/recipes.json").getInputStream(),
                    CatalogRecipe[].class));
        } catch (IOException e) {
            throw new IllegalStateException("Recipe catalog could not be loaded", e);
        }
    }

    public Optional<CatalogRecipe> find(String request) {
        String query = RecipeText.normalize(request);
        if (query.isBlank()) {
            return Optional.empty();
        }

        for (CatalogRecipe recipe : recipes) {
            boolean exactTitle = RecipeText.normalize(recipe.title()).equals(query);
            boolean exactAlias = recipe.aliases().stream()
                    .anyMatch(alias -> RecipeText.normalize(alias).equals(query));
            if (exactTitle || exactAlias) {
                return Optional.of(recipe);
            }
        }

        Set<String> words = Set.of(query.split(" "));
        List<Candidate> candidates = new ArrayList<>();
        for (CatalogRecipe recipe : recipes) {
            Set<String> terms = new HashSet<>();
            terms.addAll(Arrays.asList(RecipeText.normalize(recipe.title()).split(" ")));
            recipe.aliases().forEach(alias ->
                    terms.addAll(Arrays.asList(RecipeText.normalize(alias).split(" "))));
            recipe.searchTerms().forEach(term -> terms.add(RecipeText.normalize(term)));

            long matchingWords = words.stream().filter(terms::contains).count();
            double score = (double) matchingWords / Math.max(1, words.size());
            if (score >= 0.75) {
                candidates.add(new Candidate(recipe, score));
            }
        }

        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
        boolean singleMatch = candidates.size() == 1;
        boolean clearWinner = candidates.size() > 1
                && candidates.get(0).score() > candidates.get(1).score();
        return singleMatch || clearWinner
                ? Optional.of(candidates.get(0).recipe())
                : Optional.empty();
    }

    private record Candidate(CatalogRecipe recipe, double score) {
    }
}
