package com.kitchenai.gamification;

import java.time.LocalDate;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/gamification")
public class GamificationController {
  private final GamificationService service;
  private final JdbcTemplate jdbc;

  public GamificationController(GamificationService service, JdbcTemplate jdbc) {
    this.service = service;
    this.jdbc = jdbc;
  }

  @GetMapping("/me")
  public Map<String,Object> me(@AuthenticationPrincipal UUID user) { return service.me(user); }

  // Compatibility response for the existing mobile profile/dashboard.
  @GetMapping("/progress")
  public Map<String,Object> progress(@AuthenticationPrincipal UUID user) {
    Map<String,Object> data = new LinkedHashMap<>(service.me(user));
    int xp = ((Number)data.getOrDefault("lifetimeXp",0)).intValue();
    data.put("totalXp", xp);
    data.put("level", xp >= 100 ? "KITCHEN_MASTER" : xp >= 60 ? "SKILLED_COOK" : xp >= 30 ? "HOME_COOK" : "BEGINNER_CHEF");
    return data;
  }

  @GetMapping("/leaderboard/current")
  public Map<String,Object> leaderboard(@RequestParam(defaultValue="0") int page,
      @RequestParam(defaultValue="20") int size) { return service.leaderboard(page,size); }

  @GetMapping("/leaderboard/weeks/{weekStart}")
  public Map<String,Object> historical(@PathVariable LocalDate weekStart) {
    var snapshot = jdbc.queryForList("SELECT id,week_start AS weekStart,week_end AS weekEnd,status FROM weekly_leaderboard_snapshot WHERE week_start=?",weekStart);
    if (snapshot.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);
    UUID id=(UUID)snapshot.get(0).get("id");
    return Map.of("snapshot",snapshot.get(0),"entries",jdbc.queryForList(
        "SELECT e.rank_position AS rank,u.id AS userId,u.display_name AS displayName,e.weekly_xp AS weeklyXp,e.lifetime_xp AS lifetimeXp FROM weekly_leaderboard_entry e JOIN app_user u ON u.id=e.user_id WHERE e.snapshot_id=? ORDER BY e.rank_position",id));
  }

  @GetMapping("/badges/catalog")
  public List<Map<String,Object>> catalog() {
    return jdbc.queryForList("SELECT code,name,description,icon,category,repeatable FROM badge_definition WHERE active=true ORDER BY category,name");
  }

  @GetMapping("/badges/me")
  public List<Map<String,Object>> myBadges(@AuthenticationPrincipal UUID user) { return service.badgeHistory(user); }

  @GetMapping("/users/{userId}/badges")
  public List<Map<String,Object>> badges(@PathVariable UUID userId) { return service.badgeHistory(userId); }

  @PostMapping("/admin/settlements/{weekStart}")
  public Map<String,Object> settle(@AuthenticationPrincipal UUID admin, @PathVariable LocalDate weekStart) {
    requireAdmin(admin);
    LocalDate currentWeekStart = service.currentWeek().start();
    if (!weekStart.getDayOfWeek().equals(java.time.DayOfWeek.MONDAY) || !weekStart.plusWeeks(1).isBefore(currentWeekStart.plusDays(1)))
      throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Only closed Monday-based weeks can be settled");
    return service.settleWeek(weekStart);
  }

  private void requireAdmin(UUID user) {
    Boolean allowed=jdbc.queryForObject("SELECT is_admin FROM app_user WHERE id=?",Boolean.class,user);
    if (!Boolean.TRUE.equals(allowed)) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
  }
}
