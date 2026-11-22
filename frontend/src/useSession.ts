import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "./api";
import { restoreSession, SessionUser, TOKEN_KEY } from "./session";

export type SessionState =
  | { status: "checking" }
  | { status: "anonymous" }
  | { status: "authenticated"; user: SessionUser }
  | { status: "error"; message: string };

const fetchCurrentUser = async () => (await api.get("/auth/me")).data;

export function useSession() {
  const [state, setState] = useState<SessionState>(() =>
    localStorage.getItem(TOKEN_KEY) ? { status: "checking" } : { status: "anonymous" },
  );
  // Ignores a slow restore that finishes after the user already logged in or out.
  const generation = useRef(0);

  const restore = useCallback(async () => {
    const current = ++generation.current;
    setState({ status: "checking" });
    const result = await restoreSession(fetchCurrentUser, localStorage);
    if (current !== generation.current) return;
    if (result.kind === "authenticated") setState({ status: "authenticated", user: result.user });
    else if (result.kind === "anonymous") setState({ status: "anonymous" });
    else setState({ status: "error", message: result.message });
  }, []);

  useEffect(() => {
    if (localStorage.getItem(TOKEN_KEY)) void restore();
  }, [restore]);

  const login = useCallback((token: string, user: SessionUser) => {
    generation.current++;
    localStorage.setItem(TOKEN_KEY, token);
    setState({ status: "authenticated", user });
  }, []);

  const logout = useCallback(() => {
    generation.current++;
    localStorage.removeItem(TOKEN_KEY);
    setState({ status: "anonymous" });
  }, []);

  return { state, login, logout, retry: restore };
}
