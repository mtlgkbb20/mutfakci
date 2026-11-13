import { useRouter } from "expo-router";
import { useEffect, useState } from "react";
import { Alert, Pressable, ScrollView, Text, View } from "react-native";
import { api } from "../src/api";
import { AppShell, Button, Field, Header, s } from "../src/ui";

type Recipe = {
  id: string;
  title: string;
  description: string;
  servings: number;
  preparationTime: number;
  cookingTime: number;
  ingredients: string;
  stages: string;
};

const GENERIC_ERROR =
  "Beklenmedik bir hata oluştu. Lütfen support ile iletişime geçin.";

export default function Dashboard() {
  const router = useRouter();
  const [user, setUser] = useState<any>();
  const [recipes, setRecipes] = useState<Recipe[]>([]);
  const [progress, setProgress] = useState<any>({
    totalXp: 0,
    level: "BEGINNER_CHEF",
  });
  const [food, setFood] = useState("");
  const [loading, setLoading] = useState(false);
  const [expanded, setExpanded] = useState<"recipe" | "recovery" | null>(null);

  useEffect(() => {
    Promise.all([
      api("/auth/me"),
      api("/recipes"),
      api("/gamification/progress"),
    ])
      .then(([currentUser, savedRecipes, currentProgress]) => {
        setUser(currentUser);
        setRecipes(savedRecipes);
        setProgress(currentProgress);
      })
      .catch((error) => {
        console.error("[KitchenAI Dashboard] bootstrap failed", error);
        router.replace("/auth");
      });
  }, []);

  const showGenericError = (error: unknown) => {
    console.error("[KitchenAI Dashboard] recipe generation failed", error);
    Alert.alert("Bir sorun oluştu", GENERIC_ERROR);
  };

  const generate = async () => {
    if (!food.trim()) {
      return;
    }

    setLoading(true);
    try {
      const recipe = await api("/recipes/generate", {
        method: "POST",
        body: JSON.stringify({ desiredFood: food, servings: 4 }),
      });
            setRecipes((current) => [
              recipe,
              ...current.filter((item) => item.id !== recipe.id),
            ]);
      setFood("");
      router.push({
        pathname: "/recipe",
        params: { data: JSON.stringify(recipe) },
      });
    } catch (error) {
      showGenericError(error);
    } finally {
      setLoading(false);
    }
  };

  const xp = Number(progress.totalXp || 0);
  const level =
    progress.level === "BEGINNER_CHEF"
      ? "Başlangıç Şefi"
      : progress.level === "HOME_COOK"
        ? "Ev Aşçısı"
        : progress.level === "SKILLED_COOK"
          ? "Usta Aşçı"
          : "Mutfak Ustası";

  return (
    <AppShell>
      <ScrollView
        showsVerticalScrollIndicator={false}
        contentContainerStyle={s.scroll}
      >
        <Header
          title="Kitchen AI"
          subtitle={
            user?.displayName
              ? `Merhaba, ${user.displayName}`
              : "Mutfağın hazır mı?"
          }
        />
        <View style={s.hero}>
          <Text style={s.heroTitle}>Yemeğini birlikte güzelleştirelim.</Text>
          <Text style={[s.body, { marginTop: 10 }]}>
            Yeni bir yemek yapmak veya pişirdiğin yemeği düzeltmek için
            buradayız.
          </Text>
          <Text style={s.heroMark}>✦</Text>
        </View>

        <View style={s.actionRow}>
          <Pressable
            style={s.actionButton}
            onPress={() => setExpanded(expanded === "recipe" ? null : "recipe")}
          >
            <Text style={s.actionIcon}>＋</Text>
            <Text style={s.actionLabel}>Yeni Tarif</Text>
          </Pressable>
          <Pressable
            style={s.actionButton}
            onPress={() =>
              setExpanded(expanded === "recovery" ? null : "recovery")
            }
          >
            <Text style={s.actionIcon}>?</Text>
            <Text style={s.actionLabel}>Yardım</Text>
          </Pressable>
          <Pressable style={s.actionButton} onPress={() => router.push("/pantry")}>
            <Text style={s.actionIcon}>⌂</Text>
            <Text style={s.actionLabel}>Akıllı Kiler</Text>
          </Pressable>
        </View>

        <View style={[s.card, { padding: 18 }]}>
          <Text style={s.eyebrow}>HAFTALIK YEMEK PLANI</Text>
          <Text style={s.heading}>Bu haftanın öğünlerini planla</Text>
          <Text style={[s.body, { marginTop: 6 }]}>
            Kilerindeki malzemeleri değerlendir, eksiklerini tek alışveriş listesinde gör.
          </Text>
          <Button onPress={() => router.push("/meal-planner")}>Haftalık planı aç</Button>
        </View>

        {expanded === "recipe" && (
          <View style={s.card}>
            <Text style={s.eyebrow}>YENİ BİR ŞEY YAP</Text>
            <Text style={s.heading}>Ne pişirmek istiyorsun?</Text>
            <Text style={[s.body, { marginTop: 6 }]}>
              Yemeğin adını veya istediğin sonucu yaz.
            </Text>
            <Field
              value={food}
              onChangeText={setFood}
              placeholder="Örn. yumuşak elmalı kek"
              autoFocus
            />
            <Button onPress={generate} loading={loading}>
              Tarif oluştur
            </Button>
          </View>
        )}

        {expanded === "recovery" && (
          <View style={s.card}>
            <Text style={s.eyebrow}>YEMEĞİNE YARDIM ET</Text>
            <Text style={s.heading}>Bir şey ters mi gitti?</Text>
            <Text style={[s.body, { marginTop: 6 }]}>
              Fotoğrafını gönder. Ne yapabileceğini kısa ve anlaşılır şekilde
              anlatalım.
            </Text>
            <Button onPress={() => router.push("/recovery")}>
              Fotoğraf seç
            </Button>
          </View>
        )}

        <View style={[s.card, { marginBottom: 20 }]}>
          <View style={s.sectionRow}>
            <View>
              <Text style={s.eyebrow}>İLERLEMEN</Text>
              <Text style={s.statValue}>{xp} XP</Text>
              <Text style={s.statLabel}>{level}</Text>
            </View>
            <Text style={{ fontSize: 34 }}>🏆</Text>
          </View>
          <View style={s.progressTrack}>
            <View
              style={[
                s.progressFill,
                { width: `${Math.min(100, ((xp % 30) / 30) * 100)}%` },
              ]}
            />
          </View>
          <Text style={s.recipeMeta}>
            {xp < 30
              ? `${30 - xp} XP sonra yeni seviye`
              : "Yeni mutfak hedeflerine devam et"}
          </Text>
        </View>

        <View style={s.sectionRow}>
          <Text style={s.heading}>Son tarifler</Text>
          <Pressable onPress={() => router.push("/(tabs)/history")}>
            <Text style={s.smallLink}>Tümünü gör</Text>
          </Pressable>
        </View>
        {recipes.length === 0 ? (
          <View style={s.card}>
            <Text style={s.body}>İlk tarifin burada görünecek.</Text>
          </View>
        ) : (
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={{ paddingBottom: 10 }}
          >
            {recipes.slice(0, 6).map((recipe, index) => (
              <Pressable
                key={`dashboard-recipe-${index}`}
                style={s.recipeMini}
                onPress={() =>
                  router.push({
                    pathname: "/recipe",
                    params: { data: JSON.stringify(recipe) },
                  })
                }
              >
                <Text style={s.recipeEmoji}>
                  {index % 3 === 0 ? "🍰" : index % 3 === 1 ? "🥞" : "🍲"}
                </Text>
                <Text numberOfLines={2} style={s.recipeTitle}>
                  {recipe.title}
                </Text>
                <Text style={s.recipeMeta}>
                  {recipe.preparationTime} dk · {recipe.servings} porsiyon
                </Text>
              </Pressable>
            ))}
          </ScrollView>
        )}
      </ScrollView>
    </AppShell>
  );
}
