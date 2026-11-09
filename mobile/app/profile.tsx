import { useRouter } from "expo-router";
import { useEffect, useState } from "react";
import { ScrollView, Text, View } from "react-native";
import { api, clearSession } from "../src/api";
import { AppShell, Button, Header, s } from "../src/ui";

export default function Profile() {
  const router = useRouter();

  const [user, setUser] = useState<any>();
  const [membership, setMembership] = useState<any>();
  const [badges, setBadges] = useState<any[]>([]);
  const [progress, setProgress] = useState<any>({
    totalXp: 0,
    level: "BEGINNER_CHEF",
  });

  useEffect(() => {
    Promise.all([
      api("/auth/me"),
      api("/gamification/progress"),
      api("/membership"),
      api("/gamification/badges/me"),
    ])
      .then(([currentUser, currentProgress, currentMembership, userBadges]) => {
        setUser(currentUser);
        setProgress(currentProgress);
        setMembership(currentMembership);
        setBadges(userBadges);
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
              {
                width: 82,
                height: 82,
                borderRadius: 41,
                marginBottom: 15,
              },
            ]}
          >
            <Text style={[s.avatarText, { fontSize: 34 }]}>
              {(user?.displayName || "K").charAt(0).toUpperCase()}
            </Text>
          </View>

          <Text style={s.title}>{user?.displayName || "Aşçı"}</Text>
          <Text style={[s.body, { marginTop: 5 }]}>
            {user?.email || ""}
          </Text>
        </View>

        {membership && (
          <View style={[s.card, { marginTop: 16 }]}>
            <Text style={s.eyebrow}>ÜYELİĞİN</Text>
            <Text style={s.heading}>
              {membership.tier === "FREE"
                ? "Free"
                : membership.tier === "COOKER"
                  ? "Cooker"
                  : "Chef"}
            </Text>
            <Text style={[s.body, { marginTop: 6 }]}>
              {membership.remaining} / {membership.limit} AI tarif hakkın kaldı
              {membership.period === "LIFETIME"
                ? " · ömür boyu"
                : " · bugün"}
            </Text>

            <View
              style={{
                height: 8,
                backgroundColor: "#f0e6da",
                borderRadius: 8,
                overflow: "hidden",
                marginTop: 14,
              }}
            >
              <View
                style={{
                  height: 8,
                  width: `${Math.min(
                    100,
                    (membership.usage / Math.max(1, membership.limit)) * 100,
                  )}%`,
                  backgroundColor: "#c55a36",
                }}
              />
            </View>

            <View
              style={{
                marginTop: 18,
                paddingTop: 15,
                borderTopWidth: 1,
                borderTopColor: "#e8ded3",
              }}
            >
              <Text style={s.heading}>Cooker</Text>
              <Text style={[s.body, { marginTop: 5 }]}>
                Günlük 10 AI tarif üretimi. Aylık veya yıllık üyelik seçeneği.
              </Text>
              <Text style={[s.recipeMeta, { marginTop: 8 }]}>
                Ödeme altyapısı hazırlanıyor.
              </Text>
            </View>

            <View
              style={{
                marginTop: 15,
                paddingTop: 15,
                borderTopWidth: 1,
                borderTopColor: "#e8ded3",
              }}
            >
              <Text style={s.heading}>Chef · Coming soon</Text>
              <Text style={[s.body, { marginTop: 5 }]}>
                Kişisel mutfak asistanı. Ayrıntılar yakında.
              </Text>
            </View>
          </View>
        )}

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
                  width: `${Math.min(
                    100,
                    (((progress.totalXp || 0) % 30) / 30) * 100,
                  )}%`,
                },
              ]}
            />
          </View>

          <Text style={[s.recipeMeta, { marginTop: 8 }]}>
            {level} · {progress.totalXp || 0} XP
          </Text>
        </View>

        <View style={[s.card, { marginTop: 16 }]}>
          <Text style={s.eyebrow}>ROZET KOLEKSİYONUM</Text>
          <Text style={s.heading}>Kazandığın rozetler</Text>

          {badges.length === 0 ? (
            <Text style={[s.body, { marginTop: 8 }]}>
              Henüz rozet kazanmadın. İlk yemeğini tamamlayarak başlayabilirsin.
            </Text>
          ) : (
            badges.map((badge: any) => (
              <View
                key={`${badge.code}-${badge.sourceKey}`}
                style={{
                  flexDirection: "row",
                  alignItems: "center",
                  paddingVertical: 10,
                  borderBottomWidth: 1,
                  borderBottomColor: "#eee5da",
                }}
              >
                <Text style={{ fontSize: 26, marginRight: 12 }}>
                  {badge.icon}
                </Text>

                <View style={{ flex: 1 }}>
                  <Text style={s.heading}>{badge.name}</Text>
                  <Text style={s.recipeMeta}>{badge.description}</Text>
                  <Text style={s.recipeMeta}>
                    {new Date(badge.awardedAt).toLocaleDateString("tr-TR")}
                  </Text>
                </View>
              </View>
            ))
          )}
        </View>

        <Button secondary onPress={logout}>
          Oturumu kapat
        </Button>
      </ScrollView>
    </AppShell>
  );
}
