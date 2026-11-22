/** Session restore rules for the web app; kept free of React and axios so they can be unit-tested. */

export const TOKEN_KEY = "token";

export const SESSION_RETRY_MESSAGE =
  "Oturumun şu anda doğrulanamadı. Bağlantını kontrol edip tekrar dene.";

export type SessionUser = { id: string; email: string; displayName: string; isAdmin: boolean };

export type RestoreResult =
  | { kind: "anonymous" }
  | { kind: "authenticated"; user: SessionUser }
  | { kind: "retry"; message: string };

type TokenStorage = Pick<Storage, "getItem" | "removeItem">;

/**
 * Only a rejected token (401/403) ends the session. Network errors, timeouts and 5xx are
 * transient: the token is kept so a retry can succeed without logging the user out.
 */
export function isAuthFailure(error: unknown): boolean {
  const status = (error as { response?: { status?: unknown } } | null | undefined)?.response?.status;
  return status === 401 || status === 403;
}

function isSessionUser(value: unknown): value is SessionUser {
  const user = value as Partial<SessionUser> | null;
  return !!user && typeof user.id === "string" && typeof user.displayName === "string";
}

/** Rebuilds the logged-in user from a stored token, e.g. after a page reload. */
export async function restoreSession(
  fetchCurrentUser: () => Promise<unknown>,
  storage: TokenStorage,
): Promise<RestoreResult> {
  if (!storage.getItem(TOKEN_KEY)) return { kind: "anonymous" };
  try {
    const user = await fetchCurrentUser();
    if (isSessionUser(user)) return { kind: "authenticated", user };
    return { kind: "retry", message: SESSION_RETRY_MESSAGE };
  } catch (error) {
    if (isAuthFailure(error)) {
      storage.removeItem(TOKEN_KEY);
      return { kind: "anonymous" };
    }
    return { kind: "retry", message: SESSION_RETRY_MESSAGE };
  }
}
