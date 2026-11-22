import AsyncStorage from "@react-native-async-storage/async-storage";
import Constants from "expo-constants";

export const apiBaseURL =
  process.env.EXPO_PUBLIC_API_URL ||
  Constants.expoConfig?.extra?.apiUrl ||
  "http://localhost:8080/api/v1";

export class ApiError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message);
    this.name = "ApiError";
  }
}

export const TIMEOUT_MESSAGE = "Sunucuya ulaşılamadı.";

/** Error thrown when the caller cancelled the request (as opposed to a timeout). */
export class RequestCancelledError extends Error {
  constructor() {
    super("İstek iptal edildi.");
    this.name = "RequestCancelledError";
  }
}

/**
 * Combines the internal timeout signal with an optional caller signal. Aborting either one aborts
 * the returned signal; `cleanup` removes the listener so long-lived caller signals do not leak.
 */
export function combineSignals(timeoutSignal: AbortSignal, callerSignal?: AbortSignal | null) {
  if (!callerSignal) return { signal: timeoutSignal, cleanup: () => {} };
  const controller = new AbortController();
  const abortFromTimeout = () => controller.abort();
  const abortFromCaller = () => controller.abort();
  if (timeoutSignal.aborted || callerSignal.aborted) {
    controller.abort();
    return { signal: controller.signal, cleanup: () => {} };
  }
  timeoutSignal.addEventListener("abort", abortFromTimeout, { once: true });
  callerSignal.addEventListener("abort", abortFromCaller, { once: true });
  return {
    signal: controller.signal,
    cleanup: () => {
      timeoutSignal.removeEventListener("abort", abortFromTimeout);
      callerSignal.removeEventListener("abort", abortFromCaller);
    },
  };
}

export async function api(path: string, options: RequestInit = {}, timeoutMs = 15000) {
  const token = await AsyncStorage.getItem("token");
  const headers = new Headers(options.headers);
  if (!headers.has("Content-Type") && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const timeoutController = new AbortController();
  const timeout = setTimeout(() => timeoutController.abort(), timeoutMs);
  const { signal, cleanup } = combineSignals(timeoutController.signal, options.signal);
  const url = `${apiBaseURL}${path}`;
  console.log("[KitchenAI API] request", options.method || "GET", url);
  try {
    const response = await fetch(url, {
      ...options,
      headers,
      signal,
    });
    console.log("[KitchenAI API] response", response.status, url);
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      console.error("[KitchenAI API] failure", {
        status: response.status,
        url,
        error: data.error,
        errorId: data.errorId,
      });
      throw new ApiError(data.error || `API error (${response.status})`, response.status);
    }
    return data;
  } catch (error: any) {
    console.error("[KitchenAI API] error", {
      name: error?.name,
      message: error?.message,
      url,
    });
    if (error?.name === "AbortError") {
      if (options.signal?.aborted && !timeoutController.signal.aborted) throw new RequestCancelledError();
      throw new Error(TIMEOUT_MESSAGE);
    }
    throw error;
  } finally {
    clearTimeout(timeout);
    cleanup();
  }
}

export const setSession = async (token: string, user: any) => {
  await AsyncStorage.multiSet([["token", token], ["user", JSON.stringify(user)]]);
};
export const clearSession = () => AsyncStorage.multiRemove(["token", "user"]);
