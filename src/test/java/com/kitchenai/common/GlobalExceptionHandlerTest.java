package com.kitchenai.common;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kitchenai.support.WebTestSupport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

  record Payload(@NotBlank String name, @Positive int servings) {}

  @RestController
  static class Probe {
    @GetMapping("/api-exception") void apiException() { throw new ApiException(HttpStatus.NOT_FOUND, "Recipe not found"); }
    @GetMapping("/forbidden") void forbidden() { throw new ResponseStatusException(HttpStatus.FORBIDDEN); }
    @GetMapping("/conflict") void conflict() { throw new ResponseStatusException(HttpStatus.CONFLICT, "Submission window is closed"); }
    @GetMapping("/upstream") void upstream() { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "secret upstream detail"); }
    @GetMapping("/boom") void boom() { throw new IllegalStateException("db password=hunter2"); }
    @GetMapping("/param") String param(@RequestParam int page) { return "ok"; }
    @PostMapping("/body") String body(@Valid @RequestBody Payload payload) { return "ok"; }
  }

  private final MockMvc mvc = WebTestSupport.mvc(new Probe());

  @Test
  void apiExceptionKeepsStatusAndMessage() throws Exception {
    mvc.perform(get("/api-exception"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Recipe not found"))
        .andExpect(jsonPath("$.errorId", not(emptyString())))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  void responseStatusExceptionKeepsItsStatusInsteadOfBecoming500() throws Exception {
    mvc.perform(get("/forbidden")).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("Forbidden"));
    mvc.perform(get("/conflict")).andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("Submission window is closed"));
  }

  @Test
  void serverSideResponseStatusExceptionIsMasked() throws Exception {
    mvc.perform(get("/upstream"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error").value(GlobalExceptionHandler.UNEXPECTED))
        .andExpect(content().string(not(containsString("secret upstream detail"))));
  }

  @Test
  void unexpectedExceptionIsMaskedAs500WithoutLeakingDetails() throws Exception {
    mvc.perform(get("/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error").value(GlobalExceptionHandler.UNEXPECTED))
        .andExpect(content().string(not(containsString("hunter2"))));
  }

  @Test
  void beanValidationFailureIs400WithFieldNames() throws Exception {
    mvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"servings\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(GlobalExceptionHandler.INVALID_REQUEST))
        .andExpect(jsonPath("$.fields", contains("name", "servings")));
  }

  @Test
  void malformedJsonIs400() throws Exception {
    mvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{not json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(GlobalExceptionHandler.INVALID_REQUEST));
  }

  @Test
  void missingOrMistypedParameterIs400() throws Exception {
    mvc.perform(get("/param")).andExpect(status().isBadRequest());
    mvc.perform(get("/param").param("page", "abc")).andExpect(status().isBadRequest());
  }

  @Test
  void wrongMethodIs405() throws Exception {
    mvc.perform(delete("/param")).andExpect(status().isMethodNotAllowed());
  }

  @Test
  void everyErrorGetsAUniqueId() throws Exception {
    String a = mvc.perform(get("/boom")).andReturn().getResponse().getContentAsString();
    String b = mvc.perform(get("/boom")).andReturn().getResponse().getContentAsString();
    String idA = com.jayway.jsonpath.JsonPath.read(a, "$.errorId");
    String idB = com.jayway.jsonpath.JsonPath.read(b, "$.errorId");
    org.junit.jupiter.api.Assertions.assertNotEquals(idA, idB);
  }
}
