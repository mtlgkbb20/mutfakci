import { describe, expect, it, vi } from "vitest";
import { isAuthFailure, restoreSession, SESSION_RETRY_MESSAGE, TOKEN_KEY } from "./session";

const ada = { id: "u1", email: "ada@example.com", displayName: "Ada", isAdmin: false };

function memoryStorage(token: string | null) {
  const values = new Map<string, string>();
  if (token) values.set(TOKEN_KEY, token);
  return {
    getItem: (key: string) => values.get(key) ?? null,
    removeItem: vi.fn((key: string) => void values.delete(key)),
    values,
  };
}

const httpError = (status: number) => Object.assign(new Error(`HTTP ${status}`), { response: { status } });

describe("isAuthFailure", () => {
  it.each([401, 403])("treats %i as a rejected session", (status) => {
    expect(isAuthFailure(httpError(status))).toBe(true);
  });
  it.each([
    ["500", httpError(500)],
    ["404", httpError(404)],
    ["network error without response", new Error("Network Error")],
    ["null", null],
    ["undefined", undefined],
  ])("does not treat %s as a rejected session", (_label, error) => {
    expect(isAuthFailure(error)).toBe(false);
  });
});

describe("restoreSession", () => {
  it("stays anonymous without a stored token and never calls the API", async () => {
    const fetchUser = vi.fn();
    expect(await restoreSession(fetchUser, memoryStorage(null))).toEqual({ kind: "anonymous" });
    expect(fetchUser).not.toHaveBeenCalled();
  });

  it("restores the user when the stored token is still valid", async () => {
    const storage = memoryStorage("jwt");
    expect(await restoreSession(async () => ada, storage)).toEqual({ kind: "authenticated", user: ada });
    expect(storage.values.get(TOKEN_KEY)).toBe("jwt");
  });

  it.each([401, 403])("clears the token and logs out when /auth/me answers %i", async (status) => {
    const storage = memoryStorage("expired");
    const result = await restoreSession(() => Promise.reject(httpError(status)), storage);
    expect(result).toEqual({ kind: "anonymous" });
    expect(storage.removeItem).toHaveBeenCalledWith(TOKEN_KEY);
    expect(storage.values.has(TOKEN_KEY)).toBe(false);
  });

  it.each([
    ["server error", httpError(500)],
    ["network failure", new Error("Network Error")],
  ])("keeps the token and asks for a retry on %s", async (_label, error) => {
    const storage = memoryStorage("jwt");
    const result = await restoreSession(() => Promise.reject(error), storage);
    expect(result).toEqual({ kind: "retry", message: SESSION_RETRY_MESSAGE });
    expect(storage.removeItem).not.toHaveBeenCalled();
  });

  it.each([null, "", { id: 1 }, { displayName: "Ada" }])("asks for a retry on a malformed user payload %j", async (payload) => {
    const storage = memoryStorage("jwt");
    expect(await restoreSession(async () => payload, storage)).toEqual({ kind: "retry", message: SESSION_RETRY_MESSAGE });
    expect(storage.removeItem).not.toHaveBeenCalled();
  });
});
