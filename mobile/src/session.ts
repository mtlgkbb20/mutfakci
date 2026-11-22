import { ApiError } from "./api";

/**
 * Only real authentication failures may end the local session. Server errors, timeouts and
 * network failures are transient: the session must be kept and the user offered a retry.
 */
export function isAuthFailure(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 401 || error.status === 403);
}

export type BootstrapFailure = { kind: "logout" } | { kind: "retry"; message: string };

export const BOOTSTRAP_RETRY_MESSAGE =
  "Verilerin şu anda yüklenemedi. Bağlantını kontrol edip tekrar dene.";

export function classifyBootstrapFailure(error: unknown): BootstrapFailure {
  return isAuthFailure(error) ? { kind: "logout" } : { kind: "retry", message: BOOTSTRAP_RETRY_MESSAGE };
}
