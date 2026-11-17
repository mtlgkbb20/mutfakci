const { chromium } = require("playwright");
const fs = require("node:fs");
const path = require("node:path");

(async () => {
  const output = path.join(__dirname, "..", "screenshots", "ui-refresh");
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 2,
    isMobile: true,
    hasTouch: true,
  });

  await page.addInitScript(() => {
    localStorage.setItem("token", "ui-preview-token");
    localStorage.setItem("user", JSON.stringify({
      id: "preview-user",
      displayName: "Mutfak Dostu",
      email: "preview@kitchenai.local"
    }));
  });

  await page.route("**/api/v1/**", async route => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith("/recipes")) {
      return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify([
        { id: "preview-recipe-1", title: "Havuçlu Tarçınlı Kek", description: "Yumuşacık, dengeli ve mis kokulu", servings: 4, preparationTime: 15, cookingTime: 35, ingredients: "Havuç, un, tarçın", stages: "Karıştır ve pişir" }
      ]) });
    }
    if (url.pathname.endsWith("/gamification/progress")) {
      return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ totalXp: 45, level: "HOME_COOK" }) });
    }
    return route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
  });

  const base = "http://127.0.0.1:4173";
  await page.goto(base + "/dashboard", { waitUntil: "networkidle" });
  await page.getByText("Nereden başlayalım?").waitFor({ timeout: 30000 });
  await page.screenshot({ path: path.join(output, "01-dashboard.png"), fullPage: true });

  await page.getByText("Tarif öner", { exact: true }).first().click();
  await page.getByText("Bugün ne pişirelim?").waitFor();
  await page.screenshot({ path: path.join(output, "02-tarif-oner.png"), fullPage: true });

  await page.getByText("Tarifimi kurtar", { exact: true }).first().click();
  await page.getByText("Bir şey planladığın gibi gitmedi mi?").waitFor();
  await page.screenshot({ path: path.join(output, "03-tarifimi-kurtar-kart.png"), fullPage: true });

  await page.goto(base + "/recovery", { waitUntil: "networkidle" });
  await page.getByText("Tarifin adını yazmalısın.").waitFor({ state: "detached", timeout: 1500 }).catch(() => {});
  await page.screenshot({ path: path.join(output, "04-tarif-kurtarma-ekrani.png"), fullPage: true });

  await browser.close();
  console.log("Captured four Expo web screenshots:", output);
})().catch(error => {
  console.error(error);
  process.exit(1);
});
