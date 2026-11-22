import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Auth } from "./Auth";
import { api } from "../api";
import { GENERIC_ERROR } from "../lib";
import { httpError, routeApi } from "../test/apiMock";

vi.mock("../api", () => ({ api: { get: vi.fn(), post: vi.fn() } }));

const ada = { id: "u1", email: "ada@example.com", displayName: "Ada", isAdmin: false };

describe("Auth", () => {
  it("logs in with email and password and hands the token and user to the caller", async () => {
    routeApi(api, { post: { "/auth/login": () => ({ token: "jwt", user: ada }) } });
    const onLogin = vi.fn();
    render(<Auth onLogin={onLogin} />);

    await userEvent.type(screen.getByPlaceholderText("E-posta"), "ada@example.com");
    await userEvent.type(screen.getByPlaceholderText("Şifre"), "supersecret");
    await userEvent.click(screen.getByRole("button", { name: /giriş yap/i }));

    await waitFor(() => expect(onLogin).toHaveBeenCalledWith("jwt", ada));
    expect(api.post).toHaveBeenCalledWith("/auth/login", { email: "ada@example.com", password: "supersecret" });
  });

  it("switches to registration and sends the display name", async () => {
    routeApi(api, { post: { "/auth/register": () => ({ token: "jwt", user: ada }) } });
    const onLogin = vi.fn();
    render(<Auth onLogin={onLogin} />);

    await userEvent.click(screen.getByRole("button", { name: "Hesap oluştur" }));
    await userEvent.type(screen.getByPlaceholderText("Adın"), "Ada");
    await userEvent.type(screen.getByPlaceholderText("E-posta"), "ada@example.com");
    await userEvent.type(screen.getByPlaceholderText("Şifre"), "supersecret");
    await userEvent.click(screen.getByRole("button", { name: /kayıt ol/i }));

    await waitFor(() => expect(onLogin).toHaveBeenCalledWith("jwt", ada));
    expect(api.post).toHaveBeenCalledWith("/auth/register", { email: "ada@example.com", password: "supersecret", displayName: "Ada" });
  });

  it("shows only the name field in registration mode", async () => {
    render(<Auth onLogin={vi.fn()} />);
    expect(screen.queryByPlaceholderText("Adın")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Hesap oluştur" }));
    expect(screen.getByPlaceholderText("Adın")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Zaten hesabım var" }));
    expect(screen.queryByPlaceholderText("Adın")).not.toBeInTheDocument();
  });

  it("alerts a generic error and does not log in on rejected credentials", async () => {
    const alert = vi.spyOn(window, "alert").mockImplementation(() => {});
    vi.spyOn(console, "error").mockImplementation(() => {});
    routeApi(api, { post: { "/auth/login": () => { throw httpError(401); } } });
    const onLogin = vi.fn();
    render(<Auth onLogin={onLogin} />);

    await userEvent.type(screen.getByPlaceholderText("E-posta"), "ada@example.com");
    await userEvent.type(screen.getByPlaceholderText("Şifre"), "wrongpass1");
    await userEvent.click(screen.getByRole("button", { name: /giriş yap/i }));

    await waitFor(() => expect(alert).toHaveBeenCalledWith(GENERIC_ERROR));
    expect(onLogin).not.toHaveBeenCalled();
  });
});
