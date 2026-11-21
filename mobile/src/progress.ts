/**
 * Renders XP progress strictly from the values returned by /gamification/progress. Level
 * thresholds live in the backend (LevelProgress); the app must not re-derive them.
 */
export type ProgressResponse = {
  totalXp?: number;
  level?: string;
  levelNumber?: number;
  xpToNextLevel?: number | null;
  nextLevelMinXp?: number | null;
  progressRatio?: number;
};

const TIER_LABELS: Record<string, string> = {
  BEGINNER_CHEF: "Başlangıç Şefi",
  HOME_COOK: "Ev Aşçısı",
  SKILLED_COOK: "Usta Aşçı",
  KITCHEN_MASTER: "Mutfak Ustası",
};

export function levelLabel(level?: string): string {
  return (level && TIER_LABELS[level]) || TIER_LABELS.BEGINNER_CHEF;
}

export function describeProgress(progress: ProgressResponse | null | undefined) {
  const xp = Math.max(0, Number(progress?.totalXp) || 0);
  const rawRatio = Number(progress?.progressRatio);
  const ratio = Number.isFinite(rawRatio) ? Math.min(1, Math.max(0, rawRatio)) : 0;
  const isMaxLevel = progress?.nextLevelMinXp === null;
  const toNext = progress?.xpToNextLevel;
  let caption: string;
  if (isMaxLevel) caption = "En yüksek seviyedesin!";
  else if (typeof toNext === "number" && toNext > 0) caption = `${toNext} XP sonra yeni seviye`;
  else caption = "Yeni mutfak hedeflerine devam et";
  return {
    xp,
    label: levelLabel(progress?.level),
    percent: isMaxLevel ? 100 : Math.round(ratio * 1000) / 10,
    caption,
  };
}
