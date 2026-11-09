package com.kitchenai.gamification;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/gamification")
public class GamificationController {

  private final GamificationService gamificationService;
  private final JdbcTemplate jdbc;

  public GamificationController(
      GamificationService gamificationService, JdbcTemplate jdbc) {
    this.gamificationService = gamificationService;
    this.jdbc = jdbc;
  }

  @GetMapping("/me")
  public Map<String, Object> me(@AuthenticationPrincipal UUID userId) {
    return gamificationService.me(userId);
  }

  // Compatibility response for the existing mobile profile/dashboard.
  @GetMapping("/progress")
  public Map<String, Object> progress(@AuthenticationPrincipal UUID userId) {
    Map<String, Object> data =
        new LinkedHashMap<>(gamificationService.me(userId));
    int totalXp = ((Number) data.getOrDefault("lifetimeXp", 0)).intValue();

    data.put("totalXp", totalXp);
    data.put(
        "level",
        totalXp >= 100
            ? "KITCHEN_MASTER"
            : totalXp >= 60
                ? "SKILLED_COOK"
                : totalXp >= 30
                    ? "HOME_COOK"
                    : "BEGINNER_CHEF");

    return data;
  }

  @GetMapping("/leaderboard/current")
  public Map<String, Object> leaderboard(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return gamificationService.leaderboard(page, size);
  }

  @GetMapping("/leaderboard/weeks/{weekStart}")
  public Map<String, Object> historical(@PathVariable LocalDate weekStart) {
    var snapshots =
        jdbc.queryForList(
            """
            SELECT
              id,
              week_start AS weekStart,
              week_end AS weekEnd,
              status
            FROM weekly_leaderboard_snapshot
            WHERE week_start = ?
            """,
            weekStart);

    if (snapshots.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    UUID snapshotId = (UUID) snapshots.get(0).get("id");
    var entries =
        jdbc.queryForList(
            """
            SELECT
              entry.rank_position AS rank,
              app_user.id AS userId,
              app_user.display_name AS displayName,
              entry.weekly_xp AS weeklyXp,
              entry.lifetime_xp AS lifetimeXp
            FROM weekly_leaderboard_entry entry
            JOIN app_user ON app_user.id = entry.user_id
            WHERE entry.snapshot_id = ?
            ORDER BY entry.rank_position
            """,
            snapshotId);

    return Map.of("snapshot", snapshots.get(0), "entries", entries);
  }

  @GetMapping("/badges/catalog")
  public List<Map<String, Object>> catalog() {
    return jdbc.queryForList(
        """
        SELECT code, name, description, icon, category, repeatable
        FROM badge_definition
        WHERE active = TRUE
        ORDER BY category, name
        """);
  }

  @GetMapping("/badges/me")
  public List<Map<String, Object>> myBadges(
      @AuthenticationPrincipal UUID userId) {
    return gamificationService.badgeHistory(userId);
  }

  @GetMapping("/users/{userId}/badges")
  public List<Map<String, Object>> badges(@PathVariable UUID userId) {
    return gamificationService.badgeHistory(userId);
  }

  @PostMapping("/admin/settlements/{weekStart}")
  public Map<String, Object> settle(
      @AuthenticationPrincipal UUID adminId,
      @PathVariable LocalDate weekStart) {

    requireAdmin(adminId);

    LocalDate currentWeekStart = gamificationService.currentWeek().start();
    boolean isMonday = weekStart.getDayOfWeek() == java.time.DayOfWeek.MONDAY;
    boolean isClosedWeek = !weekStart.plusWeeks(1).isAfter(currentWeekStart);

    if (!isMonday || !isClosedWeek) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Only closed Monday-based weeks can be settled");
    }

    return gamificationService.settleWeek(weekStart);
  }

  private void requireAdmin(UUID userId) {
    Boolean isAdmin =
        jdbc.queryForObject(
            "SELECT is_admin FROM app_user WHERE id = ?",
            Boolean.class,
            userId);

    if (!Boolean.TRUE.equals(isAdmin)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
  }
}
