package com.kitchenai.recipe;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kitchenai.support.WebTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

class RecipeHistoryControllerTest {
  private final RecipeRepository recipes = mock(RecipeRepository.class);
  private final MockMvc mvc = WebTestSupport.mvc(new RecipeHistoryController(recipes));
  private final UUID user = UUID.randomUUID();

  @BeforeEach void login() { WebTestSupport.authenticate(user); }
  @AfterEach void logout() { WebTestSupport.clearAuthentication(); }

  @Test
  void defaultPageIsTheFirst20OfTheCallersRecipes() throws Exception {
    Recipe recipe = new Recipe(user, null, "AI_GENERATED", "Kek", "d", "CAKE", 4, 10, 20, "[]", "[]");
    when(recipes.findByUserIdOrderByCreatedAtDesc(eq(user), eq(PageRequest.of(0, 20))))
        .thenReturn(new PageImpl<>(List.of(recipe), PageRequest.of(0, 20), 41));

    mvc.perform(get("/api/v1/recipe-history"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].title").value("Kek"))
        .andExpect(jsonPath("$.content[0].ingredients").doesNotExist())
        .andExpect(jsonPath("$.totalElements").value(41))
        .andExpect(jsonPath("$.totalPages").value(3))
        .andExpect(jsonPath("$.hasNext").value(true));
  }

  @Test
  void dateRangeUsesTheRangeQuery() throws Exception {
    Instant from = Instant.parse("2026-11-01T00:00:00Z"), to = Instant.parse("2026-11-30T00:00:00Z");
    when(recipes.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(user, from, to, PageRequest.of(1, 10)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 10), 10));
    mvc.perform(get("/api/v1/recipe-history").param("page", "1").param("size", "10")
            .param("from", from.toString()).param("to", to.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasNext").value(false));
    verify(recipes, never()).findByUserIdOrderByCreatedAtDesc(any(), any());
  }

  @ParameterizedTest
  @CsvSource({"-1,20", "0,0", "0,51"})
  void invalidPaginationIs400(int page, int size) throws Exception {
    mvc.perform(get("/api/v1/recipe-history").param("page", "" + page).param("size", "" + size))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Invalid pagination parameters"));
    verifyNoInteractions(recipes);
  }

  @Test
  void boundaryPageSizesAreAccepted() throws Exception {
    when(recipes.findByUserIdOrderByCreatedAtDesc(eq(user), any())).thenReturn(new PageImpl<>(List.of()));
    mvc.perform(get("/api/v1/recipe-history").param("size", "1")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/recipe-history").param("size", "50")).andExpect(status().isOk());
  }

  @Test
  void halfOpenOrInvertedRangesAre400() throws Exception {
    mvc.perform(get("/api/v1/recipe-history").param("from", "2026-11-01T00:00:00Z")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/recipe-history").param("from", "2026-11-30T00:00:00Z").param("to", "2026-11-01T00:00:00Z"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(recipes);
  }

  @Test
  void malformedDateIs400NotA500() throws Exception {
    mvc.perform(get("/api/v1/recipe-history").param("from", "yesterday").param("to", "today"))
        .andExpect(status().isBadRequest());
  }
}
