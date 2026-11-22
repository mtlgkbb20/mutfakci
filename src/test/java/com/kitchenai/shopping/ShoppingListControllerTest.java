package com.kitchenai.shopping;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kitchenai.shopping.ShoppingListContracts.*;
import com.kitchenai.support.WebTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class ShoppingListControllerTest {
  private final ShoppingListService service = mock(ShoppingListService.class);
  private final MockMvc mvc = WebTestSupport.mvc(new ShoppingListController(service));
  private final UUID user = UUID.randomUUID();
  private final UUID list = UUID.randomUUID();
  private final String base = "/api/v1/shopping-lists";

  @BeforeEach void login() { WebTestSupport.authenticate(user); }
  @AfterEach void logout() { WebTestSupport.clearAuthentication(); }

  @Test
  void createFromRecipesValidatesEachSelection() throws Exception {
    mvc.perform(post(base).contentType(MediaType.APPLICATION_JSON)
            .content("{\"recipes\":[{\"recipeCatalogId\":\"kek\",\"servings\":31}]}"))
        .andExpect(status().isBadRequest());
    mvc.perform(post(base).contentType(MediaType.APPLICATION_JSON)
            .content("{\"recipes\":[{\"recipeCatalogId\":\"\",\"servings\":2}]}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);

    mvc.perform(post(base).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"Haftalık\",\"recipes\":[{\"recipeCatalogId\":\"kek\",\"servings\":2}],\"personalized\":false}"))
        .andExpect(status().isOk());
    verify(service).create(argThat(r -> r.recipes().size() == 1 && !r.personalized()), eq(user));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "{\"name\":\"\",\"quantity\":1,\"unit\":\"g\"}",
    "{\"name\":\"Un\",\"quantity\":0,\"unit\":\"g\"}",
    "{\"name\":\"Un\",\"unit\":\"g\"}",
    "{\"name\":\"Un\",\"quantity\":1,\"unit\":\"\"}",
  })
  void invalidItemsAre400(String body) throws Exception {
    mvc.perform(post(base + "/" + list + "/items").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void itemUpdateRejectsNegativePurchasedQuantity() throws Exception {
    mvc.perform(patch(base + "/" + list + "/items/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"purchasedQuantity\":-1}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void everyOperationIsScopedToTheAuthenticatedUser() throws Exception {
    UUID item = UUID.randomUUID();
    mvc.perform(get(base)).andExpect(status().isOk());
    mvc.perform(get(base + "/" + list)).andExpect(status().isOk());
    mvc.perform(patch(base + "/" + list).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Yeni\"}")).andExpect(status().isOk());
    mvc.perform(delete(base + "/" + list + "/items/" + item)).andExpect(status().isOk());
    mvc.perform(post(base + "/" + list + "/complete").contentType(MediaType.APPLICATION_JSON).content("{\"operationKey\":\"k\"}")).andExpect(status().isOk());

    verify(service).list(user);
    verify(service).get(list, user);
    verify(service).rename(eq(list), any(UpdateListRequest.class), eq(user));
    verify(service).removeItem(list, item, user);
    verify(service).complete(eq(list), argThat(r -> r.operationKey().equals("k")), eq(user));
  }

  @Test
  void completionRequiresAnIdempotencyKey() throws Exception {
    mvc.perform(post(base + "/" + list + "/complete").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }
}
