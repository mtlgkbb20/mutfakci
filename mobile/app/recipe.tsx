import { useLocalSearchParams, useRouter } from "expo-router";
import { useMemo } from "react";
import { Linking, ScrollView, Text } from "react-native";
import { api } from "../src/api";
import { Button, Screen, s } from "../src/ui";
export default function Recipe() {
  const router = useRouter();
  const { data } = useLocalSearchParams<{ data: string }>();
  const recipe = useMemo(() => JSON.parse(data || "{}"), [data]);
  const start = async () => {
    const session = await api("/cooking-sessions", {
      method: "POST",
      body: JSON.stringify({
        recipeId: recipe.id,
        desiredOutcome: recipe.title,
      }),
    });
    router.push({ pathname: "/recovery", params: { sessionId: session.id } });
  };
  return (
    <Screen>
      <ScrollView contentContainerStyle={s.scroll}>
        <Text style={s.eyebrow}>
          {recipe.sourceType === "CURATED" || recipe.sourceUrl ? "SABİT TARİF" : "ÖNERİLEN TARİF"}
        </Text>
        <Text style={s.title}>{recipe.title}</Text>
        <Text style={[s.body, { marginVertical: 12 }]}>{recipe.description}</Text>
        {(recipe.preparationTime > 0 || recipe.cookingTime > 0 || recipe.servings > 0) && (
          <Text style={s.body}>
            {recipe.preparationTime > 0 ? `${recipe.preparationTime} dk hazırlık` : ""}
            {recipe.cookingTime > 0 ? ` · ${recipe.cookingTime} dk pişirme` : ""}
            {recipe.servings > 0 ? ` · ${recipe.servings} porsiyon` : ""}
          </Text>
        )}
        {recipe.sourceUrl ? (
          <>
            <Text style={[s.recipeMeta, { marginTop: 12 }]}>
              Kaynak: {recipe.sourceName} · {recipe.sourceAuthor}
              {recipe.sourcePublishedAt ? ` · ${recipe.sourcePublishedAt}` : ""}
            </Text>
            <Button secondary onPress={() => Linking.openURL(recipe.sourceUrl)}>
              Orijinal tarifi aç
            </Button>
          </>
        ) : null}
        <Text style={[s.heading, { marginTop: 25 }]}>Malzemeler</Text>
        {JSON.parse(recipe.ingredients || "[]").map((x: any, index: number) => (
          <Text style={s.body} key={`ingredient-${index}`}>
            {x.quantity > 0 ? `${x.quantity} ${x.unit} ` : `${x.unit} `} {x.name}
            {x.preparation ? ` — ${x.preparation}` : ""}
          </Text>
        ))}
        <Text style={[s.heading, { marginTop: 25 }]}>Aşamalar</Text>
        {JSON.parse(recipe.stages || "[]").map((x: any, index: number) => (
          <Text style={[s.body, { marginTop: 10 }]} key={`stage-${index}`}>
            {x.order}. {x.title}: {x.instructions}
          </Text>
        ))}
        <Button onPress={start}>Pişirmeye başla</Button>
        <Button secondary onPress={() => router.back()}>Geri dön</Button>
      </ScrollView>
    </Screen>
  );
}
