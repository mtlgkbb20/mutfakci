import { describe, expect, it } from "vitest";
import { describeProgress, levelLabel } from "../progress";

describe("describeProgress (KAI-027)", () => {
  it("uses the backend ratio instead of a hard-coded 30 XP step", () => {
    // 60 XP = level 2 (50..150): 10% into the level, 90 XP to go.
    const view = describeProgress({ totalXp: 60, level: "HOME_COOK", progressRatio: 0.1, xpToNextLevel: 90, nextLevelMinXp: 150 });
    expect(view).toEqual({ xp: 60, label: "Ev Aşçısı", percent: 10, caption: "90 XP sonra yeni seviye" });
  });

  it("shows an empty bar and the full distance at 0 XP", () => {
    const view = describeProgress({ totalXp: 0, level: "BEGINNER_CHEF", progressRatio: 0, xpToNextLevel: 50, nextLevelMinXp: 50 });
    expect(view.percent).toBe(0);
    expect(view.caption).toBe("50 XP sonra yeni seviye");
  });

  it("shows 1 XP remaining just below a threshold", () => {
    const view = describeProgress({ totalXp: 49, progressRatio: 0.98, xpToNextLevel: 1, nextLevelMinXp: 50 });
    expect(view.percent).toBe(98);
    expect(view.caption).toBe("1 XP sonra yeni seviye");
  });

  it("renders the max level as a full bar without a next-level target", () => {
    const view = describeProgress({ totalXp: 999_999, level: "KITCHEN_MASTER", progressRatio: 1, xpToNextLevel: null, nextLevelMinXp: null });
    expect(view.percent).toBe(100);
    expect(view.caption).toBe("En yüksek seviyedesin!");
  });

  it("clamps out-of-range or missing values", () => {
    expect(describeProgress({ totalXp: -5, progressRatio: 7 }).percent).toBe(100);
    expect(describeProgress({ totalXp: -5, progressRatio: 7 }).xp).toBe(0);
    expect(describeProgress(undefined)).toEqual({ xp: 0, label: "Başlangıç Şefi", percent: 0, caption: "Yeni mutfak hedeflerine devam et" });
  });

  it("maps every tier and falls back for unknown codes", () => {
    expect(levelLabel("SKILLED_COOK")).toBe("Usta Aşçı");
    expect(levelLabel("KITCHEN_MASTER")).toBe("Mutfak Ustası");
    expect(levelLabel("LEVEL_9")).toBe("Başlangıç Şefi");
  });
});
