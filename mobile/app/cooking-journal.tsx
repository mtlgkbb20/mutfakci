import { useCallback, useEffect, useState } from "react";
import { ActivityIndicator, Image, Pressable, ScrollView, Text, View } from "react-native";
import { useRouter } from "expo-router";
import AsyncStorage from "@react-native-async-storage/async-storage";
import * as ImagePicker from "expo-image-picker";
import * as ImageManipulator from "expo-image-manipulator";
import { File as ExpoFile } from "expo-file-system";
import { api, apiBaseURL } from "../src/api";
import { AppShell, Button, Field, Header, s, colors } from "../src/ui";

type Session = { id: string; recipeId?: string | null; status: string; startedAt?: string };
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
type Variation = { id: string; title: string; sourceRecipeId: string; servings: number; createdAt: string };
function snapshot(entry: Entry): any {
  try { return JSON.parse(entry.recipeSnapshot); } catch { return {}; }
}
function recipeTitle(entry: Entry) { return snapshot(entry).title || "Tarifsiz pişirme"; }
function dateLabel(value?: string) {
  if (!value) return "Tarih yok";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Tarih yok" : date.toLocaleString("tr-TR");
}

export default function CookingJournal() {
  const router = useRouter();
  const [entries, setEntries] = useState<Entry[]>([]);
  const [sessions, setSessions] = useState<Session[]>([]);
  const [variations, setVariations] = useState<Variation[]>([]);
  const [selectedSession, setSelectedSession] = useState("");
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

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [journal, cookingSessions, savedVariations] = await Promise.all([
        api("/cooking-journal"),
        api("/cooking-sessions"),
        api("/cooking-journal/variations"),
      ]);
      setEntries(journal);
      setSessions(cookingSessions);
      setVariations(savedVariations);
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
      setNotice("Pişirme tamamlandı. Şimdi günlüğe kaydedebilirsin.");
      await load();
    } catch (e: any) { setError(e.message || "Pişirme tamamlanamadı."); }
    finally { setSaving(false); }
  };

  const save = async () => {
    if (!selectedSession) { setError("Önce tamamlanmış bir pişirme oturumu seç."); return; }
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
      setSelectedSession(""); setNotes(""); setRating(5); setServings("2"); setNewPhoto(undefined);
      setNotice("Pişirme günlüğe kaydedildi.");
      await load();
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
      setEntries(current => current.map(item => item.id === entry.id ? updated : item));
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

  const pendingSessions = sessions.filter(item => item.status === "ACTIVE");
  const completedSessions = sessions.filter(item => item.status === "COMPLETED" &&
    !entries.some(entry => entry.cookingSessionId === item.id));

  return (
    <AppShell>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={s.scroll}>
        <Header title="Pişirme Günlüğüm" subtitle="Denemelerini kaydet, gelişimini takip et" />
        <View style={s.card}>
          <Text style={s.eyebrow}>PİŞİRMEYİ TAMAMLA</Text>
          <Text style={s.heading}>Devam eden oturumların</Text>
          {pendingSessions.length === 0 ? (
            <Text style={[s.recipeMeta, { marginTop: 8 }]}>Devam eden pişirme oturumu yok.</Text>
          ) : pendingSessions.map(session => (
            <View key={session.id} style={{ borderTopWidth: 1, borderTopColor: colors.line, marginTop: 10, paddingTop: 10 }}>
              <Text style={s.body}>{session.recipeId ? "Tarifli pişirme" : "Tarifsiz pişirme"} · {dateLabel(session.startedAt)}</Text>
              <Button secondary onPress={() => completeSession(session.id)} loading={saving}>Pişirmeyi tamamladım</Button>
            </View>
          ))}
        </View>

        <View style={s.card}>
          <Text style={s.eyebrow}>YENİ KAYIT</Text>
          <Text style={s.heading}>Tamamladığın pişirmeyi kaydet</Text>
          <Text style={[s.body, { marginTop: 6 }]}>Her oturum günlüğe yalnızca bir kez eklenebilir.</Text>
          {completedSessions.length === 0 ? (
            <Text style={[s.recipeMeta, { marginTop: 12 }]}>Kaydedilmeyi bekleyen tamamlanmış oturum yok.</Text>
          ) : completedSessions.map(session => (
            <Pressable key={session.id} onPress={() => setSelectedSession(session.id)}
              style={{ borderWidth: 1, borderColor: selectedSession === session.id ? colors.orange : colors.line,
                backgroundColor: selectedSession === session.id ? colors.sand : colors.white,
                borderRadius: 12, padding: 12, marginTop: 8 }}>
              <Text style={s.body}>{session.recipeId ? "Tarifli pişirme" : "Tarifsiz pişirme"} · {dateLabel(session.startedAt)}</Text>
              <Text style={s.recipeMeta}>{selectedSession === session.id ? "Seçildi" : "Günlüğe eklemek için seç"}</Text>
            </Pressable>
          ))}
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
        </View>

        <View style={s.sectionRow}>
          <Text style={s.heading}>Geçmiş denemeler</Text>
          <Pressable onPress={load}><Text style={s.smallLink}>Yenile</Text></Pressable>
        </View>
        {loading ? <ActivityIndicator color={colors.orange} /> : null}
        {!loading && entries.length === 0 ? <View style={s.card}><Text style={s.body}>Henüz pişirme kaydı yok.</Text></View> : null}
        {entries.map(entry => {
          const attemptCount = entry.recipeId ? entries.filter(item => item.recipeId === entry.recipeId).length : 1;
          const draftPhoto = draftPhotos[entry.id];
          return (
            <View key={entry.id} style={s.card}>
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
        })}

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
    </AppShell>
  );
}
