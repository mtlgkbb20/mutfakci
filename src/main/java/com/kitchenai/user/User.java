package com.kitchenai.user;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name = "app_user")
public class User {
 @Id private UUID id;
 @Column(nullable=false, unique=true) private String email;
 @Column(name="password_hash", nullable=false) private String passwordHash;
 @Column(nullable=false) private String displayName;
 @Column(name="is_admin", nullable=false) private boolean admin;
 @Column(name="email_verified", nullable=false) private boolean emailVerified=true;
 private Instant createdAt; private Instant updatedAt;
 public User() {}
 public User(String email,String hash,String name) {
  id=UUID.randomUUID(); this.email=email; passwordHash=hash; displayName=name;
  emailVerified=false; createdAt=Instant.now(); updatedAt=createdAt;
 }
 public UUID getId(){return id;} public String getEmail(){return email;}
 public String getPasswordHash(){return passwordHash;} public String getDisplayName(){return displayName;}
 public boolean isAdmin(){return admin;} public boolean isEmailVerified(){return emailVerified;}
}