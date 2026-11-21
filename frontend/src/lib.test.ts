import { describe, expect, it } from "vitest";
import { apiErrorMessage, prettyJson, withAuthHeader } from "./lib";

describe("prettyJson", () => {
  it("formats valid JSON with two-space indentation", () => {
    expect(prettyJson('{"a":1,"b":[2]}')).toBe('{\n  "a": 1,\n  "b": [\n    2\n  ]\n}');
  });
  it("returns plain text unchanged", () => {
    expect(prettyJson("not json")).toBe("not json");
  });
  it.each([null, undefined, ""])("returns an empty string for %s", (value) => {
    expect(prettyJson(value)).toBe("");
  });
});

describe("apiErrorMessage", () => {
  it("uses the backend error message when present", () => {
    expect(apiErrorMessage({ response: { data: { error: "Kota doldu" } } }, "fallback")).toBe("Kota doldu");
  });
  it.each([
    ["no response (network error)", new Error("Network Error")],
    ["empty body", { response: { data: {} } }],
    ["blank message", { response: { data: { error: "   " } } }],
    ["non-string message", { response: { data: { error: { code: 1 } } } }],
    ["null", null],
    ["undefined", undefined],
  ])("falls back for %s", (_label, error) => {
    expect(apiErrorMessage(error, "fallback")).toBe("fallback");
  });
});

describe("withAuthHeader", () => {
  const storage = (token: string | null) => ({ getItem: (key: string) => (key === "token" ? token : null) });

  it("adds the bearer token when one is stored", () => {
    const config = withAuthHeader({ headers: {} as Record<string, unknown> }, storage("abc"));
    expect(config.headers.Authorization).toBe("Bearer abc");
  });
  it("does not add an Authorization header without a token", () => {
    const config = withAuthHeader({ headers: {} as Record<string, unknown> }, storage(null));
    expect(config.headers).not.toHaveProperty("Authorization");
  });
});
