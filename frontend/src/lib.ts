/** Pure helpers shared by the web app; kept free of React so they can be unit-tested. */

/** Pretty-prints a JSON string; returns non-JSON input unchanged and empty input as "". */
export function prettyJson(value: string | null | undefined): string {
  if (!value) return "";
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

/** Extracts the backend's `{ error }` message from an axios-style error, or returns the fallback. */
export function apiErrorMessage(error: unknown, fallback: string): string {
  const message = (error as { response?: { data?: { error?: unknown } } } | null | undefined)?.response?.data?.error;
  return typeof message === "string" && message.trim() ? message : fallback;
}

type HeaderBag = { Authorization?: unknown; [key: string]: unknown };

/** Adds the bearer token from storage to the request headers; leaves them untouched without a token. */
export function withAuthHeader<T extends { headers: HeaderBag }>(config: T, storage: Pick<Storage, "getItem">): T {
  const token = storage.getItem("token");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
}

export const GENERIC_ERROR = "Beklenmedik bir hata oluştu. Lütfen support ile iletişime geçin.";

/** Display name for a membership tier; unknown tiers fall back to the top tier label. */
export function membershipTierLabel(tier: string): string {
  if (tier === "FREE") return "Free üyelik";
  if (tier === "COOKER") return "Cooker üyeliği";
  return "Chef";
}

/** Width of the quota bar in percent, clamped to 0–100 and safe for a zero/negative limit. */
export function quotaUsagePercent(usage: number, limit: number): number {
  if (!Number.isFinite(usage) || usage <= 0) return 0;
  return Math.min(100, (usage / Math.max(1, limit)) * 100);
}
