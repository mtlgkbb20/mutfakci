import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import axios from "axios";
import { ChefHat, LogIn, Plus, Crown, Sparkles } from "lucide-react";
import "./styles.css";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || "http://localhost:8080/api/v1",
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

type MembershipPlan = {
  code: string;
  available: boolean;
  price: number;
  autoRenew: boolean;
};
type Membership = {
  tier: string;
  billingInterval: string;
  remaining: number;
  limit: number;
  usage: number;
  period: string;
  paidAccess: boolean;
  plans: MembershipPlan[];
};
type Recipe = {
  id: string;
  title: string;
  description: string;
  servings: number;
  preparationTime: number;
  cookingTime: number;
  ingredients: string;
  stages: string;
  sourceType?: string;
};

const GENERIC_ERROR =
  "Beklenmedik bir hata oluştu. Lütfen support ile iletişime geçin.";

function Auth({ onLogin }: { onLogin: (user: any) => void }) {
  const [register, setRegister] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    try {
      const { data } = await api.post(
        `/auth/${register ? "register" : "login"}`,
        register ? { email, password, displayName: name } : { email, password },
      );
      localStorage.setItem("token", data.token);
      onLogin(data.user);
    } catch (error) {
      console.error("[KitchenAI Web] authentication failed", error);
      window.alert(GENERIC_ERROR);
    }
  };

  return (
    <main className="center">
      <section className="card auth">
        <ChefHat size={42} />
        <h1>Kitchen AI</h1>
        <p>İyi sonuç için mutfağında akıllı bir yardımcı.</p>
        <form onSubmit={submit}>
          {register && (
            <input
              required
              placeholder="Adın"
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          )}
          <input
            required
            type="email"
            placeholder="E-posta"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
          <input
            required
            minLength={8}
            type="password"
            placeholder="Şifre"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          <button>
            <LogIn size={18} />
            {register ? "Kayıt ol" : "Giriş yap"}
          </button>
        </form>
        <button className="link" onClick={() => setRegister(!register)}>
          {register ? "Zaten hesabım var" : "Hesap oluştur"}
        </button>
      </section>
    </main>
  );
}

function App() {
  const [user, setUser] = useState<any>(null);
  const [recipe, setRecipe] = useState<Recipe | null>(null);
  const [food, setFood] = useState("");
  const [busy, setBusy] = useState(false);
  const [image, setImage] = useState<File | null>(null);
  const [feedback, setFeedback] = useState<any>(null);
  const [membership, setMembership] = useState<Membership | null>(null);
  const [checkoutBusy, setCheckoutBusy] = useState(false);
  const [checkoutAddress, setCheckoutAddress] = useState("");
  const [checkoutPhone, setCheckoutPhone] = useState("");

  const refreshMembership = async () => {
    const { data } = await api.get("/membership");
    setMembership(data);
  };

  useEffect(() => {
    if (!user) return;
    refreshMembership().catch((error) =>
      console.error("Membership load failed", error),
    );

    const params = new URLSearchParams(window.location.search);
    const paymentResult = params.get("payment");
    const orderId = params.get("order");
    if (paymentResult && orderId) {
      api.get(`/membership/checkouts/${encodeURIComponent(orderId)}`)
        .then(async ({ data }) => {
          await refreshMembership();
          window.alert(
            data.status === "PAID"
              ? "Ödeme doğrulandı. Cooker üyeliğin aktif."
              : paymentResult === "failed"
                ? "Ödeme tamamlanmadı."
                : "Ödeme sonucu henüz doğrulanmadı. Biraz sonra profilini yenile.",
          );
        })
        .catch((error) => console.error("Payment status check failed", error))
        .finally(() => {
          window.history.replaceState({}, "", window.location.pathname);
        });
    }
  }, [user]);

  const plan = (code: string) =>
    membership?.plans?.find((item) => item.code === code);

  const startCheckout = async (planCode: string) => {
    if (!checkoutAddress.trim() || !checkoutPhone.trim()) {
      window.alert("Ödeme için adres ve telefon bilgisi gir.");
      return;
    }
    const checkoutWindow = window.open("about:blank", "_blank");
    if (!checkoutWindow) {
      window.alert("Ödeme penceresi tarayıcı tarafından engellendi.");
      return;
    }
    setCheckoutBusy(true);
    try {
      const { data } = await api.post("/membership/checkout", {
        planCode,
        displayName: user.displayName,
        address: checkoutAddress,
        phone: checkoutPhone,
      });
      checkoutWindow.location.href = data.checkoutUrl;
    } catch (error: any) {
      checkoutWindow.close();
      window.alert(error?.response?.data?.error || GENERIC_ERROR);
    } finally {
      setCheckoutBusy(false);
    }
  };

  if (!localStorage.getItem("token") || !user) {
    return <Auth onLogin={setUser} />;
  }

  const generate = async () => {
    if (!food.trim()) return;
    setBusy(true);
    try {
      const { data } = await api.post("/recipes/generate", {
        desiredFood: food,
        servings: 4,
      });
      setRecipe(data);
      await refreshMembership();
    } catch (error: any) {
      console.error("[KitchenAI Web] recipe generation failed", error);
      window.alert(error?.response?.data?.error || GENERIC_ERROR);
    } finally {
      setBusy(false);
    }
  };

  const recover = async () => {
    if (!image) return;
    setBusy(true);
    try {
      const session = (
        await api.post("/cooking-sessions", { desiredOutcome: "İdeal kıvam" })
      ).data;
      const form = new FormData();
      form.append("image", image);
      form.append("desiredOutcome", "İdeal kıvam");
      const { data } = await api.post(
        `/cooking-sessions/${session.id}/observations`,
        form,
      );
      setFeedback(data);
    } catch (error) {
      console.error("[KitchenAI Web] recovery failed", error);
      window.alert(GENERIC_ERROR);
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <header>
        <ChefHat />
        <strong>Kitchen AI</strong>
        <span>Merhaba, {user.displayName}</span>
        <button
          className="link"
          onClick={() => {
            localStorage.removeItem("token");
            setUser(null);
          }}
        >
          Çıkış
        </button>
      </header>
      <main className="container">
        <div className="hero">
          <div>
            <p className="eyebrow">MUTFAKTAKİ YARDIMCIN</p>
            <h1>
              Bugün ne pişirmek
              <br />
              istersin?
            </h1>
            <p>
              İstediğin sonucu anlat, sana uygun bir yol haritası hazırlayalım.
            </p>
          </div>
          <ChefHat size={100} />
        </div>
        {membership && (
          <section className="card membership">
            <div className="membership-top">
              <div>
                <p className="eyebrow">ÜYELİĞİN</p>
                <h2>
                  {membership.tier === "FREE"
                    ? "Free üyelik"
                    : membership.tier === "COOKER"
                      ? "Cooker üyeliği"
                      : "Chef"}
                </h2>
                <p>
                  {membership.remaining} / {membership.limit} AI tarif hakkın
                  kaldı · {membership.period === "LIFETIME" ? "ömür boyu" : "bugün"}
                </p>
              </div>
              <Crown size={34} />
            </div>
            <div className="quota-track">
              <span
                style={{
                  width: `${Math.min(100, (membership.usage / Math.max(1, membership.limit)) * 100)}%`,
                }}
              />
            </div>
            <div className="plan-grid">
              <article className="plan-card current">
                <span className="plan-label">MEVCUT</span>
                <h3>Free</h3>
                <strong>3 tarif</strong>
                <p>Hesabın boyunca toplam 3 AI tarif üretimi.</p>
              </article>
              <article className="plan-card">
                <span className="plan-label">TEK ÖDEME · OTOMATİK YENİLEME YOK</span>
                <h3>Cooker Monthly</h3>
                <strong>10 tarif / gün</strong>
                <p>Ödeme sonrası 30 gün boyunca günlük 10 AI tarif üretimi.</p>
                <strong>{plan("COOKER_MONTHLY")?.price ?? "—"} ₺</strong>
                <button
                  disabled={!plan("COOKER_MONTHLY")?.available || checkoutBusy}
                  onClick={() => startCheckout("COOKER_MONTHLY")}
                >
                  {checkoutBusy ? "Hazırlanıyor..." : "PayTR ile öde"}
                </button>
              </article>
              <article className="plan-card">
                <span className="plan-label">TEK ÖDEME · OTOMATİK YENİLEME YOK</span>
                <h3>Cooker Yearly</h3>
                <strong>10 tarif / gün</strong>
                <p>Ödeme sonrası 365 gün boyunca günlük 10 AI tarif üretimi.</p>
                <strong>{plan("COOKER_YEARLY")?.price ?? "—"} ₺</strong>
                <button
                  disabled={!plan("COOKER_YEARLY")?.available || checkoutBusy}
                  onClick={() => startCheckout("COOKER_YEARLY")}
                >
                  {checkoutBusy ? "Hazırlanıyor..." : "PayTR ile öde"}
                </button>
              </article>
              <article className="plan-card chef-plan">
                <span className="plan-label">COMING SOON</span>
                <h3><Sparkles size={18} /> Chef</h3>
                <strong>Kişisel mutfak asistanı</strong>
                <p>Detaylar yakında açıklanacak.</p>
              </article>
            </div>
            <div className="checkout-details">
              <p>PayTR ödeme formu için iletişim bilgileri</p>
              <input
                value={checkoutAddress}
                onChange={(event) => setCheckoutAddress(event.target.value)}
                placeholder="Adres"
              />
              <input
                value={checkoutPhone}
                onChange={(event) => setCheckoutPhone(event.target.value)}
                placeholder="Telefon"
                type="tel"
              />
              <small>
                Test ortamında ödeme alınmaz. Cooker paketi tek ödemeyle 30/365 gün
                aktif olur; otomatik yenileme bu test entegrasyonunda yoktur.
              </small>
            </div>
          </section>
        )}
        <div className="grid">
          <section className="card action">
            <Plus />
            <h2>Yeni tarif öner</h2>
            <p>Nasıl bir yemek istediğini anlat.</p>
            <input
              value={food}
              onChange={(event) => setFood(event.target.value)}
              placeholder="Örn. yumuşak elmalı kek"
            />
            <button onClick={generate} disabled={busy}>
              {busy ? "Tarif hazırlanıyor..." : "Tarif oluştur"}
            </button>
          </section>
          <section className="card action">
            <h2>Tarifimi kurtar</h2>
            <p>Fotoğraf yükle, mevcut durum için hızlı öneri al.</p>
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp"
              onChange={(event) => setImage(event.target.files?.[0] || null)}
            />
            <button onClick={recover} disabled={!image || busy}>
              {busy ? "Fotoğraf inceleniyor..." : "Fotoğrafı değerlendir"}
            </button>
            {feedback && (
              <div className="feedback">
                <h3>{feedback.message}</h3>
              </div>
            )}
          </section>
        </div>
        {recipe && (
          <section className="card recipe">
            <p className="eyebrow">
              ÖNERİLEN TARİF · {recipe.sourceType || "UNKNOWN"}
            </p>
            <h2>{recipe.title}</h2>
            <p>{recipe.description}</p>
            <p>
              {recipe.preparationTime} dk hazırlık · {recipe.cookingTime} dk
              pişirme · {recipe.servings} porsiyon
            </p>
            <h3>Malzemeler</h3>
            <ul>
              {JSON.parse(recipe.ingredients).map((item: any, index: number) => (
                <li key={`ingredient-${index}`}>
                  {item.quantity} {item.unit} {item.name}
                </li>
              ))}
            </ul>
            <h3>Aşamalar</h3>
            {JSON.parse(recipe.stages).map((stage: any, index: number) => (
              <p key={`stage-${index}`}>
                <b>
                  {stage.order}. {stage.title}:
                </b>{" "}
                {stage.instructions}
              </p>
            ))}
          </section>
        )}
      </main>
    </>
  );
}

createRoot(document.getElementById("root")!).render(
  <BrowserRouter>
    <App />
  </BrowserRouter>,
);
