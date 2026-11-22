import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@react-native-async-storage/async-storage", () => ({
  default: {
    getItem: vi.fn(async () => "test-token"),
    multiSet: vi.fn(async () => undefined),
    multiRemove: vi.fn(async () => undefined),
  },
}));
vi.mock("expo-constants", () => ({ default: { expoConfig: { extra: {} } } }));

import { api, ApiError, combineSignals, RequestCancelledError, TIMEOUT_MESSAGE } from "../api";

/** fetch stub that never resolves on its own and rejects with AbortError when its signal aborts. */
function hangingFetch() {
  return vi.fn((_url: string, init: RequestInit) =>
    new Promise<Response>((_resolve, reject) => {
      const signal = init.signal!;
      const fail = () => reject(Object.assign(new Error("aborted"), { name: "AbortError" }));
      if (signal.aborted) fail();
      else signal.addEventListener("abort", fail, { once: true });
    }));
}

beforeEach(() => {
  vi.spyOn(console, "log").mockImplementation(() => {});
  vi.spyOn(console, "error").mockImplementation(() => {});
});
afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("combineSignals", () => {
  it("returns the timeout signal untouched when no caller signal is given", () => {
    const timeout = new AbortController();
    expect(combineSignals(timeout.signal).signal).toBe(timeout.signal);
  });

  it("aborts when either the timeout or the caller aborts", () => {
    const t1 = new AbortController(); const c1 = new AbortController();
    const a = combineSignals(t1.signal, c1.signal);
    t1.abort();
    expect(a.signal.aborted).toBe(true);

    const t2 = new AbortController(); const c2 = new AbortController();
    const b = combineSignals(t2.signal, c2.signal);
    c2.abort();
    expect(b.signal.aborted).toBe(true);
  });

  it("is aborted immediately when the caller signal is already aborted", () => {
    const caller = new AbortController(); caller.abort();
    expect(combineSignals(new AbortController().signal, caller.signal).signal.aborted).toBe(true);
  });

  it("removes its listeners on cleanup", () => {
    const timeout = new AbortController(); const caller = new AbortController();
    const removeTimeout = vi.spyOn(timeout.signal, "removeEventListener");
    const removeCaller = vi.spyOn(caller.signal, "removeEventListener");
    const combined = combineSignals(timeout.signal, caller.signal);
    combined.cleanup();
    expect(removeTimeout).toHaveBeenCalledWith("abort", expect.any(Function));
    expect(removeCaller).toHaveBeenCalledWith("abort", expect.any(Function));
    caller.abort();
    expect(combined.signal.aborted).toBe(false);
  });
});

describe("api", () => {
  it("times out even when the caller passes its own signal (regression KAI-019)", async () => {
    vi.useFakeTimers();
    const fetchMock = hangingFetch();
    vi.stubGlobal("fetch", fetchMock);
    const caller = new AbortController();

    const request = api("/recipes", { signal: caller.signal }, 1000);
    const assertion = expect(request).rejects.toThrow(TIMEOUT_MESSAGE);
    await vi.advanceTimersByTimeAsync(1000);
    await assertion;
    expect(caller.signal.aborted).toBe(false);
  });

  it("reports caller cancellation separately from a timeout", async () => {
    const fetchMock = hangingFetch();
    vi.stubGlobal("fetch", fetchMock);
    const caller = new AbortController();

    const request = api("/recipes", { signal: caller.signal }, 60_000);
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalled());
    caller.abort();
    await expect(request).rejects.toBeInstanceOf(RequestCancelledError);
  });

  it("times out without a caller signal", async () => {
    vi.useFakeTimers();
    vi.stubGlobal("fetch", hangingFetch());
    const request = api("/recipes", {}, 500);
    const assertion = expect(request).rejects.toThrow(TIMEOUT_MESSAGE);
    await vi.advanceTimersByTimeAsync(500);
    await assertion;
  });

  it("detaches from the caller signal after a successful response", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ ok: 1 }), { status: 200 })));
    const caller = new AbortController();
    const remove = vi.spyOn(caller.signal, "removeEventListener");
    await expect(api("/recipes", { signal: caller.signal })).resolves.toEqual({ ok: 1 });
    expect(remove).toHaveBeenCalledWith("abort", expect.any(Function));
  });

  it("sends the bearer token and surfaces HTTP status through ApiError", async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ error: "nope" }), { status: 503 }));
    vi.stubGlobal("fetch", fetchMock);
    const error = await api("/recipes").catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(503);
    const init = (fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1];
    expect(new Headers(init.headers).get("Authorization")).toBe("Bearer test-token");
  });
});
