package com.kitchenai.gamification;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kitchenai.support.WebTestSupport;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

class CookingEventControllerTest {
  static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0};
  static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0, 0, 0, 0, 0, 0, 0, 0};
  static final byte[] WEBP = {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50};

  private final CookingEventService service = mock(CookingEventService.class);
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final MockMvc mvc = WebTestSupport.mvc(new CookingEventController(service, jdbc));
  private final UUID user = UUID.randomUUID();
  private final UUID event = UUID.randomUUID();

  @BeforeEach void login() { WebTestSupport.authenticate(user); }
  @AfterEach void logout() { WebTestSupport.clearAuthentication(); }

  private void userIsAdmin(Boolean admin) {
    when(jdbc.queryForList(anyString(), eq(Boolean.class), eq(user)))
        .thenReturn(admin == null ? List.of() : List.of(admin));
  }

  static Stream<RequestBuilder> adminEndpoints() {
    UUID id = UUID.randomUUID();
    return Stream.of(
        get("/api/v1/events/admin/" + id + "/submissions"),
        get("/api/v1/events/admin/" + id + "/images/" + UUID.randomUUID()),
        post("/api/v1/events/admin/" + id + "/submissions/" + UUID.randomUUID() + "/moderation")
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"),
        post("/api/v1/events/admin").contentType(MediaType.APPLICATION_JSON).content("{}"),
        post("/api/v1/events/admin/" + id + "/open-voting"),
        post("/api/v1/events/admin/" + id + "/finalize"),
        post("/api/v1/events/admin/" + id + "/cancel"));
  }

  @ParameterizedTest
  @MethodSource("adminEndpoints")
  void adminEndpointsReturn403ForNonAdmins(RequestBuilder request) throws Exception {
    userIsAdmin(false);
    mvc.perform(request).andExpect(status().isForbidden());
    verifyNoInteractions(service);
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @ParameterizedTest
  @MethodSource("adminEndpoints")
  void adminEndpointsReturn403ForUnknownUsersInsteadOf500(RequestBuilder request) throws Exception {
    userIsAdmin(null);
    mvc.perform(request).andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }

  @Test
  void adminCanCancelAnEvent() throws Exception {
    userIsAdmin(true);
    mvc.perform(post("/api/v1/events/admin/" + event + "/cancel")).andExpect(status().isNoContent());
    verify(jdbc).update(contains("status='CANCELLED'"), eq(event));
  }

  @Test
  void adminCreateIsAttributedToTheAuthenticatedAdmin() throws Exception {
    userIsAdmin(true);
    UUID created = UUID.randomUUID();
    when(service.create(eq(user), anyMap())).thenReturn(created);
    mvc.perform(post("/api/v1/events/admin").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Kek\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(created.toString()));
  }

  @Test
  void joinUsesTheAuthenticatedUser() throws Exception {
    mvc.perform(post("/api/v1/events/" + event + "/participation")).andExpect(status().isNoContent());
    verify(service).join(event, user);
  }

  @Test
  void voteDefaultsMissingScoreToZero() throws Exception {
    UUID submission = UUID.randomUUID();
    mvc.perform(put("/api/v1/events/" + event + "/submissions/" + submission + "/vote")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isNoContent());
    verify(service).vote(event, user, submission, 0);
  }

  @Test
  void submitAcceptsJpegPngAndWebpWithMatchingSignatures() throws Exception {
    UUID submissionId = UUID.randomUUID();
    when(service.submit(eq(event), eq(user), eq("Lezzetli"), anyList(), anyList())).thenReturn(submissionId);
    mvc.perform(multipart("/api/v1/events/" + event + "/submissions")
            .file(new MockMultipartFile("images", "a.jpg", "image/jpeg", JPEG))
            .file(new MockMultipartFile("images", "b.png", "image/png", PNG))
            .file(new MockMultipartFile("images", "c.webp", "image/webp", WEBP))
            .param("caption", "Lezzetli"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.submissionId").value(submissionId.toString()));
    verify(service).submit(eq(event), eq(user), eq("Lezzetli"),
        eq(List.of("image/jpeg", "image/png", "image/webp")), anyList());
  }

  @Test
  void submitRejectsMoreThanEightImages() throws Exception {
    var request = multipart("/api/v1/events/" + event + "/submissions");
    for (int i = 0; i < 9; i++) request.file(new MockMultipartFile("images", i + ".jpg", "image/jpeg", JPEG));
    mvc.perform(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Upload 1-8 images"));
    verifyNoInteractions(service);
  }

  @Test
  void submitRejectsUnsupportedContentType() throws Exception {
    mvc.perform(multipart("/api/v1/events/" + event + "/submissions")
            .file(new MockMultipartFile("images", "a.gif", "image/gif", JPEG)))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void submitRejectsFilesWhoseBytesDoNotMatchTheDeclaredType() throws Exception {
    mvc.perform(multipart("/api/v1/events/" + event + "/submissions")
            .file(new MockMultipartFile("images", "fake.png", "image/png", JPEG)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Invalid image content"));
    mvc.perform(multipart("/api/v1/events/" + event + "/submissions")
            .file(new MockMultipartFile("images", "tiny.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8})))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void publicImageIsServedWithItsStoredContentType() throws Exception {
    UUID image = UUID.randomUUID();
    when(service.image(event, image)).thenReturn(Map.of("content_type", "image/png", "image_data", PNG));
    mvc.perform(get("/api/v1/events/" + event + "/images/" + image))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"))
        .andExpect(content().bytes(PNG));
  }
}
