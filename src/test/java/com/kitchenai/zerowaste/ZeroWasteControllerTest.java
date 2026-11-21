package com.kitchenai.zerowaste;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kitchenai.support.WebTestSupport;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class ZeroWasteControllerTest {
  private final ZeroWasteService service = mock(ZeroWasteService.class);
  private final MockMvc mvc = WebTestSupport.mvc(new ZeroWasteController(service));
  private final UUID user = UUID.randomUUID();
  private final UUID id = UUID.randomUUID();

  @BeforeEach void login() { WebTestSupport.authenticate(user); }
  @AfterEach void logout() { WebTestSupport.clearAuthentication(); }

  private ResultActions post(String path, String body) throws Exception {
    return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
        .contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  void confirmPassesTheAuthenticatedUserAndAllFields() throws Exception {
    post("/api/v1/zero-waste/remainders", "{\"ingredientName\":\"Yumurta akı\",\"quantity\":2,\"unit\":\"adet\","
        + "\"catalogRecipeId\":\"kek\",\"useByDate\":\"2026-11-25\",\"operationKey\":\"op-1\"}")
        .andExpect(status().isOk());
    verify(service).confirm(eq(user), eq("Yumurta akı"), argThat(q -> q.compareTo(new BigDecimal("2")) == 0), eq("adet"),
        eq("kek"), isNull(), isNull(), isNull(), eq(java.time.LocalDate.of(2026, 11, 25)), eq("op-1"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "{\"ingredientName\":\"\",\"quantity\":1,\"unit\":\"g\",\"operationKey\":\"k\"}",
    "{\"ingredientName\":\"x\",\"quantity\":0,\"unit\":\"g\",\"operationKey\":\"k\"}",
    "{\"ingredientName\":\"x\",\"quantity\":0.0001,\"unit\":\"g\",\"operationKey\":\"k\"}",
    "{\"ingredientName\":\"x\",\"quantity\":1,\"unit\":\"\",\"operationKey\":\"k\"}",
    "{\"ingredientName\":\"x\",\"quantity\":1,\"unit\":\"g\"}",
    "{\"ingredientName\":\"x\",\"quantity\":1,\"unit\":\"g\",\"operationKey\":\"k\",\"useByDate\":\"not-a-date\"}",
  })
  void invalidConfirmationsAre400(String body) throws Exception {
    post("/api/v1/zero-waste/remainders", body).andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void previewRequiresServingsBetween1And30() throws Exception {
    post("/api/v1/zero-waste/preview", "{\"catalogRecipeId\":\"kek\",\"servings\":31}").andExpect(status().isBadRequest());
    post("/api/v1/zero-waste/preview", "{\"catalogRecipeId\":\"kek\"}").andExpect(status().isBadRequest());
    post("/api/v1/zero-waste/preview", "{\"catalogRecipeId\":\"kek\",\"servings\":30}").andExpect(status().isOk());
    verify(service).preview("kek", null, 30, user);
  }

  @Test
  void lifecycleOperationsAreScopedToTheCaller() throws Exception {
    post("/api/v1/zero-waste/remainders/" + id + "/consume", "{\"quantity\":1,\"operationKey\":\"c\"}").andExpect(status().isOk());
    post("/api/v1/zero-waste/remainders/" + id + "/transfer-to-pantry", "{\"operationKey\":\"t\"}").andExpect(status().isOk());
    post("/api/v1/zero-waste/remainders/" + id + "/dismiss", "{\"operationKey\":\"d\"}").andExpect(status().isOk());
    mvc.perform(patch("/api/v1/zero-waste/remainders/" + id).contentType(MediaType.APPLICATION_JSON)
        .content("{\"quantity\":3,\"unit\":\"g\",\"operationKey\":\"p\"}")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/zero-waste/remainders/" + id)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/zero-waste/remainders")).andExpect(status().isOk());

    verify(service).consume(eq(user), eq(id), any(), eq("c"));
    verify(service).transfer(user, id, "t");
    verify(service).dismiss(user, id, "d");
    verify(service).correct(eq(user), eq(id), any(), eq("g"), isNull(), eq("p"));
    verify(service).get(user, id);
    verify(service).list(user);
  }

  @Test
  void operationsWithoutAnIdempotencyKeyAre400() throws Exception {
    post("/api/v1/zero-waste/remainders/" + id + "/transfer-to-pantry", "{}").andExpect(status().isBadRequest());
    post("/api/v1/zero-waste/remainders/" + id + "/consume", "{\"quantity\":1}").andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void suggestionsRejectOutOfRangeServings() throws Exception {
    when(service.suggestions(user, id, 4)).thenReturn(List.of());
    mvc.perform(get("/api/v1/zero-waste/remainders/" + id + "/suggestions").param("servings", "4")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/zero-waste/remainders/" + id + "/suggestions").param("servings", "0")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/zero-waste/remainders/" + id + "/suggestions").param("servings", "31")).andExpect(status().isBadRequest());
    verify(service, times(1)).suggestions(any(), any(), any());
  }
}
