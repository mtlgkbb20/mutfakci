import AsyncStorage from "@react-native-async-storage/async-storage";
import Constants from "expo-constants";

const baseUrl =
  process.env.EXPO_PUBLIC_API_URL ||
  Constants.expoConfig?.extra?.apiUrl ||
  "http://localhost:8080/api/v1";

export async function api(
  path: string,
  options: RequestInit = {},
  timeoutMs = 15_000,
) {
  const token = await AsyncStorage.getItem("token");
  const headers = new Headers(options.headers);

  if (!headers.has("Content-Type") && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }

  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch(`${baseUrl}${path}`, {
      ...options,
      headers,
      signal: options.signal || controller.signal,
    });

    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
      throw new Error(data.error || `API error (${response.status})`);
    }

    return data;
  } catch (error: any) {
    if (error?.name === "AbortError") {
      throw new Error("Sunucuya ulaşılamadı.");
    }

    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

export async function setSession(token: string, user: unknown) {
  const serializedUser = JSON.stringify(user);

  if (!serializedUser) {
    throw new Error("Oturum bilgisi kaydedilemedi.");
  }

  await AsyncStorage.multiSet([
    ["token", token],
    ["user", serializedUser],
  ]);
}

export function clearSession() {
  return AsyncStorage.multiRemove(["token", "user"]);
}
