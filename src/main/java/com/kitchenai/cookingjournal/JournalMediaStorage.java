package com.kitchenai.cookingjournal;

import com.kitchenai.common.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class JournalMediaStorage {
  private static final long MAX_BYTES = 10_000_000L;
  private static final long MAX_PIXELS = 20_000_000L;
  private final Path root;

  public JournalMediaStorage(@Value("${app.storage-path}") String storagePath) {
    this.root = Path.of(storagePath).toAbsolutePath().normalize();
  }

  public StoredPhoto store(UUID userId, MultipartFile upload) {
    if (upload == null || upload.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "A photo is required");
    }
    String mime = upload.getContentType();
    if (mime == null || !List.of("image/jpeg", "image/png").contains(mime)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Use a JPEG or PNG photo");
    }
    if (upload.getSize() > MAX_BYTES) {
      throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Image is too large");
    }
    try {
      byte[] raw = upload.getBytes();
      BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(raw));
      if (decoded == null) throw new ApiException(HttpStatus.BAD_REQUEST, "The image could not be decoded");
      long pixels = (long) decoded.getWidth() * decoded.getHeight();
      if (decoded.getWidth() < 160 || decoded.getHeight() < 160 || pixels > MAX_PIXELS) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "Image dimensions are not supported");
      }
      String extension = mime.equals("image/png") ? "png" : "jpg";
      ByteArrayOutputStream sanitized = new ByteArrayOutputStream();
      if (!ImageIO.write(decoded, extension, sanitized)) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "The image format is not supported");
      }

      Path userDirectory = root.resolve(userId.toString()).normalize();
      if (!userDirectory.startsWith(root)) throw new IOException("Invalid storage path");
      Files.createDirectories(userDirectory);
      setPermissions(userDirectory, "rwx------");
      String key = userId + "/" + UUID.randomUUID() + "." + extension;
      Path target = resolveOwned(userId, key);
      Files.write(target, sanitized.toByteArray(), StandardOpenOption.CREATE_NEW);
      setPermissions(target, "rw-------");
      return new StoredPhoto(key, mime);
    } catch (IOException exception) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Photo could not be stored");
    }
  }

  public Photo load(UUID userId, String key) {
    Path path = resolveOwned(userId, key);
    try {
      if (!Files.isRegularFile(path)) throw new ApiException(HttpStatus.NOT_FOUND, "Photo not found");
      String lower = path.getFileName().toString().toLowerCase();
      String mime = lower.endsWith(".png") ? "image/png" : "image/jpeg";
      return new Photo(Files.readAllBytes(path), mime);
    } catch (IOException exception) {
      throw new ApiException(HttpStatus.NOT_FOUND, "Photo not found");
    }
  }

  public void delete(UUID userId, String key) {
    if (key == null) return;
    try {
      Files.deleteIfExists(resolveOwned(userId, key));
    } catch (IOException exception) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Photo could not be deleted");
    }
  }

  private Path resolveOwned(UUID userId, String key) {
    Path path = root.resolve(key).normalize();
    if (!path.startsWith(root) || !path.startsWith(root.resolve(userId.toString()).normalize())) {
      throw new ApiException(HttpStatus.NOT_FOUND, "Photo not found");
    }
    return path;
  }

  private void setPermissions(Path path, String permissions) {
    try {
      Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
    } catch (UnsupportedOperationException | IOException ignored) {
      // The authenticated media endpoint still enforces ownership on non-POSIX filesystems.
    }
  }

  public record StoredPhoto(String objectKey, String contentType) {}
  public record Photo(byte[] bytes, String contentType) {}
}
