import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import axios from "axios";
import { ChefHat, Info, LogIn, Plus, X } from "lucide-react";
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
  sourceName?: string;
  sourceAuthor?: string;
  sourceUrl?: string;
  sourcePublishedAt?: string;
  sourceRightsStatus?: string;
};
type FeaturedRecipe = {
  id: string;
  title: string;
  description: string;
  category: string;
  sourceName: string;
  sourceAuthor: string;
  sourceUrl: string;
  sourcePublishedAt: string;
  sourceRightsStatus?: string;
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
  const [busy, setBusy] = useState(false);
  const [image, setImage] = useState<File | null>(null);
  const [feedback, setFeedback] = useState<any>(null);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [featured, setFeatured] = useState<FeaturedRecipe[]>([]);
  const [infoRecipe, setInfoRecipe] = useState<FeaturedRecipe | Recipe | null>(null);

  useEffect(() => {
    if (!user) return;
    api.get("/featured-recipes")
      .then(({ data }) => setFeatured(data))
      .catch((error) => console.error("[KitchenAI Web] featured recipes failed", error));
  }, [user]);

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


  const openFeatured = async (item: FeaturedRecipe) => {
    setBusy(true);
    try {
      const { data } = await api.post("/recipes/generate", { desiredFood: item.title });
      setRecipe({
        ...data,
        sourceName: item.sourceName,
        sourceAuthor: item.sourceAuthor,
        sourceUrl: item.sourceUrl,
        sourcePublishedAt: item.sourcePublishedAt,
        sourceRightsStatus: item.sourceRightsStatus,
        servings: 0,
        preparationTime: 0,
        cookingTime: 0,
      });
    } catch (error) {
      console.error("[KitchenAI Web] featured recipe failed", error);
      window.alert(GENERIC_ERROR);
    } finally {
      setBusy(false);
    }
  };

  const recover = async () => {
    if (!image || !recoveryRecipeName.trim() || !recoveryProblem.trim()) return;
    setBusy(true);
    try {
      const session = (
        await api.post("/cooking-sessions", { desiredOutcome: "İdeal kıvam" })
      ).data;
      const form = new FormData();
      form.append("image", image);
      form.append("recipeName", recoveryRecipeName.trim());
      form.append("problemDescription", recoveryProblem.trim());
      form.append("desiredOutcome", "Tarifteki sorunu gider");
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
            <h2>Tarifime yardım et</h2>
            <p>Ürünün fotoğrafını yükle, adını ve yaşadığın sorunu anlat. Önce fotoğraf ve açıklamayı birlikte değerlendireceğiz. Görsel analiz başarısız olursa açıklamanı tek başına kullanarak öneri üretmeye çalışacağız.</p>
            <input
              required
              maxLength={200}
              value={recoveryRecipeName}
              onChange={(e) => setRecoveryRecipeName(e.target.value)}
              placeholder="Ürünün / tarifin adı"
            />
            <textarea
              required
              maxLength={1000}
              value={recoveryProblem}
              onChange={(e) => setRecoveryProblem(e.target.value)}
              placeholder="Sorun ne? Örn. Kekimin içi pişmedi ama dışı fazla kızardı."
              rows={4}
            />
            <input
              required
              type="file"
              accept="image/jpeg,image/png"
              onChange={(e) => setImage(e.target.files?.[0] || null)}
            />
            <button
              onClick={recover}
              disabled={!image || !recoveryRecipeName.trim() || !recoveryProblem.trim() || busy}
            >
              {busy ? "Tarif değerlendiriliyor..." : "Yardım al"}
            </button>
            {feedback && (
              <div className="feedback">
                <h3>{feedback.message}</h3>
                {feedback.textAdvice?.advice && <p>{feedback.textAdvice.advice}</p>}
                {feedback.textAdvice?.caution && <p>{feedback.textAdvice.caution}</p>}
                {feedback.action?.instruction && <p>{feedback.action.instruction}</p>}
              </div>
            )}
          </section>
        </div>
        <section className="card recipe">
          <p className="eyebrow">KAYNAKLI SABİT TARİFLER</p>
          <h2>Sabit Tarifler</h2>
          <p>Yayımlanmış tarifler. Her tarifin orijinal kaynağı kayıtlıdır.</p>
          <div className="grid">
            {featured.map((item) => (
              <article className="card action" key={item.id}>
                <p className="eyebrow">{item.category.replaceAll("_", " ")}</p>
                <h3>{item.title}</h3>
                <p>{item.description}</p>
                <div className="recipe-card-meta">
                  <small>Kaynak: {item.sourceName} · {item.sourceAuthor}</small>
                  <button className="info-button" type="button" aria-label={`${item.title} kaynak ve güvence bilgisi`} title="Kaynak ve güvence bilgisi" onClick={() => setInfoRecipe(item)}>
                    <Info size={17} /> Bilgi
                  </button>
                </div>
                <button onClick={() => openFeatured(item)} disabled={busy}>
                  {busy ? "Açılıyor..." : "Tarifi yap"}
                </button>
              </article>
            ))}
          </div>
        </section>
        {recipe && (
          <section className="card recipe">
            <p className="eyebrow">
              {recipe.sourceUrl ? "SABİT TARİF" : "ÖNERİLEN TARİF"} · {recipe.sourceType || "UNKNOWN"}
            </p>
            <div className="recipe-title-row">
              <h2>{recipe.title}</h2>
              {recipe.sourceUrl && <button className="info-button" type="button" onClick={() => setInfoRecipe(recipe)}><Info size={17} /> Kaynak bilgisi</button>}
            </div>
            <p>{recipe.description}</p>
            {(recipe.preparationTime > 0 || recipe.cookingTime > 0 || recipe.servings > 0) && (
              <p>
                {recipe.preparationTime > 0 ? `${recipe.preparationTime} dk hazırlık` : ""}
                {recipe.cookingTime > 0 ? ` · ${recipe.cookingTime} dk pişirme` : ""}
                {recipe.servings > 0 ? ` · ${recipe.servings} porsiyon` : ""}
              </p>
            )}
            {recipe.sourceUrl && (
              <p>
                Kaynak: {recipe.sourceName} · {recipe.sourceAuthor}
                {recipe.sourcePublishedAt ? ` · ${recipe.sourcePublishedAt}` : ""}
                {" · "}<a href={recipe.sourceUrl} target="_blank" rel="noreferrer">Orijinal tarifi aç</a>
              </p>
            )}
            <h3>Malzemeler</h3>
            <ul>
              {JSON.parse(recipe.ingredients).map((item: any, index: number) => (
                <li key={`ingredient-${index}`}>
                  {item.quantity > 0 ? `${item.quantity} ${item.unit}` : item.unit} {item.name}
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
        {infoRecipe && (
          <div className="info-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setInfoRecipe(null); }}>
            <section className="info-dialog" role="dialog" aria-modal="true" aria-labelledby="recipe-info-title">
              <div className="info-dialog-heading">
                <div><p className="eyebrow">ŞEFFAFLIK VE KAYNAK</p><h2 id="recipe-info-title">Tarif bilgisi</h2></div>
                <button className="info-close" type="button" aria-label="Bilgi penceresini kapat" onClick={() => setInfoRecipe(null)}><X size={20} /></button>
              </div>
              <h3>{infoRecipe.title}</h3>
              <dl className="info-facts">
                <div><dt>Yayıncı / kaynak</dt><dd>{infoRecipe.sourceName || "Belirtilmemiş"}</dd></div>
                <div><dt>Tarif sahibi / yazar</dt><dd>{infoRecipe.sourceAuthor || "Kaynakta belirtilmemiş"}</dd></div>
                {"sourcePublishedAt" in infoRecipe && infoRecipe.sourcePublishedAt && <div><dt>Yayın tarihi</dt><dd>{infoRecipe.sourcePublishedAt}</dd></div>}
                {"sourceRightsStatus" in infoRecipe && <div><dt>Hak durumu</dt><dd>{infoRecipe.sourceRightsStatus === "CHECK_REQUIRED" ? "Kontrol gerekli" : infoRecipe.sourceRightsStatus || "Belirtilmemiş"}</dd></div>}
              </dl>
              <div className="info-note">
                <strong>Kaynak ve doğrulama hakkında</strong>
                <p>Bu tarif, belirtilen yayıncının orijinal tarif kaynağı esas alınarak KitchenAI tarif formatına aktarılmıştır. Orijinal malzeme ve yöntem bilgileri için kaynak sayfayı inceleyebilirsin.</p>
                <p><b>Kaynak gösterimi, yeniden yayımlama izni anlamına gelmez.</b> İçeriğin kullanım ve lisans hakları ayrıca kontrol edilmelidir.</p>
                <p>Kaynağın kayıtlı olması, tarifin KitchenAI tarafından bağımsız biçimde test edildiği veya sonucun garanti edildiği anlamına gelmez. Miktarlar, ekipman ve pişirme koşulları sonucu etkileyebilir.</p>
              </div>
              {infoRecipe.sourceUrl && <a className="source-link" href={infoRecipe.sourceUrl} target="_blank" rel="noreferrer">Orijinal kaynak sayfasını aç ↗</a>}
            </section>
          </div>
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
