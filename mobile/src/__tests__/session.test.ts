import { describe, expect, it, vi } from "vitest";

vi.mock("@react-native-async-storage/async-storage", () => ({ default: {} }));
vi.mock("expo-constants", () => ({ default: { expoConfig: { extra: {} } } }));

import { ApiError, TIMEOUT_MESSAGE } from "../api";
import { BOOTSTRAP_RETRY_MESSAGE, classifyBootstrapFailure, isAuthFailure } from "../session";

describe("dashboard bootstrap failure handling (KAI-014)", () => {
  it.each([401, 403])("ends the session on HTTP %i", (status) => {
    const error = new ApiError("auth", status);
    expect(isAuthFailure(error)).toBe(true);
    expect(classifyBootstrapFailure(error)).toEqual({ kind: "logout" });
  });

  it.each([
    ["HTTP 500", new ApiError("boom", 500)],
    ["HTTP 503", new ApiError("down", 503)],
    ["HTTP 404", new ApiError("missing", 404)],
    ["timeout", new Error(TIMEOUT_MESSAGE)],
    ["network failure", new TypeError("Network request failed")],
    ["non-error value", "weird"],
  ])("keeps the session and offers a retry on %s", (_label, error) => {
    expect(isAuthFailure(error)).toBe(false);
    expect(classifyBootstrapFailure(error)).toEqual({ kind: "retry", message: BOOTSTRAP_RETRY_MESSAGE });
  });
});
