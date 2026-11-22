package com.kitchenai.gamification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class GamificationControllerTest {

  private Map<String, Object> serviceResponse(int lifetimeXp) {
    Map<String, Object> me = new LinkedHashMap<>();
    me.put("lifetimeXp", lifetimeXp);
    me.put("weeklyXp", 5);
    me.put("level", LevelProgress.levelFor(lifetimeXp));
    me.putAll(LevelProgress.of(lifetimeXp).toFields());
    return me;
  }

  @Test
  void progressTierAndProgressFieldsShareTheSameThresholds() {
    GamificationService service = mock(GamificationService.class);
    GamificationController controller = new GamificationController(service, mock(JdbcTemplate.class));
    UUID user = UUID.randomUUID();
    // 60 XP was SKILLED_COOK under the old 30/60/100 tiers while the progress
    // fields described level 2; both must now agree.
    when(service.me(user)).thenReturn(serviceResponse(60));

    Map<String, Object> body = controller.progress(user);

    assertEquals(60, body.get("totalXp"));
    assertEquals(2, body.get("levelNumber"));
    assertEquals("HOME_COOK", body.get("level"));
    assertEquals(50, body.get("currentLevelMinXp"));
    assertEquals(150, body.get("nextLevelMinXp"));
    assertEquals(90, body.get("xpToNextLevel"));
    assertEquals(0.1, (double) body.get("progressRatio"), 1e-9);
    verify(service).me(user);
  }

  @Test
  void progressIsComputedForTheAuthenticatedUserOnly() {
    GamificationService service = mock(GamificationService.class);
    GamificationController controller = new GamificationController(service, mock(JdbcTemplate.class));
    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();
    when(service.me(alice)).thenReturn(serviceResponse(0));
    when(service.me(bob)).thenReturn(serviceResponse(300));

    assertEquals("BEGINNER_CHEF", controller.progress(alice).get("level"));
    assertEquals("KITCHEN_MASTER", controller.progress(bob).get("level"));
  }
}
