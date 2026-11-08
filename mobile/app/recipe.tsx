import { useLocalSearchParams, useRouter } from "expo-router";
import { useMemo } from "react";
import { ScrollView, Text } from "react-native";
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
        <Text style={s.eyebrow}>ÖNERİLEN TARİF</Text>
        <Text style={s.title}>{recipe.title}</Text>
        <Text style={[s.body, { marginVertical: 12 }]}>
          {recipe.description}
        </Text>
        <Text style={s.body}>
          {recipe.preparationTime} dk hazırlık · {recipe.cookingTime} dk pişirme
          · {recipe.servings} porsiyon
        </Text>
        <Text style={[s.heading, { marginTop: 25 }]}>Malzemeler</Text>
        {JSON.parse(recipe.ingredients || "[]").map((x: any) => (
          <Text style={s.body} key={x.name}>
            • {x.quantity} {x.unit} {x.name}
          </Text>
        ))}
        <Text style={[s.heading, { marginTop: 25 }]}>Aşamalar</Text>
        {JSON.parse(recipe.stages || "[]").map((x: any) => (
          <Text style={[s.body, { marginTop: 10 }]} key={x.order}>
            {x.order}. {x.title}: {x.instructions}
          </Text>
        ))}
        <Button onPress={start}>Pişirmeye başla</Button>
        <Button secondary onPress={() => router.back()}>
          Geri dön
        </Button>
      </ScrollView>
    </Screen>
  );
}
