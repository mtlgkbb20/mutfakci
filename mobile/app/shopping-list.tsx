import { useCallback, useEffect, useMemo, useState } from "react";
import { Alert, Pressable, ScrollView, Text, View } from "react-native";
import { api } from "../src/api";
import { AppShell, Button, Field, Header, colors, s } from "../src/ui";

type MealPlan = { id: string; weekStart: string; entries: { recipe: { title: string } }[] };
type ShoppingItem = {
  id: string; name: string; category: string; requiredQuantity: number;
  pantryCoveredQuantity: number; toBuyQuantity: number; purchasedQuantity: number;
  unit: string; checked: boolean; version: number;
};
type ShoppingList = {
  id: string; title: string; sourceType: string; status: string;
  items: ShoppingItem[]; costEstimateAvailable: boolean; estimatedTotal: number | null;
  warnings: string[];
};

const categoryLabels: Record<string, string> = {
  FRUITS_VEGETABLES: "Meyve & Sebze",
  DAIRY: "Süt & Kahvaltılık",
  MEAT_FISH: "Et & Balık",
  BAKING_PANTRY: "Temel Gıdalar",
  OILS_CONDIMENTS: "Yağ & Baharat",
  OTHER: "Diğer",
};

export default function ShoppingListScreen() {
  const [plans, setPlans] = useState<MealPlan[]>([]);
  const [lists, setLists] = useState<ShoppingList[]>([]);
  const [active, setActive] = useState<ShoppingList | null>(null);
  const [title, setTitle] = useState("Haftalık alışveriş");
  const [itemName, setItemName] = useState("");
  const [itemQuantity, setItemQuantity] = useState("1");
  const [itemUnit, setItemUnit] = useState("adet");
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    const [savedPlans, savedLists]: [MealPlan[], ShoppingList[]] = await Promise.all([
      api("/meal-plans"), api("/shopping-lists"),
    ]);
    setPlans(savedPlans);
    setLists(savedLists);
    if (active) {
      const latest = savedLists.find((item) => item.id === active.id);
      setActive(latest || null);
    } else if (savedLists.length > 0) {
      setActive(savedLists[0]);
    }
  }, [active]);

  useEffect(() => {
    refresh().catch((error) => Alert.alert("Alışveriş verileri yüklenemedi", error.message));
  }, [refresh]);

  const createFromPlan = async (planId: string) => {
    setLoading(true);
    try {
      const created: ShoppingList = await api("/shopping-lists", {
        method: "POST",
        body: JSON.stringify({ title: title.trim() || "Alışveriş listem", mealPlanId: planId }),
      });
      setActive(created);
      setLists((current) => [created, ...current.filter((item) => item.id !== created.id)]);
    } catch (error: any) {
      Alert.alert("Liste oluşturulamadı", error.message);
    } finally {
      setLoading(false);
    }
  };

  const selectList = async (listId: string) => {
    setLoading(true);
    try {
      const detail: ShoppingList = await api("/shopping-lists/" + listId);
      setActive(detail);
    } catch (error: any) {
      Alert.alert("Liste açılamadı", error.message);
    } finally {
      setLoading(false);
    }
  };

  const updateItem = async (item: ShoppingItem, checked: boolean) => {
    if (!active || active.status !== "ACTIVE") return;
    try {
      const updated: ShoppingList = await api(
        "/shopping-lists/" + active.id + "/items/" + item.id,
        {
          method: "PATCH",
          body: JSON.stringify({
            purchasedQuantity: checked ? item.toBuyQuantity : 0,
            checked,
          }),
        },
      );
      setActive(updated);
      setLists((current) => current.map((list) => list.id === updated.id ? updated : list));
    } catch (error: any) {
      Alert.alert("Ürün güncellenemedi", error.message);
    }
  };

  const addItem = async () => {
    if (!active || !itemName.trim()) return;
    const quantity = Number(itemQuantity);
    if (!Number.isFinite(quantity) || quantity <= 0) {
      Alert.alert("Miktarı kontrol et", "Miktar sıfırdan büyük olmalı.");
      return;
    }
    try {
      const updated: ShoppingList = await api("/shopping-lists/" + active.id + "/items", {
        method: "POST",
        body: JSON.stringify({ name: itemName.trim(), quantity, unit: itemUnit.trim() || "adet" }),
      });
      setActive(updated);
      setLists((current) => current.map((list) => list.id === updated.id ? updated : list));
      setItemName("");
      setItemQuantity("1");
    } catch (error: any) {
      Alert.alert("Ürün eklenemedi", error.message);
    }
  };

  const complete = async () => {
    if (!active) return;
    const selected = active.items.filter((item) => item.checked && item.purchasedQuantity > 0);
    if (selected.length === 0) {
      Alert.alert("Satın alınan ürün yok", "Önce aldığın ürünleri işaretle.");
      return;
    }
    setLoading(true);
    try {
      const operationKey = "mobile-" + Date.now() + "-" + Math.random().toString(36).slice(2, 9);
      await api("/shopping-lists/" + active.id + "/complete", {
        method: "POST", body: JSON.stringify({ operationKey }),
      });
      Alert.alert("Alışveriş tamamlandı", "Seçtiğin ürünler Akıllı Kiler'e eklendi.");
      setActive(null);
      await refresh();
    } catch (error: any) {
      Alert.alert("Alışveriş tamamlanamadı", error.message);
    } finally {
      setLoading(false);
    }
  };

  const groups = useMemo(() => {
    const result = new Map<string, ShoppingItem[]>();
    for (const item of active?.items || []) {
      result.set(item.category, [...(result.get(item.category) || []), item]);
    }
    return [...result.entries()];
  }, [active]);

  return (
    <AppShell>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={s.scroll}>
        <Header title="Alışveriş Asistanı" subtitle="Eksiklerini listele, alışverişini kilerinle tamamla" />

        <View style={s.card}>
          <Text style={s.eyebrow}>YENİ LİSTE</Text>
          <Text style={s.heading}>Neye göre alışveriş yapalım?</Text>
          <Field value={title} onChangeText={setTitle} placeholder="Liste adı" />
          {plans.length === 0 ? (
            <Text style={s.recipeMeta}>Kayıtlı haftalık plan bulunamadı. Önce Haftalık Plan ekranından plan oluştur.</Text>
          ) : plans.map((plan) => (
            <Pressable key={plan.id} onPress={() => createFromPlan(plan.id)}
              style={{ paddingVertical: 12, borderTopWidth: 1, borderTopColor: colors.line }}>
              <Text style={{ color: colors.ink, fontWeight: "800" }}>{plan.weekStart} haftası</Text>
              <Text style={s.recipeMeta}>{plan.entries.length} öğün · liste oluştur</Text>
            </Pressable>
          ))}
          {loading && <Text style={s.recipeMeta}>İşlem yapılıyor...</Text>}
        </View>

        {lists.length > 0 && (
          <View style={s.card}>
            <Text style={s.eyebrow}>KAYITLI LİSTELER</Text>
            {lists.map((list) => (
              <Pressable key={list.id} onPress={() => selectList(list.id)}
                style={{ paddingVertical: 10, borderTopWidth: 1, borderTopColor: colors.line }}>
                <Text style={{ color: colors.ink, fontWeight: "800" }}>{list.title}</Text>
                <Text style={s.recipeMeta}>{list.status === "COMPLETED" ? "Tamamlandı" : "Aktif"} · {list.items.length} ürün</Text>
              </Pressable>
            ))}
          </View>
        )}

        {active && (
          <>
            <View style={s.card}>
              <Text style={s.eyebrow}>{active.status === "COMPLETED" ? "TAMAMLANDI" : "ALIŞVERİŞ LİSTESİ"}</Text>
              <Text style={s.heading}>{active.title}</Text>
              {active.warnings?.map((warning, index) => (
                <Text key={index} style={[s.recipeMeta, { color: colors.orange, marginBottom: 6 }]}>{warning}</Text>
              ))}
              {groups.map(([category, items]) => (
                <View key={category} style={{ marginTop: 14 }}>
                  <Text style={s.eyebrow}>{categoryLabels[category] || category}</Text>
                  {items.map((item) => (
                    <Pressable key={item.id} disabled={active.status !== "ACTIVE"}
                      onPress={() => updateItem(item, !item.checked)}
                      style={{ flexDirection: "row", alignItems: "center", paddingVertical: 11, borderTopWidth: 1, borderTopColor: colors.line }}>
                      <Text style={{ width: 30, fontSize: 20, color: item.checked ? colors.orange : colors.muted }}>
                        {item.checked ? "☑" : "□"}
                      </Text>
                      <View style={{ flex: 1 }}>
                        <Text style={{ color: colors.ink, fontWeight: "800", textDecorationLine: item.checked ? "line-through" : "none" }}>{item.name}</Text>
                        <Text style={s.recipeMeta}>Alınacak: {item.toBuyQuantity} {item.unit} · Kilerde: {item.pantryCoveredQuantity} {item.unit}</Text>
                      </View>
                    </Pressable>
                  ))}
                </View>
              ))}
              {active.status === "ACTIVE" && (
                <>
                  <Text style={[s.eyebrow, { marginTop: 18 }]}>MANUEL ÜRÜN EKLE</Text>
                  <Field value={itemName} onChangeText={setItemName} placeholder="Ürün adı" />
                  <View style={{ flexDirection: "row", gap: 8 }}>
                    <Field value={itemQuantity} onChangeText={setItemQuantity} keyboardType="decimal-pad" placeholder="Miktar" style={{ flex: 1 }} />
                    <Field value={itemUnit} onChangeText={setItemUnit} placeholder="Birim" style={{ flex: 1 }} />
                  </View>
                  <Button secondary onPress={addItem}>Ürün ekle</Button>
                  <Button onPress={complete} loading={loading}>Alışverişi tamamla ve kileri güncelle</Button>
                </>
              )}
              <Text style={s.recipeMeta}>Tahmini maliyet: güvenilir fiyat verisi bulunmadığı için gösterilemiyor.</Text>
            </View>
          </>
        )}
      </ScrollView>
    </AppShell>
  );
}
