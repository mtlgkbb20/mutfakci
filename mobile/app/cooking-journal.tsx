import { useCallback, useEffect, useState } from "react";
import { ActivityIndicator, Image, Modal, Pressable, ScrollView, Text, View } from "react-native";
import { useRouter } from "expo-router";
import AsyncStorage from "@react-native-async-storage/async-storage";
import * as ImagePicker from "expo-image-picker";
import * as ImageManipulator from "expo-image-manipulator";
import { File as ExpoFile } from "expo-file-system";
import { api, apiBaseURL } from "../src/api";
import { AppShell, Button, Field, Header, s, colors } from "../src/ui";

type Session = { id: string; recipeId?: string | null; status: string; startedAt?: string; sessionType?: string; desiredOutcome?: string | null; recipeTitle?: string | null };
type EntrySummary = { id: string; cookingSessionId: string; recipeId?: string | null; title: string; servings?: number | null; rating?: number | null; cookedAt: string; hasPhoto?: boolean; };
type Entry = {
  id: string;
  cookingSessionId: string;
  recipeId?: string | null;
  recipeSnapshot: string;
  servings?: number | null;
  rating?: number | null;
  notes?: string | null;
  cookedAt: string;
  hasPhoto?: boolean;
};
type Variation = { id: string; title: string; servings: number; createdAt: string };
function snapshot(entry: Entry): any {
  try { return JSON.parse(entry.recipeSnapshot); } catch { return {}; }
}
function recipeTitle(entry: Entry) {
  const title = snapshot(entry).title;
  return typeof title === "string" && title.trim() ? title : "Tarif adı bulunamadı";
}
function dateLabel(value?: string) {
  if (!value) return "Tarih yok";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Tarih yok" : date.toLocaleString("tr-TR");
}

export default function CookingJournal() {
  const router = useRouter();
  const [entries, setEntries] = useState<EntrySummary[]>([]);
  const [selectedHistoryDetail, setSelectedHistoryDetail] = useState<Entry | null>(null);
  const [historyDetailLoading, setHistoryDetailLoading] = useState(false);
  const [sessions, setSessions] = useState<Session[]>([]);
  const [variations, setVariations] = useState<Variation[]>([]);
  const [selectedSession, setSelectedSession] = useState("");
  const [selectedHistoryId, setSelectedHistoryId] = useState("");
  const [picker, setPicker] = useState<"completed" | "history" | null>(null);
  const [pickerQuery, setPickerQuery] = useState("");
  const [sessionsModalVisible, setSessionsModalVisible] = useState(false);
  const [notes, setNotes] = useState("");
  const [rating, setRating] = useState<number | undefined>(5);
  const [servings, setServings] = useState("2");
  const [newPhoto, setNewPhoto] = useState<ImagePicker.ImagePickerAsset>();
  const [draftNotes, setDraftNotes] = useState<Record<string, string>>({});
  const [draftRatings, setDraftRatings] = useState<Record<string, number>>({});
  const [draftPhotos, setDraftPhotos] = useState<Record<string, ImagePicker.ImagePickerAsset>>({});
  const [variationEntry, setVariationEntry] = useState<Entry | null>(null);
  const [variationTitle, setVariationTitle] = useState("");
  const [variationDescription, setVariationDescription] = useState("");
  const [variationServings, setVariationServings] = useState("2");
  const [variationIngredients, setVariationIngredients] = useState("[]");
  const [variationStages, setVariationStages] = useState("[]");
  const [token, setToken] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const sessionTitle = (session: Session) => session.recipeTitle?.trim() || session.desiredOutcome?.trim() || "Serbest pişirme";

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [journal, cookingSessions, savedVariations] = await Promise.all([
        api("/cooking-journal"),
        api("/cooking-sessions"),
        api("/cooking-journal/variations/summaries"),
      ]);
      setEntries(journal);
      setSessions(cookingSessions);
      setVariations(savedVariations);
      setSelectedHistoryId(current => current && journal.some((entry: EntrySummary) => entry.id === current) ? current : "");
      setSelectedHistoryDetail(current => current && journal.some((entry: EntrySummary) => entry.id === current.id) ? current : null);
      setSelectedSession(current => cookingSessions.some((session: Session) =>
        session.id === current && session.status === "COMPLETED" &&
        !journal.some((entry: Entry) => entry.cookingSessionId === session.id)) ? current : "");
    } catch (e: any) {
      setError(e.message || "Pişirme günlüğü yüklenemedi.");
    } finally { setLoading(false); }
  }, []);

  useEffect(() => {
    AsyncStorage.getItem("token").then(value => setToken(value || ""));
    load();
  }, [load]);

  const choosePhoto = async (entryId?: string) => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) { setError("Fotoğraf galerisi izni gerekli."); return; }
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      quality: 0.85,
    });
    if (result.canceled) return;
    if (entryId) setDraftPhotos(current => ({ ...current, [entryId]: result.assets[0] }));
    else setNewPhoto(result.assets[0]);
  };

  const uploadPhoto = async (entryId: string, asset: ImagePicker.ImagePickerAsset) => {
    const converted = await ImageManipulator.manipulateAsync(asset.uri, [], {
      compress: 0.85,
      format: ImageManipulator.SaveFormat.JPEG,
    });
    const file = new ExpoFile(converted.uri);
    const form = new FormData();
    form.append("image", file, "cooking-journal.jpg");
    return api(`/cooking-journal/${entryId}/photo`, { method: "PUT", body: form }, 30000);
  };

  const completeSession = async (sessionId: string) => {
    setSaving(true); setError(""); setNotice("");
    try {
      await api(`/cooking-sessions/${sessionId}/complete`, { method: "POST" });
      setSelectedSession(sessionId);
      setSessionsModalVisible(false);
      setNotice("Pişirme tamamlandı. Günlüğe kaydetmek için aşağıdan seçebilirsin.");
      await load();
      setSelectedSession(sessionId);
    } catch (e: any) { setError(e.message || "Pişirme tamamlanamadı."); }
    finally { setSaving(false); }
  };

  const save = async () => {
    if (!selectedSession) { setError("Önce tamamlanmış bir pişirme seç."); return; }
    const servingCount = Number(servings);
    if (!Number.isInteger(servingCount) || servingCount < 1 || servingCount > 30) {
      setError("Porsiyon 1 ile 30 arasında olmalı."); return;
    }
    setSaving(true); setError(""); setNotice("");
    try {
      const created = await api("/cooking-journal", {
        method: "POST",
        body: JSON.stringify({ cookingSessionId: selectedSession, servings: servingCount, rating, notes }),
      });
      if (newPhoto) await uploadPhoto(created.id, newPhoto);
      setSelectedSession(""); setSelectedHistoryId(created.id); setSelectedHistoryDetail(null); setNotes(""); setRating(5); setServings("2"); setNewPhoto(undefined);
      setNotice("Pişirme günlüğe kaydedildi.");
      await load();
      const detail = await api(`/cooking-journal/${created.id}`);
      setSelectedHistoryDetail(detail);
    } catch (e: any) { setError(e.message || "Kayıt oluşturulamadı."); }
    finally { setSaving(false); }
  };

  const updateEntry = async (entry: Entry) => {
    setSaving(true); setError(""); setNotice("");
    try {
      const updated = await api(`/cooking-journal/${entry.id}`, {
        method: "PATCH",
        body: JSON.stringify({
          notes: draftNotes[entry.id] ?? entry.notes ?? "",
          rating: draftRatings[entry.id] ?? entry.rating,
        }),
      });
      setSelectedHistoryDetail(updated);
      setEntries(current => current.map(item => item.id === entry.id ? { ...item, servings: updated.servings, rating: updated.rating } : item));
      setNotice("Pişirme kaydı güncellendi.");
    } catch (e: any) { setError(e.message || "Kayıt güncellenemedi."); }
    finally { setSaving(false); }
  };

  const savePhoto = async (entry: Entry) => {
    const asset = draftPhotos[entry.id];
    if (!asset) return;
    setSaving(true); setError("");
    try {
      await uploadPhoto(entry.id, asset);
      setDraftPhotos(current => { const next = { ...current }; delete next[entry.id]; return next; });
      await load();
      const detail = await api(`/cooking-journal/${entry.id}`);
      setSelectedHistoryDetail(detail);
      setNotice("Fotoğraf güncellendi.");
    } catch (e: any) { setError(e.message || "Fotoğraf yüklenemedi."); }
    finally { setSaving(false); }
  };

  const openVariation = (entry: Entry) => {
    const data = snapshot(entry);
    setVariationEntry(entry);
    setVariationTitle(`${data.title || "Tarif"} — kişisel varyasyonum`);
    setVariationDescription(data.description || "");
    setVariationServings(String(entry.servings || data.servings || 2));
    setVariationIngredients(data.ingredients || "[]");
    setVariationStages(data.stages || "[]");
  };

  const createVariation = async () => {
    if (!variationEntry) return;
    const count = Number(variationServings);
    if (!Number.isInteger(count) || count < 1 || count > 30) {
      setError("Varyasyon porsiyonu 1 ile 30 arasında olmalı."); return;
    }
    setSaving(true); setError(""); setNotice("");
    try {
      await api(`/cooking-journal/${variationEntry.id}/variations`, {
        method: "POST",
        body: JSON.stringify({
          title: variationTitle,
          description: variationDescription,
          servings: count,
          ingredientsJson: variationIngredients,
          stagesJson: variationStages,
        }),
      });
      setVariationEntry(null);
      setNotice("Kişisel tarif varyasyonun ayrı olarak kaydedildi.");
      await load();
    } catch (e: any) { setError(e.message || "Varyasyon kaydedilemedi. Malzeme ve aşama alanları geçerli JSON dizileri olmalı."); }
    finally { setSaving(false); }
  };

  const selectHistory = async (entryId: string) => {
    if (selectedHistoryDetail?.id === entryId) {
      setSelectedHistoryId(entryId);
      setPicker(null);
      return;
    }
    setSelectedHistoryId(entryId);
    setSelectedHistoryDetail(null);
    setHistoryDetailLoading(true);
    setPicker(null);
    setError("");
    try {
      const detail = await api(`/cooking-journal/${entryId}`);
      setSelectedHistoryDetail(detail);
    } catch (e: any) {
      setError(e.message || "Pişirme detayı yüklenemedi.");
    } finally { setHistoryDetailLoading(false); }
  };

  const pendingSessions = sessions.filter(item => item.status === "ACTIVE");
  const completedSessions = sessions.filter(item => item.status === "COMPLETED" &&
    !entries.some(entry => entry.cookingSessionId === item.id));
  const selectedCompletedSession = completedSessions.find(item => item.id === selectedSession);
  const selectedHistory = selectedHistoryDetail?.id === selectedHistoryId ? selectedHistoryDetail : undefined;
  const pickerItems = picker === "completed" ? completedSessions : entries;
  const filteredPickerItems = pickerItems.filter(item => {
    const title = picker === "completed"
      ? sessionTitle(item as Session)
      : (item as EntrySummary).title;
    return title.toLocaleLowerCase("tr-TR").includes(pickerQuery.toLocaleLowerCase("tr-TR"));
  });

  const openPicker = (type: "completed" | "history") => {
    setPickerQuery("");
    setPicker(type);
  };

  return (
    <AppShell>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={s.scroll}>
        <Header title="Pişirme Günlüğüm" subtitle="Denemelerini kaydet, gelişimini takip et" />

        <View style={s.card}>
          <Text style={s.eyebrow}>PİŞİRMEYİ TAMAMLA</Text>
          <View style={s.sectionRow}>
            <Text style={s.heading}>Devam eden oturumlar</Text>
            <Text style={s.recipeMeta}>{pendingSessions.length} oturum</Text>
          </View>
          {pendingSessions.length === 0 ? (
            <Text style={[s.recipeMeta, { marginTop: 8 }]}>Devam eden pişirme oturumu yok.</Text>
          ) : (
            <>
              {pendingSessions.slice(0, 3).map(session => (
                <View key={session.id} style={{ borderTopWidth: 1, borderTopColor: colors.line, marginTop: 10, paddingTop: 10 }}>
                  <Text style={s.body}>{sessionTitle(session)}</Text>
                  <Text style={s.recipeMeta}>{dateLabel(session.startedAt)}</Text>
                </View>
              ))}
              {pendingSessions.length > 3 ? (
                <Text style={[s.recipeMeta, { marginTop: 8 }]}>+ {pendingSessions.length - 3} more</Text>
              ) : null}
              <Button secondary onPress={() => setSessionsModalVisible(true)}>Tüm oturumları gör</Button>
            </>
          )}
        </View>

        <View style={s.card}>
          <Text style={s.eyebrow}>YENİ KAYIT</Text>
          <Text style={s.heading}>Tamamladığın pişirmeyi kaydet</Text>
          <Text style={[s.body, { marginTop: 6 }]}>Tamamlanan oturumlarından birini seç; yalnızca seçtiğin pişirmenin detayları burada düzenlenir.</Text>
          {completedSessions.length === 0 ? (
            <Text style={[s.recipeMeta, { marginTop: 12 }]}>Kaydedilmeyi bekleyen tamamlanmış oturum yok.</Text>
          ) : (
            <Pressable onPress={() => openPicker("completed")} style={{
              borderWidth: 1, borderColor: selectedSession ? colors.orange : colors.line,
              backgroundColor: selectedSession ? colors.sand : colors.white,
              borderRadius: 12, padding: 14, marginTop: 12,
              flexDirection: "row", justifyContent: "space-between", alignItems: "center",
            }}>
              <View style={{ flex: 1, paddingRight: 12 }}>
                <Text style={s.eyebrow}>PİŞİRME SEÇ</Text>
                <Text style={s.body}>{selectedCompletedSession ? sessionTitle(selectedCompletedSession) : "Kaydedilecek pişirmeyi seç"}</Text>
                {selectedCompletedSession ? <Text style={s.recipeMeta}>{dateLabel(selectedCompletedSession.startedAt)}</Text> : null}
              </View>
              <Text style={{ color: colors.orange, fontWeight: "800" }}>⌄</Text>
            </Pressable>
          )}
          {selectedCompletedSession ? (
            <>
              <Field value={servings} onChangeText={setServings} keyboardType="number-pad" placeholder="Porsiyon" />
              <Text style={[s.eyebrow, { marginTop: 14 }]}>PUANIN</Text>
              <View style={{ flexDirection: "row", gap: 8 }}>
                {[1,2,3,4,5].map(value => (
                  <Pressable key={value} onPress={() => setRating(value)}
                    style={{ padding: 10, borderRadius: 10, backgroundColor: rating === value ? colors.orange : colors.sand }}>
                    <Text style={{ color: rating === value ? colors.white : colors.ink, fontWeight: "800" }}>{value} ★</Text>
                  </Pressable>
                ))}
              </View>
              <Field value={notes} onChangeText={setNotes} multiline placeholder="Notların: Neyi farklı yaptın? Sonuç nasıldı?" />
              <Button secondary onPress={() => choosePhoto()}>Fotoğraf seç (isteğe bağlı)</Button>
              {newPhoto ? <Image source={{ uri: newPhoto.uri }} style={{ height: 180, borderRadius: 14, marginTop: 10 }} resizeMode="cover" /> : null}
              <Button onPress={save} loading={saving}>Günlüğe kaydet</Button>
            </>
          ) : null}
        </View>

        <View style={s.card}>
          <View style={s.sectionRow}>
            <View style={{ flex: 1 }}>
              <Text style={s.eyebrow}>KİŞİSEL ARŞİVİN</Text>
              <Text style={s.heading}>Geçmiş denemeler</Text>
              <Text style={s.recipeMeta}>{entries.length} kayıtlı pişirme</Text>
            </View>
            <Pressable onPress={load}><Text style={s.smallLink}>Yenile</Text></Pressable>
          </View>
          {loading ? <ActivityIndicator color={colors.orange} /> : null}
          {!loading && entries.length === 0 ? <Text style={s.body}>Henüz pişirme kaydı yok.</Text> : null}
          {historyDetailLoading ? <ActivityIndicator color={colors.orange} /> : null}
          {entries.length > 0 ? (
            <>
              <Pressable onPress={() => openPicker("history")} style={{
                borderWidth: 1, borderColor: colors.line, borderRadius: 12, padding: 14,
                flexDirection: "row", justifyContent: "space-between", alignItems: "center",
              }}>
                <View style={{ flex: 1, paddingRight: 12 }}>
                  <Text style={s.eyebrow}>DENEME SEÇ</Text>
                  <Text style={s.body}>{selectedHistory ? recipeTitle(selectedHistory) : (entries.find(item => item.id === selectedHistoryId)?.title || "Geçmiş denemelerden birini seç")}</Text>
                  {selectedHistoryId ? <Text style={s.recipeMeta}>{dateLabel(selectedHistory?.cookedAt || entries.find(item => item.id === selectedHistoryId)?.cookedAt)}</Text> : null}
                </View>
                <Text style={{ color: colors.orange, fontWeight: "800" }}>⌄</Text>
              </Pressable>
              {selectedHistory ? (() => {
                const entry = selectedHistory;
                const attemptCount = entry.recipeId ? entries.filter(item => item.recipeId === entry.recipeId).length : 1;
                const draftPhoto = draftPhotos[entry.id];
                return (
                  <View style={{ marginTop: 14, borderTopWidth: 1, borderTopColor: colors.line, paddingTop: 14 }}>
                    <Text style={s.eyebrow}>{dateLabel(entry.cookedAt)}</Text>
                    <Text style={s.heading}>{recipeTitle(entry)}</Text>
                    <Text style={s.recipeMeta}>{entry.servings ?? "—"} porsiyon · {attemptCount} kayıtlı deneme · {entry.rating ? `${entry.rating}/5 yıldız` : "Puanlanmadı"}</Text>
                    {entry.hasPhoto && token ? (
                      <Image source={{ uri: `${apiBaseURL}/cooking-journal/${entry.id}/photo`, headers: { Authorization: `Bearer ${token}` } }}
                        style={{ height: 190, borderRadius: 14, marginTop: 12 }} resizeMode="cover" />
                    ) : null}
                    {draftPhoto ? <Image source={{ uri: draftPhoto.uri }} style={{ height: 180, borderRadius: 14, marginTop: 10 }} resizeMode="cover" /> : null}
                    <Button secondary onPress={() => choosePhoto(entry.id)}>Fotoğraf ekle / değiştir</Button>
                    {draftPhoto ? <Button onPress={() => savePhoto(entry)} loading={saving}>Fotoğrafı kaydet</Button> : null}
                    <Text style={[s.eyebrow, { marginTop: 14 }]}>PUAN</Text>
                    <View style={{ flexDirection: "row", gap: 8 }}>
                      {[1,2,3,4,5].map(value => (
                        <Pressable key={value} onPress={() => setDraftRatings(current => ({ ...current, [entry.id]: value }))}
                          style={{ padding: 8, borderRadius: 9, backgroundColor: (draftRatings[entry.id] ?? entry.rating) === value ? colors.orange : colors.sand }}>
                          <Text style={{ color: (draftRatings[entry.id] ?? entry.rating) === value ? colors.white : colors.ink }}>{value}★</Text>
                        </Pressable>
                      ))}
                    </View>
                    <Field value={draftNotes[entry.id] ?? entry.notes ?? ""}
                      onChangeText={value => setDraftNotes(current => ({ ...current, [entry.id]: value }))}
                      multiline placeholder="Bu denemeyle ilgili notun" />
                    <Button secondary onPress={() => updateEntry(entry)} loading={saving}>Puanı ve notu güncelle</Button>
                    {entry.recipeId ? <Button secondary onPress={() => openVariation(entry)}>Bu denemeden varyasyon oluştur</Button> : null}
                  </View>
                );
              })() : null}
            </>
          ) : null}
        </View>

        {variationEntry ? (
          <View style={s.card}>
            <Text style={s.eyebrow}>KİŞİSEL TARİF VARYASYONU</Text>
            <Text style={s.heading}>Orijinal tariften ayrı kaydet</Text>
            <Field value={variationTitle} onChangeText={setVariationTitle} placeholder="Varyasyon adı" />
            <Field value={variationDescription} onChangeText={setVariationDescription} multiline placeholder="Açıklama" />
            <Field value={variationServings} onChangeText={setVariationServings} keyboardType="number-pad" placeholder="Porsiyon" />
            <Text style={[s.eyebrow, { marginTop: 12 }]}>MALZEMELER — JSON DİZİSİ</Text>
            <Field value={variationIngredients} onChangeText={setVariationIngredients} multiline />
            <Text style={[s.eyebrow, { marginTop: 12 }]}>AŞAMALAR — JSON DİZİSİ</Text>
            <Field value={variationStages} onChangeText={setVariationStages} multiline />
            <Button onPress={createVariation} loading={saving}>Varyasyonu kaydet</Button>
            <Button secondary onPress={() => setVariationEntry(null)}>Vazgeç</Button>
          </View>
        ) : null}

        {variations.length > 0 ? (
          <>
            <Text style={s.heading}>Kişisel varyasyonların</Text>
            {variations.map(variation => (
              <View key={variation.id} style={s.card}>
                <Text style={s.heading}>{variation.title}</Text>
                <Text style={s.recipeMeta}>{variation.servings} porsiyon · {dateLabel(variation.createdAt)}</Text>
              </View>
            ))}
          </>
        ) : null}
        {notice ? <Text style={{ color: colors.green, marginBottom: 10 }}>{notice}</Text> : null}
        {error ? <Text style={s.error}>{error}</Text> : null}
        <Button secondary onPress={() => router.back()}>Geri dön</Button>
      </ScrollView>

      <Modal visible={sessionsModalVisible} transparent animationType="slide" onRequestClose={() => setSessionsModalVisible(false)}>
        <View style={{ flex: 1, justifyContent: "flex-end", backgroundColor: "rgba(30,24,20,0.45)" }}>
          <View style={{ maxHeight: "82%", backgroundColor: colors.cream, borderTopLeftRadius: 24, borderTopRightRadius: 24, padding: 20 }}>
            <View style={s.sectionRow}>
              <View>
                <Text style={s.eyebrow}>DEVAM EDENLER</Text>
                <Text style={s.heading}>Oturumlarını yönet</Text>
                <Text style={s.recipeMeta}>{pendingSessions.length} devam eden oturum</Text>
              </View>
              <Pressable onPress={() => setSessionsModalVisible(false)}><Text style={s.smallLink}>Kapat</Text></Pressable>
            </View>
            <ScrollView>
              {pendingSessions.map(session => (
                <View key={session.id} style={{ backgroundColor: colors.white, borderWidth: 1, borderColor: colors.line, borderRadius: 14, padding: 14, marginBottom: 10 }}>
                  <Text style={s.body}>{sessionTitle(session)}</Text>
                  <Text style={s.recipeMeta}>{dateLabel(session.startedAt)}</Text>
                  <Button onPress={() => completeSession(session.id)} loading={saving}>Tamamlandı olarak işaretle</Button>
                </View>
              ))}
              {pendingSessions.length === 0 ? <Text style={s.body}>Devam eden oturum kalmadı.</Text> : null}
            </ScrollView>
          </View>
        </View>
      </Modal>

      <Modal visible={picker !== null} transparent animationType="slide" onRequestClose={() => setPicker(null)}>
        <View style={{ flex: 1, justifyContent: "flex-end", backgroundColor: "rgba(30,24,20,0.45)" }}>
          <View style={{ maxHeight: "82%", backgroundColor: colors.cream, borderTopLeftRadius: 24, borderTopRightRadius: 24, padding: 20 }}>
            <View style={s.sectionRow}>
              <Text style={s.heading}>{picker === "completed" ? "Pişirme seç" : "Geçmiş deneme seç"}</Text>
              <Pressable onPress={() => setPicker(null)}><Text style={s.smallLink}>Kapat</Text></Pressable>
            </View>
            <Field value={pickerQuery} onChangeText={setPickerQuery} placeholder="Tarif adıyla ara" />
            <ScrollView keyboardShouldPersistTaps="handled" style={{ marginTop: 12 }}>
              {filteredPickerItems.map(item => {
                const isCompleted = picker === "completed";
                const id = item.id;
                const title = isCompleted ? sessionTitle(item as Session) : (item as EntrySummary).title;
                const date = isCompleted ? dateLabel((item as Session).startedAt) : dateLabel((item as EntrySummary).cookedAt);
                const selected = isCompleted ? selectedSession === id : selectedHistoryId === id;
                return (
                  <Pressable key={id} onPress={() => {
                    if (isCompleted) setSelectedSession(id);
                    else void selectHistory(id);
                    setPicker(null);
                  }} style={{
                    backgroundColor: selected ? colors.sand : colors.white,
                    borderWidth: 1, borderColor: selected ? colors.orange : colors.line,
                    borderRadius: 12, padding: 14, marginBottom: 8,
                  }}>
                    <Text style={s.body}>{title}</Text>
                    <Text style={s.recipeMeta}>{date}{selected ? " · Seçili" : ""}</Text>
                  </Pressable>
                );
              })}
              {filteredPickerItems.length === 0 ? <Text style={s.body}>Aramana uygun kayıt bulunamadı.</Text> : null}
            </ScrollView>
          </View>
        </View>
      </Modal>
    </AppShell>
  );
}
