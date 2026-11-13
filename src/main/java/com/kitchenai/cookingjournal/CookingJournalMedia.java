package com.kitchenai.cookingjournal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cooking_journal_media")
public class CookingJournalMedia {
  @Id private UUID id;
  private UUID journalEntryId;
  private UUID userId;
  private String objectKey;
  private String contentType;
  private Instant uploadedAt;

  protected CookingJournalMedia() {}

  public CookingJournalMedia(UUID journalEntryId, UUID userId, String objectKey, String contentType) {
    this.id = UUID.randomUUID();
    this.journalEntryId = journalEntryId;
    this.userId = userId;
    this.objectKey = objectKey;
    this.contentType = contentType;
    this.uploadedAt = Instant.now();
  }

  public void replace(String objectKey, String contentType) {
    this.objectKey = objectKey;
    this.contentType = contentType;
    this.uploadedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getJournalEntryId() { return journalEntryId; }
  public UUID getUserId() { return userId; }
  public String getObjectKey() { return objectKey; }
  public String getContentType() { return contentType; }
  public Instant getUploadedAt() { return uploadedAt; }
}
