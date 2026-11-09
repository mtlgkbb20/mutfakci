import AsyncStorage from "@react-native-async-storage/async-storage";
import Constants from "expo-constants";

const baseURL =
  process.env.EXPO_PUBLIC_API_URL ||
  Constants.expoConfig?.extra?.apiUrl ||
  "http://localhost:8080/api/v1";

export async function api(path: string, options: RequestInit = {}, timeoutMs = 15000) {
  const token = await AsyncStorage.getItem("token");
  const headers = new Headers(options.headers);
  if (!headers.has("Content-Type") && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  const url = `${baseURL}${path}`;
  console.log("[KitchenAI API] request", options.method || "GET", url);
  try {
    const response = await fetch(url, {
      ...options,
      headers,
      signal: options.signal || controller.signal,
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
      throw new Error(data.error || `API error (${response.status})`);
    }
    return data;
  } catch (error: any) {
    console.error("[KitchenAI API] error", {
      name: error?.name,
      message: error?.message,
      url,
    });
    if (error?.name === "AbortError") throw new Error("Sunucuya ulaşılamadı.");
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

export const setSession = async (token: string, user: any) => {
  await AsyncStorage.multiSet([["token", token], ["user", JSON.stringify(user)]]);
};
export const clearSession = () => AsyncStorage.multiRemove(["token", "user"]);
