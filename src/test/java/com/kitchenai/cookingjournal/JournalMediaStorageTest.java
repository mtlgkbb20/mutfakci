package com.kitchenai.cookingjournal;

import static org.junit.jupiter.api.Assertions.*;

import com.kitchenai.common.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class JournalMediaStorageTest {
  @TempDir Path temp;

  @Test
  void storesSanitizedImageUnderPrivateOwnerDirectoryAndLoadsIt() throws Exception {
    UUID userId = UUID.randomUUID();
    JournalMediaStorage storage = new JournalMediaStorage(temp.toString());
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB), "png", output);

    var stored = storage.store(userId,
        new MockMultipartFile("image", "dish.png", "image/png", output.toByteArray()));
    var loaded = storage.load(userId, stored.objectKey());

    assertTrue(stored.objectKey().startsWith(userId + "/"));
    assertEquals("image/png", loaded.contentType());
    assertNotEquals(0, loaded.bytes().length);
  }

  @Test
  void preventsReadingAnotherUsersMediaKey() {
    UUID owner = UUID.randomUUID();
    UUID attacker = UUID.randomUUID();
    JournalMediaStorage storage = new JournalMediaStorage(temp.toString());

    assertThrows(ApiException.class, () -> storage.load(attacker, owner + "/private.jpg"));
  }

  @Test
  void rejectsUnsupportedMimeTypes() {
    JournalMediaStorage storage = new JournalMediaStorage(temp.toString());
    var upload = new MockMultipartFile("image", "dish.svg", "image/svg+xml", "<svg/>".getBytes());

    assertThrows(ApiException.class, () -> storage.store(UUID.randomUUID(), upload));
  }
}
