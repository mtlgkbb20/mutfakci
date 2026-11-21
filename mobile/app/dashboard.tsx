import { useRouter } from "expo-router";
import { useEffect, useState } from "react";
import { Alert, Pressable, ScrollView, Text, View } from "react-native";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { api, clearSession } from "../src/api";
import { describeProgress } from "../src/progress";
import { classifyBootstrapFailure } from "../src/session";
import { AppShell, Button, Field, Header, s, colors } from "../src/ui";

type Recipe = { id: string; title: string; description: string; servings: number; preparationTime: number; cookingTime: number; ingredients: string; stages: string; };
const GENERIC_ERROR = "Beklenmedik bir hata oluştu. Lütfen support ile iletişime geçin.";

export default function Dashboard() {
  const router = useRouter();
  const [user, setUser] = useState<any>();
  const [recipes, setRecipes] = useState<Recipe[]>([]);
  const [progress, setProgress] = useState<any>({ totalXp: 0, level: "BEGINNER_CHEF" });
  const [food, setFood] = useState("");
  const [loading, setLoading] = useState(false);
  const [expanded, setExpanded] = useState<"recipe" | "recovery" | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;
    const bootstrap = async () => {
      const token = await AsyncStorage.getItem("token");
      if (!token) { router.replace("/auth"); return; }
      const storedUser = await AsyncStorage.getItem("user");
      if (storedUser && active) { try { setUser(JSON.parse(storedUser)); } catch { await AsyncStorage.removeItem("user"); } }
      const [savedRecipes, currentProgress] = await Promise.all([api("/recipes"), api("/gamification/progress")]);
      if (!active) return;
      setRecipes(savedRecipes); setProgress(currentProgress); setLoadError(null);
    };
    bootstrap().catch(async (error) => {
      console.error("[KitchenAI Dashboard] bootstrap failed", error);
      const failure = classifyBootstrapFailure(error);
      if (failure.kind === "logout") {
        await clearSession();
        if (active) router.replace("/auth");
        return;
      }
      // Transient failure (5xx, timeout, network): keep the session and offer a retry.
      if (active) setLoadError(failure.message);
    });
    return () => { active = false; };
  }, [reloadKey]);

  const showGenericError = (error: unknown) => {
    console.error("[KitchenAI Dashboard] recipe generation failed", error);
    Alert.alert("Bir sorun oluştu", GENERIC_ERROR);
  };
  const generate = async () => {
    if (!food.trim()) return;
    setLoading(true);
    try {
      const recipe = await api("/recipes/generate", { method: "POST", body: JSON.stringify({ desiredFood: food, servings: 4 }) });
      setRecipes(current => [recipe, ...current.filter(item => item.id !== recipe.id)]);
      setFood("");
      router.push({ pathname: "/recipe", params: { data: JSON.stringify(recipe) } });
    } catch (error) { showGenericError(error); } finally { setLoading(false); }
  };
  const { xp, label: level, percent: xpProgress, caption: xpCaption } = describeProgress(progress);

  return <AppShell>
    <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={s.scroll}>
      <Header title="Kitchen AI" subtitle={user?.displayName ? `Merhaba, ${user.displayName} 👋` : "Bugün mutfakta ne var?"} />
      {loadError && <View style={s.card} accessibilityRole="alert">
        <Text style={s.heading}>Bir sorun oluştu</Text>
        <Text style={[s.body, { marginTop: 5 }]}>{loadError}</Text>
        <Button secondary onPress={() => { setLoadError(null); setReloadKey(key => key + 1); }}>Tekrar dene</Button>
      </View>}
      <View style={s.hero}>
        <View>
          <Text style={[s.eyebrow, { color: "#D9B77C" }]}>BUGÜNÜN MUTFAK NOTU</Text>
          <Text style={s.heroTitle}>Güzel yemekler, iyi hissettirir.</Text>
          <Text style={[s.body, { color: "#D5DED5", marginTop: 10, maxWidth: 280 }]}>Fikrini söyle. Tarifini bulalım, mutfaktaki küçük aksilikleri birlikte çözelim.</Text>
        </View>
        <Text style={s.heroMark}>✳</Text>
      </View>

      <View style={s.sectionRow}><Text style={s.heading}>Nereden başlayalım?</Text><Text style={s.recipeMeta}>HIZLI İŞLEMLER</Text></View>
      <View style={s.actionRow}>
        <Pressable style={s.actionButton} onPress={() => setExpanded(expanded === "recipe" ? null : "recipe")}>
          <Text style={s.actionIcon}>＋</Text><Text style={s.actionLabel}>Tarif öner</Text>
        </Pressable>
        <Pressable style={s.actionButton} onPress={() => setExpanded(expanded === "recovery" ? null : "recovery")}>
          <Text style={[s.actionIcon, { backgroundColor: "#F7EAE2", color: colors.orange }]}>✳</Text><Text style={s.actionLabel}>Tarifimi kurtar</Text>
        </Pressable>
      </View>

      {expanded === "recipe" && <View style={s.card}>
        <Text style={s.eyebrow}>YENİ BİR TARİF</Text><Text style={s.heading}>Bugün ne pişirelim?</Text>
        <Text style={[s.body, { marginTop: 5 }]}>Yemeğin adını veya aklındaki sonucu yaz.</Text>
        <Field value={food} onChangeText={setFood} placeholder="Örn. yumuşak elmalı kek" autoFocus />
        <Button onPress={generate} loading={loading}>Bana tarif öner</Button>
      </View>}
      {expanded === "recovery" && <View style={s.card}>
        <Text style={s.eyebrow}>MUTFAKTA DESTEK</Text><Text style={s.heading}>Bir şey planladığın gibi gitmedi mi?</Text>
        <Text style={[s.body, { marginTop: 5 }]}>Fotoğrafını paylaş, birlikte nasıl toparlayabileceğimize bakalım.</Text>
        <Button onPress={() => router.push("/recovery")}>Tarifimi kurtar</Button>
      </View>}

      <View style={s.sectionRow}><Text style={s.heading}>Mutfak asistanın</Text></View>
      <Pressable style={[s.card, { flexDirection: "row", alignItems: "center", padding: 18 }]} onPress={() => router.push("/pantry")}>
        <View style={{ width: 48, height: 48, borderRadius: 16, backgroundColor: colors.sage, alignItems: "center", justifyContent: "center", marginRight: 14 }}><Text style={{ fontSize: 25 }}>🥬</Text></View>
        <View style={{ flex: 1 }}><Text style={s.heading}>Buzdolabım</Text><Text style={s.recipeMeta}>Malzemelerini takip et, elindekilerle tarif keşfet.</Text></View>
        <Text style={{ color: colors.green, fontSize: 23 }}>›</Text>
      </Pressable>
      <View style={s.card}>
        <View style={{ flexDirection: "row", alignItems: "center" }}>
          <View style={{ flex: 1 }}><Text style={s.eyebrow}>HAFTALIK PLAN</Text><Text style={s.heading}>Haftanı lezzetle planla</Text></View><Text style={{ fontSize: 29 }}>🗓️</Text>
        </View>
        <Text style={[s.body, { marginTop: 7 }]}>Öğünlerini düzenle, eksiklerini alışveriş listene aktar.</Text>
        <Button secondary onPress={() => router.push("/meal-planner")}>Haftalık planı aç</Button>
      </View>
      <View style={s.card}>
        <View style={s.sectionRow}>
          <View><Text style={s.eyebrow}>GELİŞİMİN</Text><Text style={s.statValue}>{xp} XP</Text><Text style={s.statLabel}>{level}</Text></View>
          <View style={{ width: 52, height: 52, borderRadius: 18, backgroundColor: "#F7F0DF", alignItems: "center", justifyContent: "center" }}><Text style={{ fontSize: 27 }}>🏅</Text></View>
        </View>
        <View style={s.progressTrack}><View style={[s.progressFill, { width: `${xpProgress}%` }]} /></View>
        <Text style={s.recipeMeta}>{xpCaption}</Text>
      </View>
      <View style={s.sectionRow}>
        <View><Text style={s.heading}>Son tariflerin</Text><Text style={s.recipeMeta}>Yeniden göz at veya pişirmeye devam et</Text></View>
        <Pressable onPress={() => router.push("/(tabs)/history")}><Text style={s.smallLink}>Tümünü gör →</Text></Pressable>
      </View>
      {recipes.length === 0 ? <View style={s.card}>
        <Text style={{ fontSize: 30, marginBottom: 8 }}>🍲</Text><Text style={s.heading}>İlk tarifin seni bekliyor</Text><Text style={[s.body, { marginTop: 5 }]}>Bir tarif önerisi alarak mutfak yolculuğunu başlat.</Text>
        <Button secondary onPress={() => setExpanded("recipe")}>Tarif keşfet</Button>
      </View> : <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 10 }}>
        {recipes.slice(0, 6).map((recipe, index) => <Pressable key={recipe.id || index} style={s.recipeMini} onPress={() => router.push({ pathname: "/recipe", params: { data: JSON.stringify(recipe) } })}>
          <Text style={s.recipeEmoji}>{index % 3 === 0 ? "🍰" : index % 3 === 1 ? "🥞" : "🍲"}</Text>
          <Text numberOfLines={2} style={s.recipeTitle}>{recipe.title}</Text>
          <Text style={s.recipeMeta}>{recipe.preparationTime} dk · {recipe.servings} porsiyon</Text>
        </Pressable>)}
      </ScrollView>}
      <View style={s.card}>
        <Text style={s.eyebrow}>AKILLI ALIŞVERİŞ</Text><Text style={s.heading}>Alışverişini kolaylaştır</Text>
        <Text style={[s.body, { marginTop: 5 }]}>Planından liste oluştur, aldıklarını kilerine ekle.</Text>
        <Button secondary onPress={() => router.push("/shopping-list")}>Alışveriş asistanını aç</Button>
      </View>
    </ScrollView>
  </AppShell>;
}
