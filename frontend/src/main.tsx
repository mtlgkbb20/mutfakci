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
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

type Membership = {
  tier: string;
  billingInterval: string;
  remaining: number;
  limit: number;
  usage: number;
  period: string;
  paidAccess: boolean;
  plans: Array<{
    code: string;
    available: boolean;
    comingSoon?: boolean;
  }>;
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
  const [verifying, setVerifying] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [code, setCode] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError("");
    setNotice("");
    setLoading(true);

    try {
      if (verifying) {
        const { data } = await api.post("/auth/verify-email", { email, code });
        localStorage.setItem("token", data.token);
        onLogin(data.user);
      } else if (register) {
        const { data } = await api.post("/auth/register", {
          email,
          password,
          displayName: name,
        });
        setVerifying(true);
        setNotice(
          data.message ||
            "Doğrulama e-postası gönderildiyse gelen kutunu kontrol et.",
        );
      } else {
        const { data } = await api.post("/auth/login", { email, password });
        localStorage.setItem("token", data.token);
        onLogin(data.user);
      }
    } catch (requestError: any) {
      setError(
        requestError?.response?.data?.error ||
          "İşlem tamamlanamadı. Bilgilerini kontrol edip tekrar dene.",
      );
    } finally {
      setLoading(false);
    }
  };

  const resend = async () => {
    setError("");
    setNotice("");
    setLoading(true);

    try {
      const { data } = await api.post("/auth/resend-verification", { email });
      setNotice(data.message || "İstek alındı. E-posta adresini kontrol et.");
    } catch (requestError: any) {
      setError(
        requestError?.response?.data?.error || "İstek şu anda tamamlanamadı.",
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="center">
      <section className="card auth">
        <ChefHat size={42} />
        <h1>Kitchen AI</h1>
        <p>
          {verifying
            ? "E-posta adresine gönderilen 6 haneli kodu gir."
            : "İyi sonuç için mutfağında akıllı bir yardımcı."}
        </p>

        <form onSubmit={submit}>
          {!verifying && register && (
            <input
              required
              maxLength={120}
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
            disabled={verifying}
          />

          {!verifying && (
            <input
              required
              minLength={12}
              maxLength={128}
              type="password"
              placeholder="Şifre (en az 12 karakter)"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          )}

          {verifying && (
            <input
              required
              inputMode="numeric"
              pattern="[0-9]{6}"
              maxLength={6}
              placeholder="6 haneli doğrulama kodu"
              value={code}
              onChange={(event) =>
                setCode(event.target.value.replace(/\\D/g, "").slice(0, 6))
              }
            />
          )}

          {error && (
            <p role="alert" className="error">
              {error}
            </p>
          )}
          {notice && <p role="status">{notice}</p>}

          <button disabled={loading}>
            {loading
              ? "Lütfen bekle..."
              : verifying
                ? "E-postayı doğrula"
                : register
                  ? "Kayıt ol"
                  : "Giriş yap"}
          </button>
        </form>

        {verifying ? (
          <>
            <button
              className="link"
              type="button"
              disabled={loading}
              onClick={resend}
            >
              Yeni kod gönder
            </button>
            <button
              className="link"
              type="button"
              onClick={() => {
                setVerifying(false);
                setRegister(false);
                setCode("");
                setError("");
              }}
            >
              Giriş ekranına dön
            </button>
          </>
        ) : (
          <button
            className="link"
            type="button"
            onClick={() => {
              setRegister(!register);
              setError("");
              setNotice("");
            }}
          >
            {register ? "Zaten hesabım var" : "Hesap oluştur"}
          </button>
        )}
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
  const [gamification, setGamification] = useState<any>(null);
  const [leaderboard, setLeaderboard] = useState<any[]>([]);
  const [events, setEvents] = useState<any[]>([]);
  const [adminSubmissions, setAdminSubmissions] = useState<Record<string, any[]>>({});
  const [eventTitle, setEventTitle] = useState("");
  const [eventTheme, setEventTheme] = useState("");
  const [eventDescription, setEventDescription] = useState("");
  const [eventStart, setEventStart] = useState("");
  const [submissionOpen, setSubmissionOpen] = useState("");
  const [submissionClose, setSubmissionClose] = useState("");
  const [votingOpen, setVotingOpen] = useState("");
  const [votingClose, setVotingClose] = useState("");

  useEffect(() => {
    if (!user) return;
    api.get("/membership").then(({ data }) => setMembership(data)).catch((error) => console.error("Membership load failed", error));
  }, [user]);

  const refreshMembership = async () => {
    const { data } = await api.get("/membership");
    setMembership(data);
  };

  useEffect(() => {
    if (!user) return;
    Promise.allSettled([
      api.get("/gamification/me"),
      api.get("/gamification/leaderboard/current"),
      api.get("/events"),
    ]).then(([progressResult, leaderboardResult, eventsResult]) => {
      if (progressResult.status === "fulfilled") setGamification(progressResult.value.data);
      if (leaderboardResult.status === "fulfilled") setLeaderboard(leaderboardResult.value.data.entries || []);
      if (eventsResult.status === "fulfilled") setEvents(eventsResult.value.data || []);
    });
  }, [user]);

  const createEvent = async (event: React.FormEvent) => {
    event.preventDefault();
    try {
      await api.post("/events/admin", {
        title: eventTitle, theme: eventTheme, description: eventDescription,
        startsAt: new Date(eventStart).toISOString(),
        submissionOpensAt: new Date(submissionOpen).toISOString(),
        submissionClosesAt: new Date(submissionClose).toISOString(),
        votingOpensAt: new Date(votingOpen).toISOString(),
        votingClosesAt: new Date(votingClose).toISOString(), maxImages: 4,
      });
      window.alert("Etkinlik oluşturuldu.");
      setEventTitle(""); setEventTheme(""); setEventDescription("");
      const { data } = await api.get("/events"); setEvents(data);
    } catch (error:any) { window.alert(error?.response?.data?.error || "Etkinlik oluşturulamadı."); }
  };

  const loadAdminSubmissions = async (eventId:string) => {
    try {
      const {data}=await api.get(`/events/admin/${eventId}/submissions`);
      const withImages=await Promise.all(data.map(async (submission:any)=>{
        const imageIds=Array.isArray(submission.imageids)?submission.imageids:Array.isArray(submission.imageIds)?submission.imageIds:[];
        const images=await Promise.all(imageIds.map(async (id:string)=>{
          const response=await api.get(`/events/admin/${eventId}/images/${id}`,{responseType:"blob"});
          return URL.createObjectURL(response.data);
        }));
        return {...submission,images};
      }));
      setAdminSubmissions(current=>({...current,[eventId]:withImages}));
    }
    catch(error) { console.error("Admin submissions load failed",error); }
  };

  const adminEventAction = async (eventId:string, action:"open-voting"|"finalize"|"cancel") => {
    try {
      const {data}=await api.post(`/events/admin/${eventId}/${action}`);
      if(action==="finalize") window.alert("Etkinlik sonuçları kesinleştirildi.");
      const {data:refreshed}=await api.get("/events"); setEvents(refreshed);
    } catch(error:any) { window.alert(error?.response?.data?.error || "Etkinlik işlemi başarısız."); }
  };

  const moderateSubmission = async (eventId:string,submissionId:string,status:"APPROVED"|"REJECTED") => {
    try {
      await api.post(`/events/admin/${eventId}/submissions/${submissionId}/moderation`,{status,reason:status==="APPROVED"?"Admin onayı":"Admin reddi"});
      await loadAdminSubmissions(eventId);
    } catch(error:any) { window.alert(error?.response?.data?.error || "Moderasyon işlemi başarısız."); }
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
    } catch (error) {
      console.error("[KitchenAI Web] recipe generation failed", error);
      window.alert((error as any)?.response?.data?.error || GENERIC_ERROR);
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
        {gamification && (
          <section className="card gamification-card">
            <div className="gamification-heading">
              <div><p className="eyebrow">MUTFAK YOLCULUĞUN</p>
                <h2>{gamification.lifetimeXp || 0} XP · Seviye {gamification.level || 1}</h2>
                <p>Bu hafta {gamification.weeklyXp || 0} XP kazandın · Haftalık sıralaman #{gamification.rank || "—"}</p>
              </div><span className="gamification-trophy">🏆</span>
            </div>
            <div className="gamification-columns">
              <div><h3>Haftanın liderleri</h3>
                {leaderboard.length === 0 ? <p>Bu hafta henüz sıralama oluşmadı. İlk pişirme deneyimini tamamla!</p> :
                  leaderboard.slice(0,5).map((entry:any) => <div className="leaderboard-row" key={entry.userId}>
                    <strong>#{entry.rank}</strong><span>{entry.displayName}</span><b>{entry.weeklyXp} XP</b>
                  </div>)}
                <small>Hafta: {gamification.weekStart} – {gamification.weekEnd}</small>
              </div>
              <div><h3>Son rozetlerin</h3>
                {(gamification.recentBadges || []).length === 0 ? <p>Henüz rozetin yok. Pişirmeye devam et!</p> :
                  gamification.recentBadges.slice(0,4).map((badge:any) => <div className="badge-row" key={badge.code}>
                    <span>{badge.icon}</span><div><b>{badge.name}</b><small>{badge.description}</small></div>
                  </div>)}
              </div>
            </div>
            {gamification.availableRewards?.length > 0 && <p className="reward-note">🎁 {gamification.availableRewards.length} ücretsiz tarif ödülün hazır.</p>}
          </section>
        )}
        {events.length > 0 && <section className="card events-card">
          <p className="eyebrow">TOPLULUK</p><h2>Mutfak etkinlikleri</h2>
          <div className="event-grid">{events.slice(0,3).map((event:any) => <article className="event-tile" key={event.id}>
            <span>🍰 {event.theme}</span><h3>{event.title}</h3><p>{event.description}</p>
            <small>{new Date(event.startsAt).toLocaleDateString("tr-TR")}</small>
          </article>)}</div>
        </section>}
        {user.isAdmin && <section className="card events-card">
          <p className="eyebrow">YÖNETİM</p><h2>Yeni mutfak etkinliği oluştur</h2>
          <form className="event-admin-form" onSubmit={createEvent}>
            <input required placeholder="Etkinlik adı" value={eventTitle} onChange={e=>setEventTitle(e.target.value)} />
            <input required placeholder="Tema (örn. Tiramisu)" value={eventTheme} onChange={e=>setEventTheme(e.target.value)} />
            <textarea required placeholder="Açıklama ve katılım kuralları" value={eventDescription} onChange={e=>setEventDescription(e.target.value)} />
            <label>Etkinlik başlangıcı<input required type="datetime-local" value={eventStart} onChange={e=>setEventStart(e.target.value)} /></label>
            <label>Fotoğraf yükleme başlangıcı<input required type="datetime-local" value={submissionOpen} onChange={e=>setSubmissionOpen(e.target.value)} /></label>
            <label>Fotoğraf yükleme bitişi<input required type="datetime-local" value={submissionClose} onChange={e=>setSubmissionClose(e.target.value)} /></label>
            <label>Oylama başlangıcı<input required type="datetime-local" value={votingOpen} onChange={e=>setVotingOpen(e.target.value)} /></label>
            <label>Oylama bitişi<input required type="datetime-local" value={votingClose} onChange={e=>setVotingClose(e.target.value)} /></label>
            <button type="submit">Etkinliği oluştur</button>
          </form>
          {events.map((event:any)=><div className="admin-event-row" key={event.id}>
            <b>{event.title} · {event.status}</b>
            <span><button type="button" className="link" onClick={()=>loadAdminSubmissions(event.id)}>Başvuruları görüntüle</button>
              <button type="button" className="link" onClick={()=>adminEventAction(event.id,"open-voting")}>Oylamayı başlat</button>
              <button type="button" className="link" onClick={()=>adminEventAction(event.id,"finalize")}>Sonuçlandır</button>
              <button type="button" className="link" onClick={()=>adminEventAction(event.id,"cancel")}>İptal et</button></span>
            {(adminSubmissions[event.id]||[]).map((submission:any)=><div className="admin-submission" key={submission.id}>
              <span>{submission.displayName} · {submission.status}<span className="admin-submission-images">{(submission.images||[]).map((src:string,index:number)=><img key={index} src={src} alt="Etkinlik gönderisi" />)}</span></span>
              {submission.status==="PENDING" && <span><button type="button" onClick={()=>moderateSubmission(event.id,submission.id,"APPROVED")}>Onayla</button>
                <button type="button" className="link" onClick={()=>moderateSubmission(event.id,submission.id,"REJECTED")}>Reddet</button></span>}
            </div>)}
          </div>)}
        </section>}
        {membership && (
          <section className="card membership">
            <div className="membership-top">
              <div>
                <p className="eyebrow">ÜYELİĞİN</p>
                <h2>{membership.tier === "FREE" ? "Free üyelik" : membership.tier === "COOKER" ? "Cooker üyeliği" : "Chef"}</h2>
                <p>{membership.remaining} / {membership.limit} AI tarif hakkın kaldı · {membership.period === "LIFETIME" ? "ömür boyu" : "bugün"}</p>
              </div>
              <Crown size={34} />
            </div>
            <div className="quota-track"><span style={{width: `${Math.min(100, membership.usage / Math.max(1, membership.limit) * 100)}%`}} /></div>
            <div className="plan-grid">
              <article className="plan-card current">
                <span className="plan-label">MEVCUT</span>
                <h3>Free</h3>
                <strong>3 tarif</strong>
                <p>Hesabın boyunca toplam 3 AI tarif üretimi.</p>
              </article>
              <article className="plan-card">
                <span className="plan-label">YAKINDA</span>
                <h3>Cooker Monthly</h3>
                <strong>10 tarif / gün</strong>
                <p>Her gün İstanbul saatiyle 00.00'da yenilenir.</p>
                <button disabled>Ödeme kurulumu bekleniyor</button>
              </article>
              <article className="plan-card">
                <span className="plan-label">YAKINDA</span>
                <h3>Cooker Yearly</h3>
                <strong>10 tarif / gün</strong>
                <p>Yıllık ödeme; günlük tarif hakkı değişmez.</p>
                <button disabled>Ödeme kurulumu bekleniyor</button>
              </article>
              <article className="plan-card chef-plan">
                <span className="plan-label">COMING SOON</span>
                <h3><Sparkles size={18} /> Chef</h3>
                <strong>Kişisel mutfak asistanı</strong>
                <p>Detaylar yakında açıklanacak.</p>
              </article>
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
              accept="image/jpeg,image/png,image/webp"
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
