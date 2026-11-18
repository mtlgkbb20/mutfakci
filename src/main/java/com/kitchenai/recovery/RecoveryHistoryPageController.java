package com.kitchenai.recovery;

import com.kitchenai.common.ApiException;
import com.kitchenai.cooking.CookingSession;
import com.kitchenai.cooking.CookingSessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recovery-history")
public class RecoveryHistoryPageController {
  private final CookingSessionRepository sessions;
  private final RecipeRecoveryAttemptRepository attempts;

  public RecoveryHistoryPageController(CookingSessionRepository sessions,
      RecipeRecoveryAttemptRepository attempts) {
    this.sessions = sessions;
    this.attempts = attempts;
  }

  public record RecoverySummary(java.util.UUID id, String recipeName, String status, Instant createdAt) {}
  public record PageResponse<T>(List<T> content, int page, int size, long totalElements,
      int totalPages, boolean hasNext) {
    static <T> PageResponse<T> from(Page<T> page) {
      return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
          page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }
  }

  @GetMapping("/page")
  public PageResponse<RecoverySummary> list(
      @AuthenticationPrincipal UUID userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to) {
    if (page < 0 || size < 1 || size > 50) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid pagination parameters");
    }
    if ((from == null) != (to == null) || (from != null && from.isAfter(to))) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "A valid from/to date range is required");
    }
    List<UUID> sessionIds = sessions.findByUserIdOrderByStartedAtDesc(userId).stream()
        .map(CookingSession::getId).toList();
    if (sessionIds.isEmpty()) {
      return new PageResponse<>(List.of(), page, size, 0, 0, false);
    }
    Page<RecipeRecoveryAttempt> result = from == null
        ? attempts.findBySessionIdInOrderByCreatedAtDesc(sessionIds, PageRequest.of(page, size))
        : attempts.findBySessionIdInAndCreatedAtBetweenOrderByCreatedAtDesc(
            sessionIds, from, to, PageRequest.of(page, size));
    return PageResponse.from(result.map(attempt -> new RecoverySummary(
        attempt.getId(), attempt.getFallbackRecipeName(), attempt.getStatus(), attempt.getCreatedAt())));
  }
}
