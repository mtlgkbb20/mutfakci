import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import axios from "axios";
import { ChefHat, LogIn, Plus } from "lucide-react";
import "./styles.css";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || "http://localhost:8080/api/v1",
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

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
              onChange={(e) => setName(e.target.value)}
            />
          )}
          <input
            required
            type="email"
            placeholder="E-posta"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <input
            required
            minLength={8}
            type="password"
            placeholder="Şifre"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
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


type RecoveryHistoryItem = {
  id: string;
  status: string;
  componentKey?: string;
  problemDescription?: string;
  statusText?: string;
  recipeName?: string;
  createdAt: string;
  hasImage: boolean;
};

function RecoveryHistory({ onClose }: { onClose: () => void }) {
  const [items, setItems] = useState<RecoveryHistoryItem[]>([]);
  const [selected, setSelected] = useState<any>(null);
  const [imageUrl, setImageUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    api.get("/recovery-history")
      .then(({ data }) => setItems(data))
      .catch((e) => {
        console.error("[KitchenAI Web] recovery history failed", e);
        setError("Yardım geçmişi şu anda yüklenemiyor.");
      });
  }, []);

  useEffect(() => {
    let objectUrl: string | null = null;
    if (selected?.hasImage) {
      api.get(`/recovery-history/${selected.id}/image`, { responseType: "blob" })
        .then(({ data }) => {
          objectUrl = URL.createObjectURL(data);
          setImageUrl(objectUrl);
        })
        .catch(() => setImageUrl(null));
    } else {
      setImageUrl(null);
    }
    return () => { if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [selected?.id]);

  const open = async (id: string) => {
    setLoading(true);
    setError("");
    try {
      const { data } = await api.get(`/recovery-history/${id}`);
      setSelected(data);
    } catch (e) {
      console.error("[KitchenAI Web] recovery detail failed", e);
      setError("Yardım kaydı açılamadı.");
    } finally {
      setLoading(false);
    }
  };

  const reuse = async () => {
    if (!selected) return;
    try {
      const { data } = await api.post(`/recovery-history/${selected.id}/reuse`);
      setSelected(data);
    } catch (e) {
      console.error("[KitchenAI Web] recovery reuse failed", e);
      setError("Önceki tavsiye şu anda açılamıyor.");
    }
  };

  const pretty = (value: string | null | undefined) => {
    if (!value) return "";
    try { return JSON.stringify(JSON.parse(value), null, 2); }
    catch { return value; }
  };

  return (
    <section className="card recipe">
      <div className="history-heading">
        <h2>Yardım geçmişim</h2>
        <button className="link" onClick={onClose}>Kapat</button>
      </div>
      {error && <p role="alert">{error}</p>}
      <div className="history-layout">
        <div className="history-list">
          {items.length === 0 && !error && <p>Henüz kayıtlı yardımın yok.</p>}
          {items.map((item) => (
            <button className="history-item" key={item.id} onClick={() => open(item.id)}>
              <strong>{item.recipeName || item.componentKey || "Tarif yardımı"}</strong>
              <span>{new Date(item.createdAt).toLocaleString("tr-TR")}</span>
              <small>{item.status}{item.hasImage ? " · Fotoğraflı" : " · Metinle"}</small>
            </button>
          ))}
        </div>
        <div className="history-detail">
          {loading && <p>Kayıt yükleniyor...</p>}
          {!selected && !loading && <p>Detaylarını görmek için bir yardım kaydı seç.</p>}
          {selected && (
            <>
              <h3>{selected.recipeName || selected.componentKey || "Tarif yardımı"}</h3>
              <p>{selected.status} · {new Date(selected.createdAt).toLocaleString("tr-TR")}</p>
              {imageUrl && <img className="history-image" src={imageUrl} alt="Yardım isteğinde gönderilen yemek fotoğrafı" />}
              {selected.problemDescription && <p><b>Problem:</b> {selected.problemDescription}</p>}
              {selected.statusText && <p><b>Durum:</b> {selected.statusText}</p>}
              {selected.assessment && <><h4>Değerlendirme / Tavsiye</h4><pre>{pretty(selected.assessment)}</pre></>}
              {selected.action && <><h4>Önerilen işlem</h4><pre>{pretty(selected.action)}</pre></>}
              <button onClick={reuse}>Bu tavsiyeyi tekrar aç</button>
              {selected.notice && <p>{selected.notice}</p>}
              {selected.executions?.length > 0 && (
                <details>
                  <summary>AI çağrı kayıtları ({selected.executions.length})</summary>
                  {selected.executions.map((execution: any, index: number) => (
                    <div key={index}>
                      <b>{execution.operation}</b> · {execution.model} · {execution.status}
                      {execution.errorMessage && <p>{execution.errorMessage}</p>}
                    </div>
                  ))}
                </details>
              )}
            </>
          )}
        </div>
      </div>
    </section>
  );
}

function App() {
  const [user, setUser] = useState<any>(null);
  const [recipe, setRecipe] = useState<Recipe | null>(null);
  const [food, setFood] = useState("");
  const [recoveryRecipeName, setRecoveryRecipeName] = useState("");
  const [recoveryProblem, setRecoveryProblem] = useState("");
  const [textAdvice, setTextAdvice] = useState<any>(null);
  const [busy, setBusy] = useState(false);
  const [image, setImage] = useState<File | null>(null);
  const [feedback, setFeedback] = useState<any>(null);
  const [historyOpen, setHistoryOpen] = useState(false);

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
    } catch (error) {
      console.error("[KitchenAI Web] recipe generation failed", error);
      window.alert(GENERIC_ERROR);
    } finally {
      setBusy(false);
    }
  };


  const recoverByText = async () => {
    if (!recoveryRecipeName.trim() || !recoveryProblem.trim()) return;
    setBusy(true);
    setTextAdvice(null);
    try {
      const session = (await api.post("/cooking-sessions", {
        desiredOutcome: "Tarif sorununu metinle çöz"
      })).data;
      const { data } = await api.post(
        `/cooking-sessions/${session.id}/recovery-attempts/text-fallback`,
        { recipeName: recoveryRecipeName.trim(), problemDescription: recoveryProblem.trim() },
      );
      setTextAdvice(data);
    } catch (error) {
      console.error("[KitchenAI Web] text recovery failed", error);
      window.alert(GENERIC_ERROR);
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
        `/cooking-sessions/${session.id}/recovery-attempts`,
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
        <button className="link" onClick={() => setHistoryOpen(!historyOpen)}>Yardım geçmişim</button>
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
        {historyOpen && <RecoveryHistory onClose={() => setHistoryOpen(false)} />}
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
        <div className="grid">
          <section className="card action">
            <Plus />
            <h2>Yeni tarif öner</h2>
            <p>Nasıl bir yemek istediğini anlat.</p>
            <input
              value={food}
              onChange={(e) => setFood(e.target.value)}
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
              accept="image/jpeg,image/png"
              onChange={(e) => setImage(e.target.files?.[0] || null)}
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
