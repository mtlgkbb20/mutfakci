package com.kitchenai.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class User {
  @Id private UUID id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(nullable = false)
  private String displayName;

  @Column(name = "is_admin", nullable = false)\n  private boolean admin;\n\n  private Instant createdAt;
  private Instant updatedAt;

  public User() {}

  public User(String email, String hash, String name) {
    id = UUID.randomUUID();
    this.email = email;
    passwordHash = hash;
    displayName = name;
    createdAt = Instant.now();
    updatedAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getDisplayName() {
    return displayName;
  }
}
