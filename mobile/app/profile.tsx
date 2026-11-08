import { useRouter } from "expo-router";
import { useEffect, useState } from "react";
import { ScrollView, Text, View } from "react-native";
import { api, clearSession } from "../src/api";
import { AppShell, Button, Header, s } from "../src/ui";
export default function Profile() {
  const router = useRouter();
  const [user, setUser] = useState<any>();
  const [progress, setProgress] = useState<any>({
    totalXp: 0,
    level: "BEGINNER_CHEF",
  });
  useEffect(() => {
    Promise.all([api("/auth/me"), api("/gamification/progress")])
      .then(([u, p]) => {
        setUser(u);
        setProgress(p);
      })
      .catch(() => router.replace("/auth"));
  }, []);
  const level =
    progress.level === "BEGINNER_CHEF"
      ? "Başlangıç Şefi"
      : progress.level === "HOME_COOK"
        ? "Ev Aşçısı"
        : progress.level === "SKILLED_COOK"
          ? "Usta Aşçı"
          : "Mutfak Ustası";
  const logout = async () => {
    await clearSession();
    router.replace("/auth");
  };
  return (
    <AppShell>
      <ScrollView
        showsVerticalScrollIndicator={false}
        contentContainerStyle={s.scroll}
      >
        <Header title="Profil" subtitle="Mutfak yolculuğun" />
        <View style={[s.card, { alignItems: "center", paddingVertical: 30 }]}>
          <View
            style={[
              s.avatar,
              { width: 82, height: 82, borderRadius: 41, marginBottom: 15 },
            ]}
          >
            <Text style={[s.avatarText, { fontSize: 34 }]}>
              {(user?.displayName || "K").charAt(0).toUpperCase()}
            </Text>
          </View>
          <Text style={s.title}>{user?.displayName || "Aşçı"}</Text>
          <Text style={[s.body, { marginTop: 5 }]}>{user?.email || ""}</Text>
        </View>
        <View style={s.statRow}>
          <View style={s.stat}>
            <Text style={s.statValue}>{progress.totalXp || 0}</Text>
            <Text style={s.statLabel}>Toplam XP</Text>
          </View>
          <View style={s.stat}>
            <Text style={s.statValue}>⭐</Text>
            <Text style={s.statLabel}>{level}</Text>
          </View>
        </View>
        <View style={[s.card, { marginTop: 16 }]}>
          <Text style={s.eyebrow}>İLERLEME</Text>
          <Text style={s.heading}>Mutfak seviyen</Text>
          <Text style={[s.body, { marginTop: 6 }]}>
            Her tarif denemesi seni daha iyi bir aşçı yapar.
          </Text>
          <View style={s.progressTrack}>
            <View
              style={[
                s.progressFill,
                {
                  width: `${Math.min(100, (((progress.totalXp || 0) % 30) / 30) * 100)}%`,
                },
              ]}
            />
          </View>
          <Text style={[s.recipeMeta, { marginTop: 8 }]}>
            {level} · {progress.totalXp || 0} XP
          </Text>
        </View>
        <Button secondary onPress={logout}>
          Oturumu kapat
        </Button>
      </ScrollView>
    </AppShell>
  );
}
