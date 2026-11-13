package com.kitchenai.cookingjournal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/cooking-journal")
public class CookingJournalController {
  private final CookingJournalService service;
  private final JournalMediaStorage media;

  public CookingJournalController(CookingJournalService service, JournalMediaStorage media) {
    this.service = service;
    this.media = media;
  }

  public record CreateRequest(
      @NotNull UUID cookingSessionId,
      @Min(1) @Max(30) Integer servings,
      @Min(1) @Max(5) Integer rating,
      String notes) {}

  public record UpdateRequest(
      @Min(1) @Max(30) Integer servings,
      @Min(1) @Max(5) Integer rating,
      String notes) {}

  public record VariationRequest(@NotBlank String title, String description,
      @Min(1) @Max(30) Integer servings, String ingredientsJson, String stagesJson) {}

  public record EntryView(UUID id, UUID cookingSessionId, UUID recipeId, String recipeSnapshot,
      Integer servings, Integer rating, String notes, Instant cookedAt, Instant createdAt,
      Instant updatedAt, boolean hasPhoto) {
    static EntryView from(CookingJournalEntry entry, boolean hasPhoto) {
      return new EntryView(entry.getId(), entry.getCookingSessionId(), entry.getRecipeId(),
          entry.getRecipeSnapshotJson(), entry.getServings(), entry.getRating(), entry.getNotes(),
          entry.getCookedAt(), entry.getCreatedAt(), entry.getUpdatedAt(), hasPhoto);
    }
  }

  public record VariationView(UUID id, UUID journalEntryId, UUID sourceRecipeId, String title,
      String description, String productType, int servings, int preparationTime, int cookingTime,
      String ingredientsJson, String stagesJson, Instant createdAt) {
    static VariationView from(RecipeVariation variation) {
      return new VariationView(variation.getId(), variation.getJournalEntryId(),
          variation.getSourceRecipeId(), variation.getTitle(), variation.getDescription(),
          variation.getProductType(), variation.getServings(), variation.getPreparationTime(),
          variation.getCookingTime(), variation.getIngredientsJson(), variation.getStagesJson(),
          variation.getCreatedAt());
    }
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public EntryView create(@Valid @RequestBody CreateRequest request,
      @AuthenticationPrincipal UUID userId) {
    CookingJournalEntry entry = service.create(userId, new CookingJournalService.CreateRequest(
        request.cookingSessionId(), request.servings(), request.rating(), request.notes()));
    return EntryView.from(entry, false);
  }

  @GetMapping
  public List<EntryView> list(@AuthenticationPrincipal UUID userId) {
    return service.list(userId).stream()
        .map(entry -> EntryView.from(entry, service.hasPhoto(userId, entry.getId()))).toList();
  }

  @GetMapping("/{entryId}")
  public EntryView get(@PathVariable UUID entryId, @AuthenticationPrincipal UUID userId) {
    return EntryView.from(service.get(userId, entryId), service.hasPhoto(userId, entryId));
  }

  @GetMapping("/recipes/{recipeId}/attempts")
  public List<EntryView> attempts(@PathVariable UUID recipeId, @AuthenticationPrincipal UUID userId) {
    return service.attempts(userId, recipeId).stream()
        .map(entry -> EntryView.from(entry, service.hasPhoto(userId, entry.getId()))).toList();
  }

  @PatchMapping("/{entryId}")
  public EntryView update(@PathVariable UUID entryId, @Valid @RequestBody UpdateRequest request,
      @AuthenticationPrincipal UUID userId) {
    CookingJournalEntry entry = service.update(userId, entryId,
        new CookingJournalService.UpdateRequest(request.servings(), request.rating(), request.notes()));
    return EntryView.from(entry, service.hasPhoto(userId, entryId));
  }

  @PutMapping(value = "/{entryId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public EntryView uploadPhoto(@PathVariable UUID entryId, @AuthenticationPrincipal UUID userId,
      @RequestPart("image") MultipartFile image) {
    JournalMediaStorage.StoredPhoto stored = media.store(userId, image);
    try {
      String previous = service.savePhoto(userId, entryId, stored);
      if (previous != null) media.delete(userId, previous);
      return EntryView.from(service.get(userId, entryId), true);
    } catch (RuntimeException exception) {
      media.delete(userId, stored.objectKey());
      throw exception;
    }
  }

  @DeleteMapping("/{entryId}/photo")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deletePhoto(@PathVariable UUID entryId, @AuthenticationPrincipal UUID userId) {
    String key = service.deletePhoto(userId, entryId);
    if (key != null) media.delete(userId, key);
  }

  @GetMapping("/{entryId}/photo")
  public ResponseEntity<ByteArrayResource> photo(@PathVariable UUID entryId,
      @AuthenticationPrincipal UUID userId) {
    CookingJournalMedia reference = service.getPhoto(userId, entryId);
    JournalMediaStorage.Photo photo = media.load(userId, reference.getObjectKey());
    MediaType contentType = reference.getContentType().equals("image/png")
        ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
    return ResponseEntity.ok().contentType(contentType).cacheControl(CacheControl.noStore())
        .body(new ByteArrayResource(photo.bytes()));
  }

  @PostMapping("/{entryId}/variations")
  @ResponseStatus(HttpStatus.CREATED)
  public VariationView createVariation(@PathVariable UUID entryId,
      @Valid @RequestBody VariationRequest request, @AuthenticationPrincipal UUID userId) {
    return VariationView.from(service.createVariation(userId, entryId,
        new CookingJournalService.VariationRequest(request.title(), request.description(),
            request.servings(), request.ingredientsJson(), request.stagesJson())));
  }

  @GetMapping("/variations")
  public List<VariationView> listVariations(@AuthenticationPrincipal UUID userId) {
    return service.listVariations(userId).stream().map(VariationView::from).toList();
  }

  @GetMapping("/variations/{variationId}")
  public VariationView getVariation(@PathVariable UUID variationId,
      @AuthenticationPrincipal UUID userId) {
    return VariationView.from(service.getVariation(userId, variationId));
  }
}
