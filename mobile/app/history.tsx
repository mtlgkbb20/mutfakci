import { useRouter } from "expo-router";
import { useEffect, useState } from "react";
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  Text,
  View,
} from "react-native";
import { api } from "../src/api";
import { AppShell, Header, s, colors } from "../src/ui";

type RecoveryItem = {
  id: string;
  recipeName?: string | null;
  problemDescription?: string | null;
  problemCategory?: string | null;
  status: string;
  createdAt?: string;
  hasImage?: boolean;
};

const statusLabels: Record<string, string> = {
  TEXT_FALLBACK_COMPLETE: "Metin önerisi hazır",
  TEXT_FALLBACK_UNAVAILABLE: "Metin önerisi alınamadı",
  CORRECTION_PROPOSED: "Düzeltme önerildi",
  RECOVERY_OFFERED: "Kurtarma seçeneği sunuldu",
  AWAITING_FOLLOW_UP: "Takip fotoğrafı bekleniyor",
  FOLLOW_UP_RECEIVED: "Takip fotoğrafı alındı",
  READY_TO_CONTINUE: "Tarife devam edilebilir",
  IMAGE_REJECTED: "Fotoğraf değerlendirilemedi",
  ANALYSIS_REJECTED: "Analiz güvenilir bulunmadı",
  ANALYSIS_INCOMPLETE: "Analiz tamamlanamadı",
  VISION_PROVIDER_UNAVAILABLE: "Görsel analiz kullanılamadı",
  REMAKE_RECOMMENDED: "Bileşeni yeniden yapmak önerildi",
};

function parseJson(value?: string | null): any {
  if (!value) return null;
  try { return JSON.parse(value); } catch { return null; }
}

function formatDate(value?: string) {
  if (!value) return "Tarih bilgisi yok";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Tarih bilgisi yok" : date.toLocaleString("tr-TR");
}

export default function History() {
  const router = useRouter();
  const [recipes, setRecipes] = useState<any[]>([]);
  const [recoveries, setRecoveries] = useState<RecoveryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [recipeError, setRecipeError] = useState("");
  const [recoveryError, setRecoveryError] = useState("");
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [details, setDetails] = useState<Record<string, any>>({});
  const [detailLoading, setDetailLoading] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    Promise.allSettled([api("/recipes"), api("/recovery-history")])
      .then(([recipeResult, recoveryResult]) => {
        if (!active) return;
        if (recipeResult.status === "fulfilled") setRecipes(recipeResult.value);
        else setRecipeError(recipeResult.reason?.message || "Tarif geçmişi yüklenemedi.");
        if (recoveryResult.status === "fulfilled") setRecoveries(recoveryResult.value);
        else setRecoveryError(recoveryResult.reason?.message || "Kurtarma geçmişi yüklenemedi.");
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  const toggleRecovery = async (id: string) => {
    if (expandedId === id) {
      setExpandedId(null);
      return;
    }
    setExpandedId(id);
    if (details[id]) return;
    setDetailLoading(id);
    try {
      const detail = await api(`/recovery-history/${id}`);
      setDetails(current => ({ ...current, [id]: detail }));
    } catch (e: any) {
      setRecoveryError(e.message || "Kurtarma detayı yüklenemedi.");
    } finally {
      setDetailLoading(null);
    }
  };

  return (
    <AppShell>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={s.scroll}>
        <Header title="Geçmişim" subtitle="Tariflerin ve pişirme denemelerin" />

        <Pressable style={s.card} onPress={() => router.push("/cooking-journal")}>
          <Text style={s.eyebrow}>PİŞİRME GÜNLÜĞÜ</Text>
          <Text style={s.heading}>Denemelerini kaydet ve karşılaştır</Text>
          <Text style={[s.body, { marginTop: 6 }]}>Puanların, notların ve geçmiş pişirme kayıtların.</Text>
          <Text style={[s.smallLink, { marginTop: 12 }]}>Günlüğümü aç →</Text>
        </Pressable>

        <View style={s.card}>
          <Text style={s.eyebrow}>TARİF KURTARMA</Text>
          <Text style={s.heading}>{recoveries.length} kurtarma kaydı</Text>
          <Text style={[s.body, { marginTop: 6 }]}>
            Fotoğrafla veya metinle yardım aldığın önceki denemeleri burada görebilirsin.
          </Text>
        </View>

        {loading ? <ActivityIndicator color={colors.orange} size="large" /> : null}
        {!loading && recoveryError ? <Text style={s.error}>{recoveryError}</Text> : null}
        {!loading && !recoveryError && recoveries.length === 0 ? (
          <View style={s.card}>
            <Text style={s.heading}>Henüz kurtarma kaydı yok</Text>
            <Text style={[s.body, { marginTop: 8 }]}>
              Bir tarif kurtarma denemesi yaptığında kayıt burada görünecek.
            </Text>
          </View>
        ) : null}

        {recoveries.map(item => {
          const detail = details[item.id];
          const assessment = parseJson(detail?.assessment);
          const action = parseJson(detail?.action);
          const expanded = expandedId === item.id;
          return (
            <View key={item.id} style={s.card}>
              <Pressable onPress={() => toggleRecovery(item.id)}>
                <View style={{ flexDirection: "row", alignItems: "center" }}>
                  <Text style={{ fontSize: 32, marginRight: 13 }}>🛟</Text>
                  <View style={{ flex: 1 }}>
                    <Text style={s.heading} numberOfLines={2}>
                      {item.recipeName || "İsimsiz tarif"}
                    </Text>
                    <Text style={[s.recipeMeta, { marginTop: 5 }]}>
                      {statusLabels[item.status] || item.status.replaceAll("_", " ")}
                    </Text>
                    <Text style={[s.recipeMeta, { marginTop: 3 }]}>
                      {formatDate(item.createdAt)}
                    </Text>
                  </View>
                  <Text style={{ fontSize: 24, color: colors.orange }}>{expanded ? "−" : "+"}</Text>
                </View>
                {item.problemDescription ? (
                  <Text style={[s.body, { marginTop: 10 }]}>{item.problemDescription}</Text>
                ) : null}
                {item.hasImage ? (
                  <Text style={[s.recipeMeta, { marginTop: 6 }]}>Fotoğraf kaydı mevcut</Text>
                ) : null}
              </Pressable>

              {expanded ? (
                <View style={{ borderTopWidth: 1, borderTopColor: colors.line, marginTop: 14, paddingTop: 12 }}>
                  {detailLoading === item.id ? (
                    <ActivityIndicator color={colors.orange} />
                  ) : detail ? (
                    <>
                      {assessment?.advice ? (
                        <>
                          <Text style={s.eyebrow}>METİN ÖNERİSİ</Text>
                          <Text style={[s.body, { marginTop: 5 }]}>{assessment.advice}</Text>
                        </>
                      ) : null}
                      {assessment?.caution ? (
                        <Text style={[s.recipeMeta, { marginTop: 8 }]}>{assessment.caution}</Text>
                      ) : null}
                      {assessment?.reason ? (
                        <>
                          <Text style={s.eyebrow}>DEĞERLENDİRME</Text>
                          <Text style={[s.body, { marginTop: 5 }]}>{assessment.reason}</Text>
                        </>
                      ) : null}
                      {action?.instruction ? (
                        <>
                          <Text style={[s.eyebrow, { marginTop: 12 }]}>ÖNERİLEN DÜZELTME</Text>
                          <Text style={[s.body, { marginTop: 5 }]}>{action.instruction}</Text>
                        </>
                      ) : null}
                      {detail.executions?.length ? (
                        <Text style={[s.recipeMeta, { marginTop: 10 }]}>
                          AI işlemi: {detail.executions.map((execution: any) => execution.operation).join(" · ")}
                        </Text>
                      ) : null}
                    </>
                  ) : (
                    <Text style={s.recipeMeta}>Kayıt ayrıntısı yüklenemedi.</Text>
                  )}
                </View>
              ) : null}
            </View>
          );
        })}

        <View style={[s.card, { marginTop: 12 }]}>
          <Text style={s.eyebrow}>TARİF GEÇMİŞİ</Text>
          <Text style={s.heading}>{recipes.length} tarif kaydedildi</Text>
          <Text style={[s.body, { marginTop: 6 }]}>
            Daha önce oluşturduğun tarifleri yeniden açabilirsin.
          </Text>
        </View>
        {recipeError ? <Text style={s.error}>{recipeError}</Text> : null}
        {!loading && recipes.length === 0 && !recipeError ? (
          <View style={s.card}>
            <Text style={s.body}>Henüz tarif yok. Dashboard’dan ilk tarifini oluştur.</Text>
          </View>
        ) : null}
        {recipes.map((recipe, index) => (
          <Pressable
            key={recipe.id || `history-recipe-${index}`}
            style={s.card}
            onPress={() => router.push({
              pathname: "/recipe",
              params: { data: JSON.stringify(recipe) },
            })}
          >
            <View style={{ flexDirection: "row", alignItems: "center" }}>
              <Text style={{ fontSize: 38, marginRight: 15 }}>
                {index % 3 === 0 ? "🍰" : index % 3 === 1 ? "🥞" : "🍲"}
              </Text>
              <View style={{ flex: 1 }}>
                <Text style={s.heading} numberOfLines={2}>{recipe.title}</Text>
                <Text style={[s.recipeMeta, { marginTop: 7 }]}>
                  {recipe.preparationTime} dk hazırlık · {recipe.cookingTime} dk pişirme · {recipe.servings} porsiyon
                </Text>
              </View>
              <Text style={{ fontSize: 25, color: colors.orange }}>›</Text>
            </View>
          </Pressable>
        ))}
      </ScrollView>
    </AppShell>
  );
}
