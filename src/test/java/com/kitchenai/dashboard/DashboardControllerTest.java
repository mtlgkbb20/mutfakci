package com.kitchenai.dashboard;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kitchenai.cooking.CookingSessionRepository;
import com.kitchenai.recipe.RecipeRepository;
import com.kitchenai.support.WebTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;

class DashboardControllerTest {
  private final RecipeRepository recipes = mock(RecipeRepository.class);
  private final CookingSessionRepository sessions = mock(CookingSessionRepository.class);
  private final MockMvc mvc = WebTestSupport.mvc(new DashboardController(recipes, sessions));
  private final UUID user = UUID.randomUUID();

  @BeforeEach void login() { WebTestSupport.authenticate(user); }
  @AfterEach void logout() { WebTestSupport.clearAuthentication(); }

  @Test
  void dashboardOnlyQueriesTheAuthenticatedUsersData() throws Exception {
    when(recipes.findTop10ByUserIdOrderByCreatedAtDesc(user)).thenReturn(List.of());
    when(sessions.findTop10ByUserIdOrderByStartedAtDesc(user)).thenReturn(List.of());
    mvc.perform(get("/api/v1/dashboard"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recentRecipes").isArray())
        .andExpect(jsonPath("$.recentSessions").isArray());
    verify(recipes).findTop10ByUserIdOrderByCreatedAtDesc(user);
    verify(sessions).findTop10ByUserIdOrderByStartedAtDesc(user);
    verifyNoMoreInteractions(recipes, sessions);
  }

  @Test
  void recentSessionsAreScopedToTheAuthenticatedUser() throws Exception {
    when(sessions.findTop10ByUserIdOrderByStartedAtDesc(user)).thenReturn(List.of());
    mvc.perform(get("/api/v1/dashboard/recent-sessions")).andExpect(status().isOk());
    verify(sessions).findTop10ByUserIdOrderByStartedAtDesc(user);
  }
}
