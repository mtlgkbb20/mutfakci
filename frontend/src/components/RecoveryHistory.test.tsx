import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RecoveryHistory } from "./RecoveryHistory";
import { api } from "../api";
import { httpError, routeApi } from "../test/apiMock";

vi.mock("../api", () => ({ api: { get: vi.fn(), post: vi.fn() } }));

const item = { id: "h1", status: "COMPLETED", recipeName: "Kek", createdAt: "2026-11-20T10:00:00Z", hasImage: false };
const detail = { ...item, problemDescription: "İçi pişmedi", assessment: '{"tip":"Fırını kıs"}', executions: [] };

describe("RecoveryHistory", () => {
  beforeEach(() => vi.spyOn(console, "error").mockImplementation(() => {}));

  it("shows the empty state when there is no history", async () => {
    routeApi(api, { get: { "/recovery-history": () => [] } });
    render(<RecoveryHistory onClose={vi.fn()} />);
    expect(await screen.findByText("Henüz kayıtlı yardımın yok.")).toBeInTheDocument();
  });

  it("shows an error instead of the empty state when the list fails", async () => {
    routeApi(api, { get: { "/recovery-history": () => { throw httpError(500); } } });
    render(<RecoveryHistory onClose={vi.fn()} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Yardım geçmişi şu anda yüklenemiyor.");
    expect(screen.queryByText("Henüz kayıtlı yardımın yok.")).not.toBeInTheDocument();
  });

  it("opens a record and pretty-prints its assessment", async () => {
    routeApi(api, { get: { "/recovery-history": () => [item], "/recovery-history/h1": () => detail } });
    render(<RecoveryHistory onClose={vi.fn()} />);

    await userEvent.click(await screen.findByRole("button", { name: /Kek/ }));

    expect(await screen.findByText("İçi pişmedi")).toBeInTheDocument();
    expect(screen.getByText(/"tip": "Fırını kıs"/)).toBeInTheDocument();
    expect(api.get).not.toHaveBeenCalledWith("/recovery-history/h1/image", expect.anything());
  });

  it("reports a failure to open a record", async () => {
    routeApi(api, { get: { "/recovery-history": () => [item], "/recovery-history/h1": () => { throw httpError(404); } } });
    render(<RecoveryHistory onClose={vi.fn()} />);
    await userEvent.click(await screen.findByRole("button", { name: /Kek/ }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Yardım kaydı açılamadı.");
  });

  it("re-opens previous advice and reports a reuse failure", async () => {
    routeApi(api, {
      get: { "/recovery-history": () => [item], "/recovery-history/h1": () => detail },
      post: { "/recovery-history/h1/reuse": () => { throw httpError(500); } },
    });
    render(<RecoveryHistory onClose={vi.fn()} />);
    await userEvent.click(await screen.findByRole("button", { name: /Kek/ }));
    await userEvent.click(await screen.findByRole("button", { name: "Bu tavsiyeyi tekrar aç" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Önceki tavsiye şu anda açılamıyor.");
  });

  it("calls onClose when closed", async () => {
    routeApi(api, { get: { "/recovery-history": () => [] } });
    const onClose = vi.fn();
    render(<RecoveryHistory onClose={onClose} />);
    await userEvent.click(screen.getByRole("button", { name: "Kapat" }));
    await waitFor(() => expect(onClose).toHaveBeenCalledOnce());
  });
});
