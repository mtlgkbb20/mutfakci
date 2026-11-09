package com.kitchenai.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class User {

  @Id
  private UUID id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "is_admin", nullable = false)
  private boolean admin;

  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified = true;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected User() {
    // Required by JPA.
  }

  public User(String email, String passwordHash, String displayName) {
    this.id = UUID.randomUUID();
    this.email = email;
    this.passwordHash = passwordHash;
    this.displayName = displayName;
    this.emailVerified = false;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
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

  public boolean isAdmin() {
    return admin;
  }

  public boolean isEmailVerified() {
    return emailVerified;
  }
}
