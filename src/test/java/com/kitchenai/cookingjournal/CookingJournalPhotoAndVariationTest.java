package com.kitchenai.cookingjournal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitchenai.common.ApiException;
import com.kitchenai.cooking.CookingSessionRepository;
import com.kitchenai.recipe.RecipeRepository;
import com.kitchenai.support.WebTestSupport;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP-level and photo/variation rules not covered by CookingJournalServiceTest. */
class CookingJournalPhotoAndVariationTest {
  private final UUID user = UUID.randomUUID();
  private final CookingJournalRepository journal = mock(CookingJournalRepository.class);
  private final CookingJournalMediaRepository mediaRepo = mock(CookingJournalMediaRepository.class);
  private final RecipeVariationRepository variations = mock(RecipeVariationRepository.class);
  private final CookingJournalService realService = new CookingJournalService(journal, mock(CookingSessionRepository.class),
      mock(RecipeRepository.class), mediaRepo, variations, new ObjectMapper());
  private final CookingJournalService service = mock(CookingJournalService.class);
  private final JournalMediaStorage storage = mock(JournalMediaStorage.class);
  private final MockMvc mvc = WebTestSupport.mvc(new CookingJournalController(service, storage));
  private final UUID entryId = UUID.randomUUID();

  @BeforeEach void login() { WebTestSupport.authenticate(user); }
  @AfterEach void logout() { WebTestSupport.clearAuthentication(); }

  private CookingJournalEntry entry(UUID recipeId) {
    String snapshot = "{\"title\":\"Pilav\",\"description\":\"d\",\"productType\":\"MAIN\",\"servings\":2,"
        + "\"preparationTime\":10,\"cookingTime\":20,\"ingredients\":\"[]\",\"stages\":\"[]\"}";
    CookingJournalEntry e = new CookingJournalEntry(user, UUID.randomUUID(), recipeId, "Pilav", snapshot, 2, 4, null, null);
    when(journal.findByIdAndUserId(e.getId(), user)).thenReturn(Optional.of(e));
    return e;
  }

  // ---- HTTP validation ----
  @ParameterizedTest
  @ValueSource(strings = {"{\"rating\":6}", "{\"rating\":0}", "{\"servings\":31}"})
  void updateRejectsOutOfRangeRatingAndServings(String body) throws Exception {
    mvc.perform(patch("/api/v1/cooking-journal/" + entryId).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void createRequiresACookingSession() throws Exception {
    mvc.perform(post("/api/v1/cooking-journal").contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}"))
        .andExpect(status().isBadRequest());
  }

  // ---- photo lifecycle ----
  private final MockMultipartFile image = new MockMultipartFile("image", "p.jpg", "image/jpeg", new byte[] {1});

  @Test
  void replacingAPhotoDeletesThePreviousFile() throws Exception {
    when(storage.store(eq(user), any())).thenReturn(new JournalMediaStorage.StoredPhoto("new.jpg", "image/jpeg"));
    when(service.savePhoto(eq(user), eq(entryId), any())).thenReturn("old.jpg");
    CookingJournalEntry existingEntry = entry(UUID.randomUUID());
    when(service.get(user, entryId)).thenReturn(existingEntry);
    mvc.perform(multipart(HttpMethod.PUT, "/api/v1/cooking-journal/" + entryId + "/photo").file(image))
        .andExpect(status().isOk()).andExpect(jsonPath("$.hasPhoto").value(true));
    verify(storage).delete(user, "old.jpg");
    verify(storage, never()).delete(user, "new.jpg");
  }

  @Test
  void failedPhotoSaveRemovesTheJustStoredFile() throws Exception {
    when(storage.store(eq(user), any())).thenReturn(new JournalMediaStorage.StoredPhoto("orphan.jpg", "image/jpeg"));
    when(service.savePhoto(eq(user), eq(entryId), any())).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Journal entry not found"));
    mvc.perform(multipart(HttpMethod.PUT, "/api/v1/cooking-journal/" + entryId + "/photo").file(image))
        .andExpect(status().isNotFound());
    verify(storage).delete(user, "orphan.jpg");
  }

  @Test
  void deletingAPhotoRemovesTheFileOnlyWhenOneExisted() throws Exception {
    when(service.deletePhoto(user, entryId)).thenReturn("a.jpg", (String) null);
    mvc.perform(delete("/api/v1/cooking-journal/" + entryId + "/photo")).andExpect(status().isNoContent());
    mvc.perform(delete("/api/v1/cooking-journal/" + entryId + "/photo")).andExpect(status().isNoContent());
    verify(storage, times(1)).delete(any(), any());
  }

  @Test
  void photoIsServedWithStoredContentTypeAndNoStore() throws Exception {
    CookingJournalMedia ref = new CookingJournalMedia(entryId, user, "a.png", "image/png");
    when(service.getPhoto(user, entryId)).thenReturn(ref);
    when(storage.load(user, "a.png")).thenReturn(new JournalMediaStorage.Photo(new byte[] {9}, "image/png"));
    mvc.perform(get("/api/v1/cooking-journal/" + entryId + "/photo"))
        .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  // ---- service: photo and variation rules ----
  @Test
  void savePhotoReturnsThePreviousKeyWhenReplacing() {
    CookingJournalEntry e = entry(null);
    CookingJournalMedia existing = new CookingJournalMedia(e.getId(), user, "old.jpg", "image/jpeg");
    when(mediaRepo.findByUserIdAndJournalEntryId(user, e.getId())).thenReturn(Optional.of(existing), Optional.empty());
    assertEquals("old.jpg", realService.savePhoto(user, e.getId(), new JournalMediaStorage.StoredPhoto("new.jpg", "image/jpeg")));
    assertEquals("new.jpg", existing.getObjectKey());
    assertNull(realService.savePhoto(user, e.getId(), new JournalMediaStorage.StoredPhoto("first.jpg", "image/jpeg")));
  }

  @Test
  void photoOperationsOnAnotherUsersEntryAre404() {
    UUID foreign = UUID.randomUUID();
    when(journal.findByIdAndUserId(foreign, user)).thenReturn(Optional.empty());
    assertEquals(404, assertThrows(ApiException.class,
        () -> realService.savePhoto(user, foreign, new JournalMediaStorage.StoredPhoto("k", "image/jpeg"))).status.value());
    assertEquals(404, assertThrows(ApiException.class, () -> realService.deletePhoto(user, foreign)).status.value());
    verifyNoInteractions(mediaRepo);
  }

  @Test
  void variationNeedsARecipeBasedEntry() {
    CookingJournalEntry e = entry(null);
    assertEquals(409, assertThrows(ApiException.class, () -> realService.createVariation(user, e.getId(),
        new CookingJournalService.VariationRequest("V", null, null, null, null))).status.value());
  }

  @Test
  void variationValidatesTitleDescriptionAndServings() {
    CookingJournalEntry e = entry(UUID.randomUUID());
    for (var bad : java.util.List.of(
        new CookingJournalService.VariationRequest("  ", null, null, null, null),
        new CookingJournalService.VariationRequest("x".repeat(256), null, null, null, null),
        new CookingJournalService.VariationRequest("V", "d".repeat(2001), null, null, null),
        new CookingJournalService.VariationRequest("V", null, 0, null, null),
        new CookingJournalService.VariationRequest("V", null, 31, null, null),
        new CookingJournalService.VariationRequest("V", null, null, "{\"not\":\"array\"}", null)))
      assertEquals(400, assertThrows(ApiException.class, () -> realService.createVariation(user, e.getId(), bad)).status.value());
    verify(variations, never()).save(any());
  }

  @Test
  void variationInheritsUnchangedFieldsFromTheSnapshot() {
    when(variations.save(any(RecipeVariation.class))).thenAnswer(inv -> inv.getArgument(0));
    CookingJournalEntry e = entry(UUID.randomUUID());
    RecipeVariation v = realService.createVariation(user, e.getId(), new CookingJournalService.VariationRequest(" Acılı ", null, null, null, null));
    assertEquals("Acılı", v.getTitle());
    assertEquals("d", v.getDescription());
    assertEquals(2, v.getServings());
    assertEquals("MAIN", v.getProductType());
  }

  @Test
  void journalListClampsPageSize() {
    realService.list(user, -4, 500);
    verify(journal).findByUserIdOrderByCookedAtDesc(eq(user), argThat(p -> p.getPageNumber() == 0 && p.getPageSize() == 50));
  }
}
