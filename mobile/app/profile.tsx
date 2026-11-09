import { useRouter } from "expo-router";
import { useEffect, useState } from "react";
import { AppState, Linking, ScrollView, Text, View } from "react-native";
import { api, clearSession } from "../src/api";
import { AppShell, Button, Field, Header, s } from "../src/ui";

export default function Profile() {
  const router = useRouter();
  const [user, setUser] = useState<any>();
  const [membership, setMembership] = useState<any>();
  const [progress, setProgress] = useState<any>({
    totalXp: 0,
    level: "BEGINNER_CHEF",
  });
  const [address, setAddress] = useState("");
  const [phone, setPhone] = useState("");
  const [checkoutLoading, setCheckoutLoading] = useState(false);
  const [checkoutMessage, setCheckoutMessage] = useState("");

  const refreshMembership = async () => {
    const result = await api("/membership");
    setMembership(result);
  };

  useEffect(() => {
    Promise.all([
      api("/auth/me"),
      api("/gamification/progress"),
      api("/membership"),
    ])
      .then(([currentUser, currentProgress, currentMembership]) => {
        setUser(currentUser);
        setProgress(currentProgress);
        setMembership(currentMembership);
      })
      .catch(() => router.replace("/auth"));
  }, []);

  useEffect(() => {
    const subscription = AppState.addEventListener("change", (state) => {
      if (state === "active") {
        refreshMembership().catch(() => undefined);
      }
    });
    return () => subscription.remove();
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

  const startCheckout = async (planCode: string) => {
    if (!address.trim() || !phone.trim()) {
      setCheckoutMessage("Ödeme için adres ve telefon bilgilerini doldur.");
      return;
    }
    setCheckoutLoading(true);
    setCheckoutMessage("");
    try {
      const checkout = await api("/membership/checkout", {
        method: "POST",
        body: JSON.stringify({
          planCode,
          displayName: user?.displayName || "Kitchen AI User",
          address,
          phone,
        }),
      });
      await Linking.openURL(checkout.checkoutUrl);
      setCheckoutMessage(
        "PayTR ödeme ekranı açıldı. İşlemden sonra uygulamaya dön; üyelik durumun otomatik yenilenecek.",
      );
    } catch (error: any) {
      setCheckoutMessage(error?.message || "Ödeme başlatılamadı.");
    } finally {
      setCheckoutLoading(false);
    }
  };

  const plan = (code: string) =>
    membership?.plans?.find((item: any) => item.code === code);

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
              {membership.period === "LIFETIME" ? " · ömür boyu" : " · bugün"}
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
                  width: `${Math.min(100, (membership.usage / Math.max(1, membership.limit)) * 100)}%`,
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
                Günlük 10 AI tarif üretimi. Aylık veya yıllık tek ödeme seçeneği.
              </Text>
              <Field
                value={address}
                onChangeText={setAddress}
                placeholder="Adres"
                autoCapitalize="sentences"
              />
              <Field
                value={phone}
                onChangeText={setPhone}
                placeholder="Telefon"
                keyboardType="phone-pad"
              />
              <Button
                loading={checkoutLoading}
                disabled={!plan("COOKER_MONTHLY")?.available}
                onPress={() => startCheckout("COOKER_MONTHLY")}
              >
                {plan("COOKER_MONTHLY")?.available
                  ? `Aylık Cooker · ${plan("COOKER_MONTHLY")?.price} ₺`
                  : "Aylık ödeme henüz kapalı"}
              </Button>
              <Button
                secondary
                loading={checkoutLoading}
                disabled={!plan("COOKER_YEARLY")?.available}
                onPress={() => startCheckout("COOKER_YEARLY")}
              >
                {plan("COOKER_YEARLY")?.available
                  ? `Yıllık Cooker · ${plan("COOKER_YEARLY")?.price} ₺`
                  : "Yıllık ödeme henüz kapalı"}
              </Button>
              <Text style={[s.recipeMeta, { marginTop: 8 }]}>
                Test entegrasyonu tek seferlik ödeme yapar; otomatik yenileme
                ve kayıtlı kart işlemleri henüz yoktur.
              </Text>
              {checkoutMessage ? (
                <Text style={[s.body, { marginTop: 10 }]}>{checkoutMessage}</Text>
              ) : null}
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
