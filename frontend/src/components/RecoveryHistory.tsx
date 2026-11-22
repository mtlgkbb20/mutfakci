import { useEffect, useState } from "react";
import { api } from "../api";
import { prettyJson } from "../lib";

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

export function RecoveryHistory({ onClose }: { onClose: () => void }) {
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

  const pretty = prettyJson;

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
