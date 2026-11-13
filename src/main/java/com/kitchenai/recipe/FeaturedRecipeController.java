package com.kitchenai.recipe;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.kitchenai.common.ApiException;

@RestController
@RequestMapping("/api/v1/featured-recipes")
public class FeaturedRecipeController {
  private final RecipeCatalogService catalog;

  public FeaturedRecipeController(RecipeCatalogService catalog) {
    this.catalog = catalog;
  }

  @GetMapping
  public List<RecipeCatalogService.CatalogRecipe> list() {
    return catalog.findAll();
  }

  @GetMapping("/{id}")
  public RecipeCatalogService.CatalogRecipe detail(@PathVariable String id) {
    return catalog.findById(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Featured recipe not found"));
  }
}
