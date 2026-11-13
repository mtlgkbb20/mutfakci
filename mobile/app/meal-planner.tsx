import { useCallback, useEffect, useMemo, useState } from "react";
import { Alert, Pressable, ScrollView, Text, View } from "react-native";
import { api } from "../src/api";
import { AppShell, Button, Field, Header, colors, s } from "../src/ui";

type RecipeSummary = {
  id: string;
  title: string;
  category: string;
  servings: number;
  estimatedTotalMinutes: number;
  ingredients: { name: string; quantity: number; unit: string; preparation: string }[];
};
type PlanEntry = {
  id: string;
  date: string;
  mealSlot: "BREAKFAST" | "LUNCH" | "DINNER";
  recipeCatalogId: string;
  servings: number;
  note?: string;
  recipe: RecipeSummary;
};
type Plan = {
  id: string;
  weekStart: string;
  servings: number;
  mealSlots: string[];
  entries: PlanEntry[];
  warnings: string[];
};
type ShoppingLine = {
  name: string;
  requiredQuantity: number;
  pantryCoveredQuantity: number;
  toBuyQuantity: number;
  unit: string;
};

const slotLabels: Record<string, string> = {
  BREAKFAST: "Kahvaltı",
  LUNCH: "Öğle",
  DINNER: "Akşam",
};

function currentMonday() {
  const date = new Date();
  date.setHours(12, 0, 0, 0);
  date.setDate(date.getDate() - ((date.getDay() + 6) % 7));
  return date.toISOString().slice(0, 10);
}

function formatDate(value: string) {
  return new Date(value + "T12:00:00").toLocaleDateString("tr-TR", {
    weekday: "long",
    day: "numeric",
    month: "long",
  });
}

export default function MealPlannerScreen() {
  const [weekStart, setWeekStart] = useState(currentMonday());
  const [servings, setServings] = useState("2");
  const [maxMinutes, setMaxMinutes] = useState("60");
  const [budget, setBudget] = useState("");
  const [goal, setGoal] = useState("");
  const [restrictions, setRestrictions] = useState("");
  const [slots, setSlots] = useState(["BREAKFAST", "LUNCH", "DINNER"]);
  const [plan, setPlan] = useState<Plan | null>(null);
  const [shopping, setShopping] = useState<ShoppingLine[]>([]);
  const [loading, setLoading] = useState(false);
  const [shoppingLoading, setShoppingLoading] = useState(false);
  const [candidateLoading, setCandidateLoading] = useState(false);
  const [candidates, setCandidates] = useState<RecipeSummary[]>([]);
  const [changingEntry, setChangingEntry] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    const saved: Plan[] = await api("/meal-plans");
    if (saved.length > 0) {
      setPlan(saved[0]);
      setWeekStart(saved[0].weekStart);
    }
  }, []);

  useEffect(() => {
    refresh().catch((error) => Alert.alert("Planlar yüklenemedi", error.message));
  }, [refresh]);

  const toggleSlot = (slot: string) => {
    setSlots((current) =>
      current.includes(slot)
        ? current.filter((value) => value !== slot)
        : [...current, slot],
    );
  };

  const createPlan = async () => {
    if (slots.length === 0) {
      Alert.alert("Öğün seç", "En az bir öğün türü seçmelisin.");
      return;
    }
    const servingCount = Number(servings);
    const preparationLimit = Number(maxMinutes);
    if (!Number.isInteger(servingCount) || servingCount < 1 || servingCount > 30 ||
        !Number.isInteger(preparationLimit) || preparationLimit < 1) {
      Alert.alert("Bilgileri kontrol et", "Porsiyon ve süre değerleri geçerli olmalı.");
      return;
    }
    setLoading(true);
    try {
      const created = await api("/meal-plans", {
        method: "POST",
        body: JSON.stringify({
          weekStart,
          servings: servingCount,
          mealSlots: slots,
          maxPreparationMinutes: preparationLimit,
          weeklyBudget: budget.trim() ? Number(budget) : null,
          currency: "TRY",
          dietaryRestrictions: restrictions.split(",").map((value) => value.trim()).filter(Boolean),
          goal: goal.trim() || null,
        }),
      });
      setPlan(created);
      setShopping([]);
      setChangingEntry(null);
    } catch (error: any) {
      Alert.alert("Plan oluşturulamadı", error.message);
    } finally {
      setLoading(false);
    }
  };

  const loadCandidates = async () => {
    if (candidates.length > 0) return;
    setCandidateLoading(true);
    try {
      if (!plan) return;
      setCandidates(await api("/meal-plans/" + plan.id + "/recipes"));
    } catch (error: any) {
      Alert.alert("Alternatif tarifler getirilemedi", error.message);
    } finally {
      setCandidateLoading(false);
    }
  };

  const replaceMeal = async (entry: PlanEntry, recipe: RecipeSummary) => {
    if (!plan) return;
    try {
      const updated = await api(
        "/meal-plans/" + plan.id + "/entries/" + entry.id,
        {
          method: "PATCH",
          body: JSON.stringify({
            recipeCatalogId: recipe.id,
            servings: entry.servings,
            note: entry.note || null,
          }),
        },
      );
      setPlan(updated);
      setChangingEntry(null);
      setShopping([]);
    } catch (error: any) {
      Alert.alert("Öğün değiştirilemedi", error.message);
    }
  };

  const loadShoppingList = async () => {
    if (!plan) return;
    setShoppingLoading(true);
    try {
      const result = await api("/meal-plans/" + plan.id + "/shopping-list");
      setShopping(result.items || []);
      if (result.warnings?.length) Alert.alert("Alışveriş listesi notu", result.warnings.join("\n"));
    } catch (error: any) {
      Alert.alert("Alışveriş listesi oluşturulamadı", error.message);
    } finally {
      setShoppingLoading(false);
    }
  };

  const groupedEntries = useMemo(() => {
    if (!plan) return [];
    const groups = new Map<string, PlanEntry[]>();
    for (const entry of plan.entries) {
      groups.set(entry.date, [...(groups.get(entry.date) || []), entry]);
    }
    return [...groups.entries()];
  }, [plan]);

  return (
    <AppShell>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={s.scroll}>
        <Header title="Haftalık Plan" subtitle="Kilerindeki malzemeleri de hesaba katalım" />

        <View style={s.card}>
          <Text style={s.eyebrow}>PLANINI OLUŞTUR</Text>
          <Text style={s.heading}>Bu hafta nasıl beslenmek istiyorsun?</Text>
          <Field value={weekStart} onChangeText={setWeekStart} placeholder="Hafta başlangıcı (YYYY-AA-GG)" />
          <View style={{ flexDirection: "row", gap: 8 }}>
            <Field value={servings} onChangeText={setServings} keyboardType="number-pad" placeholder="Kişi sayısı" style={{ flex: 1 }} />
            <Field value={maxMinutes} onChangeText={setMaxMinutes} keyboardType="number-pad" placeholder="Maks. dakika" style={{ flex: 1 }} />
          </View>
          <Field value={budget} onChangeText={setBudget} keyboardType="decimal-pad" placeholder="Haftalık bütçe (TL, isteğe bağlı)" />
          <Field value={goal} onChangeText={setGoal} placeholder="Hedef (örn. pratik ve dengeli öğünler)" />
          <Field value={restrictions} onChangeText={setRestrictions} placeholder="Beslenme kısıtları (virgülle ayır)" />
          <Text style={[s.recipeMeta, { marginTop: 12 }]}>Planlanacak öğünler</Text>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginTop: 8 }}>
            {Object.entries(slotLabels).map(([slot, label]) => {
              const selected = slots.includes(slot);
              return (
                <Pressable key={slot} onPress={() => toggleSlot(slot)}
                  style={{ paddingVertical: 10, paddingHorizontal: 14, borderRadius: 18, backgroundColor: selected ? colors.peach : colors.sand, borderWidth: 1, borderColor: selected ? colors.orange : colors.line }}>
                  <Text style={{ color: colors.ink, fontWeight: "800" }}>{selected ? "✓ " : ""}{label}</Text>
                </Pressable>
              );
            })}
          </View>
          <Button onPress={createPlan} loading={loading}>Haftalık plan oluştur</Button>
        </View>

        {plan && (
          <>
            <View style={s.sectionRow}>
              <View>
                <Text style={s.eyebrow}>KAYITLI PLAN</Text>
                <Text style={s.heading}>{formatDate(plan.weekStart)} haftası</Text>
              </View>
              <Text style={s.recipeMeta}>{plan.entries.length} öğün</Text>
            </View>
            {plan.warnings?.map((warning, index) => (
              <Text key={index} style={[s.recipeMeta, { marginBottom: 8, color: colors.orange }]}>{warning}</Text>
            ))}
            {groupedEntries.map(([date, dayEntries]) => (
              <View key={date} style={s.card}>
                <Text style={s.eyebrow}>{formatDate(date)}</Text>
                {dayEntries.map((entry) => (
                  <View key={entry.id} style={{ paddingVertical: 12, borderTopWidth: 1, borderTopColor: colors.line }}>
                    <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
                      <View style={{ flex: 1, paddingRight: 10 }}>
                        <Text style={s.recipeMeta}>{slotLabels[entry.mealSlot]} · {entry.servings} porsiyon</Text>
                        <Text style={{ fontSize: 17, fontWeight: "800", color: colors.ink, marginTop: 3 }}>{entry.recipe.title}</Text>
                        <Text style={s.recipeMeta}>{entry.recipe.estimatedTotalMinutes} dakika</Text>
                      </View>
                      <Pressable onPress={() => { setChangingEntry(changingEntry === entry.id ? null : entry.id); loadCandidates(); }}>
                        <Text style={s.smallLink}>Değiştir</Text>
                      </Pressable>
                    </View>
                    {changingEntry === entry.id && (
                      <View style={{ marginTop: 10 }}>
                        {candidateLoading ? <Text style={s.body}>Tarifler yükleniyor...</Text> :
                          candidates.length === 0 ? <Text style={s.body}>Bu süre sınırında alternatif tarif yok.</Text> :
                          candidates.map((candidate) => (
                            <Pressable key={candidate.id} onPress={() => replaceMeal(entry, candidate)}
                              style={{ paddingVertical: 9, borderTopWidth: 1, borderTopColor: colors.line }}>
                              <Text style={{ color: colors.ink, fontWeight: "700" }}>{candidate.title} · {candidate.estimatedTotalMinutes} dk</Text>
                            </Pressable>
                          ))}
                      </View>
                    )}
                  </View>
                ))}
              </View>
            ))}
            <View style={s.card}>
              <Text style={s.eyebrow}>TEKİLLEŞTİRİLMİŞ ALIŞVERİŞ LİSTESİ</Text>
              <Text style={s.heading}>Eksik malzemeler</Text>
              <Button onPress={loadShoppingList} loading={shoppingLoading}>Alışveriş listesini oluştur</Button>
              {shopping.map((line, index) => (
                <View key={line.name + line.unit + index} style={{ paddingVertical: 10, borderTopWidth: 1, borderTopColor: colors.line }}>
                  <Text style={{ color: colors.ink, fontWeight: "800" }}>{line.name}</Text>
                  <Text style={s.recipeMeta}>Gerekli: {line.requiredQuantity} {line.unit} · Kilerde: {line.pantryCoveredQuantity} {line.unit}</Text>
                  <Text style={{ color: colors.green, fontWeight: "800", marginTop: 3 }}>Alınacak: {line.toBuyQuantity} {line.unit}</Text>
                </View>
              ))}
              {shopping.length === 0 && <Text style={[s.recipeMeta, { marginTop: 8 }]}>Liste henüz oluşturulmadı.</Text>}
            </View>
          </>
        )}
      </ScrollView>
    </AppShell>
  );
}
