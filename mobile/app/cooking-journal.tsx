import { useCallback, useEffect, useState } from "react";
import { ActivityIndicator, Pressable, ScrollView, Text, View } from "react-native";
import { useRouter } from "expo-router";
import { api } from "../src/api";
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
};
function recipeTitle(entry: Entry) {
  try { return JSON.parse(entry.recipeSnapshot)?.title || "Tarifsiz pişirme"; }
  catch { return "Tarifsiz pişirme"; }
}
function dateLabel(value?: string) {
  if (!value) return "Tarih yok";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Tarih yok" : date.toLocaleString("tr-TR");
}

export default function CookingJournal() {
  const router = useRouter();
  const [entries, setEntries] = useState<Entry[]>([]);
  const [sessions, setSessions] = useState<Session[]>([]);
  const [selectedSession, setSelectedSession] = useState("");
  const [notes, setNotes] = useState("");
  const [rating, setRating] = useState<number | undefined>(5);
  const [servings, setServings] = useState("2");
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [journal, cookingSessions] = await Promise.all([
        api("/cooking-journal"),
        api("/cooking-sessions"),
      ]);
      setEntries(journal);
      setSessions(cookingSessions.filter((item: Session) => item.status === "COMPLETED"));
    } catch (e: any) {
      setError(e.message || "Pişirme günlüğü yüklenemedi.");
    } finally { setLoading(false); }
  }, []);

  useEffect(() => { load(); }, [load]);

  const save = async () => {
    if (!selectedSession) { setError("Önce tamamlanmış bir pişirme oturumu seç."); return; }
    const servingCount = Number(servings);
    if (!Number.isInteger(servingCount) || servingCount < 1 || servingCount > 30) {
      setError("Porsiyon 1 ile 30 arasında olmalı."); return;
    }
    setSaving(true); setError("");
    try {
      await api("/cooking-journal", {
        method: "POST",
        body: JSON.stringify({
          cookingSessionId: selectedSession,
          servings: servingCount,
          rating,
          notes,
        }),
      });
      setSelectedSession(""); setNotes(""); setRating(5); setServings("2");
      await load();
    } catch (e: any) { setError(e.message || "Kayıt oluşturulamadı."); }
    finally { setSaving(false); }
  };

  const updateEntry = async (entry: Entry) => {
    setSaving(true); setError("");
    try {
      const updated = await api(`/cooking-journal/${entry.id}`, {
        method: "PATCH",
        body: JSON.stringify({ notes: drafts[entry.id] ?? entry.notes ?? "" }),
      });
      setEntries(current => current.map(item => item.id === entry.id ? updated : item));
    } catch (e: any) { setError(e.message || "Not güncellenemedi."); }
    finally { setSaving(false); }
  };

  return (
    <AppShell>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={s.scroll}>
        <Header title="Pişirme Günlüğüm" subtitle="Denemelerini kaydet, gelişimini takip et" />
        <View style={s.card}>
          <Text style={s.eyebrow}>YENİ KAYIT</Text>
          <Text style={s.heading}>Tamamladığın bir pişirmeyi kaydet</Text>
          <Text style={[s.body, { marginTop: 6 }]}>
            Yalnızca tamamlanmış oturumlar listelenir. Her oturum günlüğe bir kez eklenebilir.
          </Text>
          {sessions.length === 0 ? (
            <Text style={[s.recipeMeta, { marginTop: 12 }]}>Kaydedilmeyi bekleyen tamamlanmış oturum yok.</Text>
          ) : (
            sessions.map(session => {
              let label = "Tarifsiz pişirme";
              const existing = entries.find(item => item.cookingSessionId === session.id);
              if (existing) return null;
              return (
                <Pressable key={session.id} onPress={() => setSelectedSession(session.id)}
                  style={{ borderWidth: 1, borderColor: selectedSession === session.id ? colors.orange : colors.line,
                    backgroundColor: selectedSession === session.id ? colors.sand : colors.white,
                    borderRadius: 12, padding: 12, marginTop: 8 }}>
                  <Text style={s.body}>{label} · {dateLabel(session.startedAt)}</Text>
                  <Text style={s.recipeMeta}>{selectedSession === session.id ? "Seçildi" : "Günlüğe eklemek için seç"}</Text>
                </Pressable>
              );
            })
          )}
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
          <Button onPress={save} loading={saving}>Günlüğe kaydet</Button>
        </View>

        <View style={s.sectionRow}>
          <Text style={s.heading}>Geçmiş denemeler</Text>
          <Pressable onPress={load}><Text style={s.smallLink}>Yenile</Text></Pressable>
        </View>
        {loading ? <ActivityIndicator color={colors.orange} /> : null}
        {!loading && entries.length === 0 ? (
          <View style={s.card}><Text style={s.body}>Henüz pişirme kaydı yok.</Text></View>
        ) : null}
        {entries.map(entry => (
          <View key={entry.id} style={s.card}>
            <Text style={s.eyebrow}>{dateLabel(entry.cookedAt)}</Text>
            <Text style={s.heading}>{recipeTitle(entry)}</Text>
            <Text style={s.recipeMeta}>{entry.servings ?? "—"} porsiyon · {entry.rating ? `${entry.rating}/5 yıldız` : "Puanlanmadı"}</Text>
            <Field
              value={drafts[entry.id] ?? entry.notes ?? ""}
              onChangeText={value => setDrafts(current => ({ ...current, [entry.id]: value }))}
              multiline placeholder="Bu denemeyle ilgili notun"
            />
            <Button secondary onPress={() => updateEntry(entry)} loading={saving}>Notu güncelle</Button>
          </View>
        ))}
        {error ? <Text style={s.error}>{error}</Text> : null}
        <Button secondary onPress={() => router.back()}>Geri dön</Button>
      </ScrollView>
    </AppShell>
  );
}
