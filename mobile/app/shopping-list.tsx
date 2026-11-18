import { useCallback, useEffect, useMemo, useState } from "react";
import { Alert, Pressable, ScrollView, Text, View } from "react-native";
import { api } from "../src/api";
import { AppShell, Button, Field, Header, colors, s } from "../src/ui";

type ShoppingItem = {
  id: string; name: string; category: string; requiredQuantity: number;
  pantryCoveredQuantity: number; toBuyQuantity: number; purchasedQuantity: number;
  unit: string; checked: boolean; version: number;
};
type ShoppingList = {
  id: string; title: string; sourceType: string; status: string;
  items: ShoppingItem[]; suggestions: { title: string; reason: string; servings: number }[];
  costEstimateAvailable: boolean; estimatedTotal: number | null;
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
  const [lists, setLists] = useState<ShoppingList[]>([]);
  const [active, setActive] = useState<ShoppingList | null>(null);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [title, setTitle] = useState("Alışveriş önerilerim");
  const [itemName, setItemName] = useState("");
  const [itemQuantity, setItemQuantity] = useState("1");
  const [itemUnit, setItemUnit] = useState("adet");
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    const savedLists: ShoppingList[] = await api("/shopping-lists");
    setLists(savedLists);
    if (activeId) {
      const latest = savedLists.find((item) => item.id === activeId);
      setActive(latest || null);
    } else if (savedLists.length > 0) {
      setActiveId(savedLists[0].id);
      setActive(savedLists[0]);
    }
  }, [activeId]);

  useEffect(() => {
    refresh().catch((error) => Alert.alert("Alışveriş verileri yüklenemedi", error.message));
  }, [refresh]);

  const createPersonalized = async () => {
    setLoading(true);
    try {
      const created: ShoppingList = await api("/shopping-lists", {
        method: "POST",
        body: JSON.stringify({ title: title.trim() || "Alışveriş önerilerim", personalized: true }),
      });
      setActive(created);
      setActiveId(created.id);
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
      setActiveId(detail.id);
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
      setActiveId(null);
      const savedLists: ShoppingList[] = await api("/shopping-lists");
      setLists(savedLists);
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
        <Header title="Alışveriş Asistanı" subtitle="Yemek tercihlerini öğrenir, kilerindeki eksikleri tamamlar" />

        <View style={s.card}>
          <Text style={s.eyebrow}>YENİ LİSTE</Text>
          <Text style={s.heading}>Geçmiş tercihlerine göre liste oluştur</Text>
          <Text style={s.recipeMeta}>
            AI daha önce yaptığın yemekleri, puanlarını ve Akıllı Kiler'deki ürünleri inceleyerek
            yapabileceğin yeni yemekleri seçer; eksik malzemeleri tek listede toplar.
          </Text>
          <Field value={title} onChangeText={setTitle} placeholder="Liste adı" />
          <Button onPress={createPersonalized} loading={loading}>Bana özel alışveriş listesi oluştur</Button>
          {loading && <Text style={s.recipeMeta}>Yemek geçmişin ve kilerin analiz ediliyor...</Text>}
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
              {active.suggestions?.length > 0 && (
                <View style={{ marginTop: 12, marginBottom: 8 }}>
                  <Text style={s.eyebrow}>AI'NIN ÖNERDİĞİ YEMEKLER</Text>
                  {active.suggestions.map((suggestion, index) => (
                    <View key={index} style={{ paddingVertical: 8, borderTopWidth: 1, borderTopColor: colors.line }}>
                      <Text style={{ color: colors.ink, fontWeight: "800" }}>{suggestion.title}</Text>
                      <Text style={s.recipeMeta}>{suggestion.reason} · {suggestion.servings} porsiyon</Text>
                    </View>
                  ))}
                </View>
              )}
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
