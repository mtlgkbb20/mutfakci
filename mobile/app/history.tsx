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
export default function History() {
  const router = useRouter();
  const [recipes, setRecipes] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  useEffect(() => {
    api("/recipes")
      .then(setRecipes)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);
  return (
    <AppShell>
      <ScrollView
        showsVerticalScrollIndicator={false}
        contentContainerStyle={s.scroll}
      >
        <Header title="Tarif geçmişi" subtitle="Daha önce keşfettiklerin" />
        <View style={s.card}>
          <Text style={s.eyebrow}>MUTFAK GÜNLÜĞÜ</Text>
          <Text style={s.heading}>{recipes.length} tarif kaydedildi</Text>
          <Text style={[s.body, { marginTop: 6 }]}>
            Tariflerine dokunarak malzemeleri ve aşamaları yeniden açabilirsin.
          </Text>
        </View>
        {loading ? (
          <ActivityIndicator color={colors.orange} size="large" />
        ) : error ? (
          <Text style={s.error}>{error}</Text>
        ) : recipes.length === 0 ? (
          <View style={s.card}>
            <Text style={s.heading}>Henüz tarif yok</Text>
            <Text style={[s.body, { marginTop: 8 }]}>
              Dashboard’dan ilk tarifini oluştur.
            </Text>
          </View>
        ) : (
          recipes.map((recipe, index) => (
            <Pressable
              key={`history-recipe-${index}`}
              style={s.card}
              onPress={() =>
                router.push({
                  pathname: "/recipe",
                  params: { data: JSON.stringify(recipe) },
                })
              }
            >
              <View style={{ flexDirection: "row", alignItems: "center" }}>
                <Text style={{ fontSize: 38, marginRight: 15 }}>
                  {index % 3 === 0 ? "🍰" : index % 3 === 1 ? "🥞" : "🍲"}
                </Text>
                <View style={{ flex: 1 }}>
                  <Text style={s.heading} numberOfLines={2}>
                    {recipe.title}
                  </Text>
                  <Text style={[s.recipeMeta, { marginTop: 7 }]}>
                    {recipe.preparationTime} dk hazırlık · {recipe.cookingTime}{" "}
                    dk pişirme · {recipe.servings} porsiyon
                  </Text>
                </View>
                <Text style={{ fontSize: 25, color: colors.orange }}>›</Text>
              </View>
            </Pressable>
          ))
        )}
      </ScrollView>
    </AppShell>
  );
}
