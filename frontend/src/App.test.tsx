import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";
import { api } from "./api";
import { deferred, httpError, routeApi } from "./test/apiMock";

vi.mock("./api", () => ({ api: { get: vi.fn(), post: vi.fn() } }));

const ada = { id: "u1", email: "ada@example.com", displayName: "Ada", isAdmin: false };
const membership = { tier: "FREE", billingInterval: "NONE", remaining: 2, limit: 3, usage: 1, period: "LIFETIME", paidAccess: false, plans: [] };

const dashboard = {
  "/membership": () => membership,
  "/gamification/me": () => ({ lifetimeXp: 40, level: 2, weeklyXp: 10, rank: 3, recentBadges: [] }),
  "/gamification/leaderboard/current": () => ({ entries: [] }),
  "/events": () => [],
};

describe("App session restore", () => {
  beforeEach(() => vi.spyOn(window, "alert").mockImplementation(() => {}));

  it("shows the login form without a stored token and does not call /auth/me", () => {
    routeApi(api, { get: dashboard });
    render(<App />);
    expect(screen.getByRole("button", { name: /giriş yap/i })).toBeInTheDocument();
    expect(api.get).not.toHaveBeenCalled();
  });

  it("restores the logged-in user after a page reload instead of showing the login form", async () => {
    localStorage.setItem("token", "jwt");
    routeApi(api, { get: { ...dashboard, "/auth/me": () => ada } });

    render(<App />);

    expect(screen.getByRole("status")).toHaveTextContent("Oturumun yükleniyor");
    expect(await screen.findByText("Merhaba, Ada")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /giriş yap/i })).not.toBeInTheDocument();
    expect(await screen.findByText("Free üyelik")).toBeInTheDocument();
    expect(localStorage.getItem("token")).toBe("jwt");
  });

  it.each([401, 403])("drops a rejected token (%i) and shows the login form", async (status) => {
    localStorage.setItem("token", "expired");
    routeApi(api, { get: { "/auth/me": () => { throw httpError(status); } } });

    render(<App />);

    expect(await screen.findByRole("button", { name: /giriş yap/i })).toBeInTheDocument();
    expect(localStorage.getItem("token")).toBeNull();
    expect(api.get).not.toHaveBeenCalledWith("/membership");
  });

  it("keeps the token on a network failure and recovers on retry", async () => {
    localStorage.setItem("token", "jwt");
    let attempts = 0;
    routeApi(api, { get: { ...dashboard, "/auth/me": () => { attempts++; if (attempts === 1) throw new Error("Network Error"); return ada; } } });

    render(<App />);

    expect(await screen.findByRole("alert")).toHaveTextContent("doğrulanamadı");
    expect(localStorage.getItem("token")).toBe("jwt");

    await userEvent.click(screen.getByRole("button", { name: "Tekrar dene" }));

    expect(await screen.findByText("Merhaba, Ada")).toBeInTheDocument();
    expect(attempts).toBe(2);
  });

  it("lets the user log out from the retry screen", async () => {
    localStorage.setItem("token", "jwt");
    routeApi(api, { get: { "/auth/me": () => { throw httpError(500); } } });

    render(<App />);
    await userEvent.click(await screen.findByRole("button", { name: "Çıkış yap" }));

    expect(screen.getByRole("button", { name: /giriş yap/i })).toBeInTheDocument();
    expect(localStorage.getItem("token")).toBeNull();
  });

  it("never flashes the login form while /auth/me is still pending", async () => {
    localStorage.setItem("token", "jwt");
    const me = deferred<typeof ada>();
    routeApi(api, { get: { ...dashboard, "/auth/me": () => me.promise } });

    render(<App />);

    expect(screen.getByRole("status")).toHaveTextContent("Oturumun yükleniyor");
    expect(screen.queryByPlaceholderText("E-posta")).not.toBeInTheDocument();
    me.resolve(ada);
    expect(await screen.findByText("Merhaba, Ada")).toBeInTheDocument();
  });

  it("stores the token on login and clears it on logout", async () => {
    routeApi(api, { get: dashboard, post: { "/auth/login": () => ({ token: "fresh", user: ada }) } });
    render(<App />);

    await userEvent.type(screen.getByPlaceholderText("E-posta"), "ada@example.com");
    await userEvent.type(screen.getByPlaceholderText("Şifre"), "supersecret");
    await userEvent.click(screen.getByRole("button", { name: /giriş yap/i }));

    expect(await screen.findByText("Merhaba, Ada")).toBeInTheDocument();
    expect(localStorage.getItem("token")).toBe("fresh");

    await userEvent.click(screen.getByRole("button", { name: "Çıkış" }));
    expect(screen.getByRole("button", { name: /giriş yap/i })).toBeInTheDocument();
    expect(localStorage.getItem("token")).toBeNull();
  });

  it("shows the admin event form only to admins", async () => {
    localStorage.setItem("token", "jwt");
    routeApi(api, { get: { ...dashboard, "/auth/me": () => ({ ...ada, isAdmin: true }) } });
    render(<App />);
    expect(await screen.findByRole("button", { name: "Etkinliği oluştur" })).toBeInTheDocument();
  });

  it("hides the admin event form from regular users", async () => {
    localStorage.setItem("token", "jwt");
    routeApi(api, { get: { ...dashboard, "/auth/me": () => ada } });
    render(<App />);
    await screen.findByText("Merhaba, Ada");
    expect(screen.queryByRole("button", { name: "Etkinliği oluştur" })).not.toBeInTheDocument();
  });
});

describe("App recipe generation", () => {
  beforeEach(() => {
    localStorage.setItem("token", "jwt");
  });

  it("shows the backend error message when generation is rejected", async () => {
    const alert = vi.spyOn(window, "alert").mockImplementation(() => {});
    vi.spyOn(console, "error").mockImplementation(() => {});
    routeApi(api, {
      get: { ...dashboard, "/auth/me": () => ada },
      post: { "/recipes/generate": () => { throw Object.assign(new Error("429"), { response: { status: 429, data: { error: "Tarif hakkın doldu." } } }); } },
    });
    render(<App />);

    await userEvent.type(await screen.findByPlaceholderText("Örn. yumuşak elmalı kek"), "elmalı kek");
    await userEvent.click(screen.getByRole("button", { name: "Tarif oluştur" }));

    await waitFor(() => expect(alert).toHaveBeenCalledWith("Tarif hakkın doldu."));
    expect(screen.getByRole("button", { name: "Tarif oluştur" })).toBeEnabled();
  });

  it("does not call the API for a blank request", async () => {
    routeApi(api, { get: { ...dashboard, "/auth/me": () => ada } });
    render(<App />);
    await userEvent.click(await screen.findByRole("button", { name: "Tarif oluştur" }));
    expect(api.post).not.toHaveBeenCalled();
  });

  it("renders the generated recipe and refreshes the quota", async () => {
    routeApi(api, {
      get: { ...dashboard, "/auth/me": () => ada },
      post: { "/recipes/generate": () => ({ id: "r1", title: "Elmalı Kek", description: "Yumuşak", servings: 4, preparationTime: 15, cookingTime: 40, sourceType: "AI",
        ingredients: JSON.stringify([{ name: "Elma", quantity: 2, unit: "adet" }]),
        stages: JSON.stringify([{ order: 1, title: "Hazırlık", instructions: "Elmaları doğra" }]) }) },
    });
    render(<App />);

    await userEvent.type(await screen.findByPlaceholderText("Örn. yumuşak elmalı kek"), "elmalı kek");
    await userEvent.click(screen.getByRole("button", { name: "Tarif oluştur" }));

    expect(await screen.findByRole("heading", { name: "Elmalı Kek" })).toBeInTheDocument();
    expect(screen.getByText("2 adet Elma")).toBeInTheDocument();
    expect(api.post).toHaveBeenCalledWith("/recipes/generate", { desiredFood: "elmalı kek", servings: 4 });
    expect(vi.mocked(api.get).mock.calls.filter(([url]) => url === "/membership").length).toBeGreaterThanOrEqual(2);
  });
});
