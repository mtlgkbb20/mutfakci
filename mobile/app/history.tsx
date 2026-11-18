import { useCallback, useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  Modal,
  Platform,
  Pressable,
  SectionList,
  Text,
  View,
} from "react-native";
import { useRouter } from "expo-router";
import DateTimePicker from "@expo/ui/community/datetime-picker";
import { api } from "../src/api";
import { AppShell, Button, Header, s, colors } from "../src/ui";

type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
};
type RecoveryItem = {
  id: string;
  recipeName?: string | null;
  status: string;
  createdAt?: string;
};
type RecipeItem = {
  id: string;
  title: string;
  createdAt?: string;
};
type HistoryRow =
  | { kind: "recovery"; item: RecoveryItem }
  | { kind: "recipe"; item: RecipeItem };

const statusLabels: Record<string, string> = {
  TEXT_FALLBACK_COMPLETE: "Metin önerisi hazır",
  TEXT_FALLBACK_UNAVAILABLE: "Metin önerisi alınamadı",
  CORRECTION_PROPOSED: "Düzeltme önerildi",
  RECOVERY_OFFERED: "Kurtarma seçeneği sunuldu",
  AWAITING_FOLLOW_UP: "Takip fotoğrafı bekleniyor",
  FOLLOW_UP_RECEIVED: "Takip fotoğrafı alındı",
  READY_TO_CONTINUE: "Tarife devam edilebilir",
  IMAGE_REJECTED: "Fotoğraf değerlendirilemedi",
  ANALYSIS_REJECTED: "Analiz güvenilir bulunmadı",
  ANALYSIS_INCOMPLETE: "Analiz tamamlanamadı",
  VISION_PROVIDER_UNAVAILABLE: "Görsel analiz kullanılamadı",
  REMAKE_RECOMMENDED: "Bileşeni yeniden yapmak önerildi",
};

function parseJson(value?: string | null): any {
  if (!value) return null;
  try { return JSON.parse(value); } catch { return null; }
}
function formatDate(value?: string) {
  if (!value) return "Tarih bilgisi yok";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Tarih bilgisi yok" : date.toLocaleDateString("tr-TR", {
    day: "2-digit", month: "long", year: "numeric",
  });
}
function dateRangeQuery(from: Date | null, to: Date | null) {
  if (!from || !to) return "";
  const start = new Date(from.getFullYear(), from.getMonth(), from.getDate());
  const endExclusive = new Date(to.getFullYear(), to.getMonth(), to.getDate() + 1);
  const end = new Date(endExclusive.getTime() - 1);
  return `&from=${encodeURIComponent(start.toISOString())}&to=${encodeURIComponent(end.toISOString())}`;
}
function formatSelectedDate(value: Date | null) {
  return value ? value.toLocaleDateString("tr-TR", {
    day: "2-digit", month: "long", year: "numeric",
  }) : "Tarih seç";
}

export default function History() {
  const router = useRouter();
  const [recoveries, setRecoveries] = useState<RecoveryItem[]>([]);
  const [recipes, setRecipes] = useState<RecipeItem[]>([]);
  const [recoveryPage, setRecoveryPage] = useState(0);
  const [recipePage, setRecipePage] = useState(0);
  const [recoveryHasMore, setRecoveryHasMore] = useState(false);
  const [recipeHasMore, setRecipeHasMore] = useState(false);
  const [recoveryTotal, setRecoveryTotal] = useState(0);
  const [recipeTotal, setRecipeTotal] = useState(0);
  const [showAllList, setShowAllList] = useState<"recovery" | "recipe" | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [recoveryError, setRecoveryError] = useState("");
  const [recipeError, setRecipeError] = useState("");
  const [fromDate, setFromDate] = useState<Date | null>(null);
  const [toDate, setToDate] = useState<Date | null>(null);
  const [appliedFrom, setAppliedFrom] = useState<Date | null>(null);
  const [appliedTo, setAppliedTo] = useState<Date | null>(null);
  const [datePickerTarget, setDatePickerTarget] = useState<"from" | "to" | null>(null);
  const [draftDate, setDraftDate] = useState(new Date());
  const [filterError, setFilterError] = useState("");
  const [selectedRecovery, setSelectedRecovery] = useState<any | null>(null);
  const [selectedRecipe, setSelectedRecipe] = useState<any | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState("");

  const loadHistory = useCallback(async (from: Date | null, to: Date | null) => {
    setLoading(true);
    setRecoveryError("");
    setRecipeError("");
    const range = dateRangeQuery(from, to);
    const [recoveryResult, recipeResult] = await Promise.allSettled([
      api(`/recovery-history/page?page=0&size=20${range}`) as Promise<PageResponse<RecoveryItem>>,
      api(`/recipe-history?page=0&size=20${range}`) as Promise<PageResponse<RecipeItem>>,
    ]);
    if (recoveryResult.status === "fulfilled") {
      setRecoveries(recoveryResult.value.content || []);
      setRecoveryPage(recoveryResult.value.page || 0);
      setRecoveryHasMore(!!recoveryResult.value.hasNext);
      setRecoveryTotal(recoveryResult.value.totalElements ?? recoveryResult.value.content?.length ?? 0);
    } else {
      setRecoveries([]);
      setRecoveryHasMore(false);
      setRecoveryTotal(0);
      setRecoveryError(recoveryResult.reason?.message || "Kurtarma geçmişi yüklenemedi.");
    }
    if (recipeResult.status === "fulfilled") {
      setRecipes(recipeResult.value.content || []);
      setRecipePage(recipeResult.value.page || 0);
      setRecipeHasMore(!!recipeResult.value.hasNext);
      setRecipeTotal(recipeResult.value.totalElements ?? recipeResult.value.content?.length ?? 0);
    } else {
      setRecipes([]);
      setRecipeHasMore(false);
      setRecipeTotal(0);
      setRecipeError(recipeResult.reason?.message || "Tarif geçmişi yüklenemedi.");
    }
    setLoading(false);
  }, []);

  useEffect(() => { loadHistory(null, null); }, [loadHistory]);

  const applyDateFilter = async () => {
    setFilterError("");
    if (!fromDate || !toDate) {
      setFilterError("Başlangıç ve bitiş tarihlerini seç.");
      return;
    }
    const start = new Date(fromDate.getFullYear(), fromDate.getMonth(), fromDate.getDate());
    const end = new Date(toDate.getFullYear(), toDate.getMonth(), toDate.getDate());
    if (start.getTime() > end.getTime()) {
      setFilterError("Başlangıç tarihi bitiş tarihinden sonra olamaz.");
      return;
    }
    setAppliedFrom(fromDate);
    setAppliedTo(toDate);
    await loadHistory(fromDate, toDate);
  };

  const openDatePicker = (target: "from" | "to") => {
    setDraftDate((target === "from" ? fromDate : toDate) || new Date());
    setDatePickerTarget(target);
  };

  const commitDateSelection = (date: Date) => {
    if (datePickerTarget === "from") setFromDate(date);
    if (datePickerTarget === "to") setToDate(date);
    setDatePickerTarget(null);
  };

  const clearDateFilter = async () => {
    setFromDate(null);
    setToDate(null);
    setAppliedFrom(null);
    setAppliedTo(null);
    setFilterError("");
    await loadHistory(null, null);
  };

  const loadMore = async (target: "recovery" | "recipe") => {
    if (loadingMore || (target === "recovery" ? !recoveryHasMore : !recipeHasMore)) return;
    setLoadingMore(true);
    const range = dateRangeQuery(appliedFrom, appliedTo);
    try {
      if (target === "recovery") {
        const next = await api(`/recovery-history/page?page=${recoveryPage + 1}&size=20${range}`) as PageResponse<RecoveryItem>;
        setRecoveries(current => [...current, ...next.content]);
        setRecoveryPage(next.page);
        setRecoveryHasMore(next.hasNext);
        setRecoveryTotal(next.totalElements ?? recoveryTotal);
      } else {
        const next = await api(`/recipe-history?page=${recipePage + 1}&size=20${range}`) as PageResponse<RecipeItem>;
        setRecipes(current => [...current, ...next.content]);
        setRecipePage(next.page);
        setRecipeHasMore(next.hasNext);
        setRecipeTotal(next.totalElements ?? recipeTotal);
      }
    } catch (error: any) {
      if (target === "recovery") setRecoveryError(error.message || "Kurtarma kayıtları yüklenemedi.");
      else setRecipeError(error.message || "Tarif kayıtları yüklenemedi.");
    } finally {
      setLoadingMore(false);
    }
  };

  const openRecovery = async (item: RecoveryItem) => {
    setSelectedRecipe(null);
    setSelectedRecovery(item);
    setDetailLoading(true);
    setDetailError("");
    try {
      setSelectedRecovery(await api(`/recovery-history/${item.id}`));
    } catch (error: any) {
      setDetailError(error.message || "Kurtarma ayrıntısı yüklenemedi.");
    } finally { setDetailLoading(false); }
  };

  const openRecipe = async (item: RecipeItem) => {
    setSelectedRecovery(null);
    setSelectedRecipe(item);
    setDetailLoading(true);
    setDetailError("");
    try {
      setSelectedRecipe(await api(`/recipes/${item.id}`));
    } catch (error: any) {
      setDetailError(error.message || "Tarif ayrıntısı yüklenemedi.");
    } finally { setDetailLoading(false); }
  };

  const sections = useMemo(() => [
    { key: "recovery", title: `TARİF KURTARMA · ${recoveryTotal}`, data: recoveries.slice(0, 5).map(item => ({ kind: "recovery", item } as HistoryRow)) },
    { key: "recipes", title: `TARİF GEÇMİŞİ · ${recipeTotal}`, data: recipes.slice(0, 5).map(item => ({ kind: "recipe", item } as HistoryRow)) },
  ], [recoveries, recipes, recoveryTotal, recipeTotal]);

  const renderRow = ({ item, index }: { item: HistoryRow; index: number }) => {
    const isRecovery = item.kind === "recovery";
    const title = isRecovery
      ? item.item.recipeName || "İsimsiz tarif"
      : item.item.title || "İsimsiz tarif";
    const date = item.item.createdAt;
    return (
      <Pressable
        onPress={() => isRecovery ? openRecovery(item.item as RecoveryItem) : openRecipe(item.item as RecipeItem)}
        style={({ pressed }) => [styles.row, pressed && { opacity: 0.7 }]}
      >
        <View style={[styles.rowIcon, { backgroundColor: isRecovery ? "#F8EAE2" : "#E8EFE5" }]}>
          <Text style={{ fontSize: 20 }}>{isRecovery ? "🛟" : "🍲"}</Text>
        </View>
        <View style={{ flex: 1 }}>
          <Text style={styles.rowTitle} numberOfLines={1}>{title}</Text>
          <Text style={styles.rowDate}>{formatDate(date)}</Text>
        </View>
        <Text style={{ color: colors.orange, fontSize: 24, fontWeight: "700" }}>›</Text>
      </Pressable>
    );
  };

  return (
    <AppShell>
      <SectionList
        sections={sections}
        keyExtractor={item => `${item.kind}-${item.item.id}`}
        renderItem={renderRow}
        renderSectionHeader={({ section }) => (
          <View style={styles.sectionHeader}>
            <Text style={s.eyebrow}>{section.title}</Text>
          </View>
        )}
        renderSectionFooter={({ section }) => {
          const target = section.key === "recovery" ? "recovery" : "recipe";
          const total = target === "recovery" ? recoveryTotal : recipeTotal;
          const hasMore = target === "recovery" ? recoveryHasMore : recipeHasMore;
          return total > 5 || hasMore ? (
            <Pressable onPress={() => setShowAllList(target)} style={styles.viewAllButton}>
              <Text style={styles.viewAllText}>Tümünü gör ({total})  ›</Text>
            </Pressable>
          ) : null;
        }}
        ListHeaderComponent={
          <View>
            <Header title="Geçmişim" subtitle="Yemeklerini ve tariflerini tarihe göre bul" />
            <Pressable style={s.card} onPress={() => router.push("/cooking-journal")}>
              <Text style={s.eyebrow}>PİŞİRME GÜNLÜĞÜ</Text>
              <Text style={s.heading}>Denemelerini kaydet ve karşılaştır</Text>
              <Text style={[s.body, { marginTop: 6 }]}>Puanların, notların ve geçmiş pişirme kayıtların.</Text>
              <Text style={[s.smallLink, { marginTop: 12 }]}>Günlüğümü aç →</Text>
            </Pressable>
            <View style={s.card}>
              <Text style={s.eyebrow}>TARİHE GÖRE FİLTRELE</Text>
              <Text style={s.body}>Başlangıç ve bitiş tarihini seçerek o dönemdeki kayıtları getir.</Text>
              <View style={styles.dateRangeRow}>
                <Pressable onPress={() => openDatePicker("from")} style={styles.dateButton}>
                  <Text style={styles.dateButtonLabel}>BAŞLANGIÇ</Text>
                  <Text style={styles.dateButtonValue}>📅  {formatSelectedDate(fromDate)}</Text>
                </Pressable>
                <Pressable onPress={() => openDatePicker("to")} style={styles.dateButton}>
                  <Text style={styles.dateButtonLabel}>BİTİŞ</Text>
                  <Text style={styles.dateButtonValue}>📅  {formatSelectedDate(toDate)}</Text>
                </Pressable>
              </View>
              {filterError ? <Text style={s.error}>{filterError}</Text> : null}
              <View style={{ flexDirection: "row", gap: 10 }}>
                <View style={{ flex: 1 }}><Button onPress={applyDateFilter}>Filtrele</Button></View>
                <View style={{ flex: 1 }}><Button secondary onPress={clearDateFilter}>Temizle</Button></View>
              </View>
              {appliedFrom && appliedTo ? (
                <Text style={styles.appliedFilter}>{formatSelectedDate(appliedFrom)} – {formatSelectedDate(appliedTo)} aralığı gösteriliyor</Text>
              ) : null}
            </View>
            {loading ? <ActivityIndicator color={colors.orange} size="large" style={{ marginVertical: 18 }} /> : null}
            {recoveryError ? <Text style={s.error}>{recoveryError}</Text> : null}
            {recipeError ? <Text style={s.error}>{recipeError}</Text> : null}
          </View>
        }
        ListEmptyComponent={!loading ? (
          <View style={s.card}>
            <Text style={s.heading}>Bu aralıkta kayıt bulunamadı</Text>
            <Text style={[s.body, { marginTop: 6 }]}>Farklı bir tarih aralığı deneyebilirsin.</Text>
          </View>
        ) : undefined}
        ListFooterComponent={<View style={{ height: 24 }} />}
        stickySectionHeadersEnabled={false}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={s.scroll}
        initialNumToRender={12}
        maxToRenderPerBatch={12}
        windowSize={7}
      />

      {Platform.OS === "ios" && datePickerTarget ? (
        <Modal visible transparent animationType="slide" onRequestClose={() => setDatePickerTarget(null)}>
          <View style={styles.modalBackdrop}>
            <View style={styles.datePickerSheet}>
              <View style={styles.modalHeader}>
                <Text style={s.heading}>{datePickerTarget === "from" ? "Başlangıç tarihi" : "Bitiş tarihi"}</Text>
                <Pressable onPress={() => setDatePickerTarget(null)}>
                  <Text style={{ color: colors.muted, fontSize: 28 }}>×</Text>
                </Pressable>
              </View>
              <DateTimePicker
                value={draftDate}
                mode="date"
                display="inline"
                locale="tr-TR"
                minimumDate={datePickerTarget === "to" ? fromDate || undefined : undefined}
                maximumDate={datePickerTarget === "from" ? toDate || new Date() : new Date()}
                onValueChange={(_event, selectedDate) => {
                  if (selectedDate) setDraftDate(selectedDate);
                }}
              />
              <Button onPress={() => commitDateSelection(draftDate)}>Tarihi seç</Button>
            </View>
          </View>
        </Modal>
      ) : null}
      {Platform.OS === "android" && datePickerTarget ? (
        <DateTimePicker
          value={draftDate}
          mode="date"
          presentation="dialog"
          minimumDate={datePickerTarget === "to" ? fromDate || undefined : undefined}
          maximumDate={datePickerTarget === "from" ? toDate || new Date() : new Date()}
          onValueChange={(_event, selectedDate) => {
            if (selectedDate) commitDateSelection(selectedDate);
          }}
          onDismiss={() => setDatePickerTarget(null)}
        />
      ) : null}

      <Modal
        visible={showAllList !== null}
        transparent
        animationType="slide"
        onRequestClose={() => setShowAllList(null)}
      >
        <View style={styles.modalBackdrop}>
          <View style={styles.modalSheet}>
            <View style={styles.modalHeader}>
              <View style={{ flex: 1 }}>
                <Text style={s.eyebrow}>{showAllList === "recovery" ? "TARİF KURTARMA" : "TARİF GEÇMİŞİ"}</Text>
                <Text style={s.heading}>Tüm kayıtlar</Text>
              </View>
              <Pressable onPress={() => setShowAllList(null)}>
                <Text style={{ color: colors.muted, fontSize: 28 }}>×</Text>
              </Pressable>
            </View>
            <FlatList
              data={showAllList === "recovery" ? recoveries : recipes}
              keyExtractor={item => showAllList + "-" + item.id}
              renderItem={({ item }) => renderRow({
                item: showAllList === "recovery"
                  ? { kind: "recovery", item: item as RecoveryItem }
                  : { kind: "recipe", item: item as RecipeItem },
                index: 0,
              })}
              ListEmptyComponent={<Text style={s.body}>Bu aralıkta kayıt bulunamadı.</Text>}
              ListFooterComponent={
                (showAllList === "recovery" ? recoveryHasMore : recipeHasMore) ? (
                  <Button onPress={() => loadMore(showAllList!)} loading={loadingMore}>Daha fazla yükle</Button>
                ) : <View style={{ height: 16 }} />
              }
              showsVerticalScrollIndicator={false}
            />
          </View>
        </View>
      </Modal>

      <Modal
        visible={!!selectedRecovery || !!selectedRecipe}
        transparent
        animationType="slide"
        onRequestClose={() => { setSelectedRecovery(null); setSelectedRecipe(null); }}
      >
        <View style={styles.modalBackdrop}>
          <View style={styles.modalSheet}>
            <View style={styles.modalHeader}>
              <Text style={s.heading} numberOfLines={2}>
                {selectedRecovery?.recipeName || selectedRecipe?.title || "Kayıt detayı"}
              </Text>
              <Pressable onPress={() => { setSelectedRecovery(null); setSelectedRecipe(null); }}>
                <Text style={{ color: colors.muted, fontSize: 28 }}>×</Text>
              </Pressable>
            </View>
            {detailLoading ? <ActivityIndicator color={colors.orange} size="large" style={{ margin: 24 }} /> : null}
            {detailError ? <Text style={s.error}>{detailError}</Text> : null}
            {!detailLoading && selectedRecovery ? (
              <SectionList
                sections={[{ title: "Support", data: [selectedRecovery] }]}
                keyExtractor={() => selectedRecovery.id}
                renderItem={() => {
                  const assessment = parseJson(selectedRecovery.assessment);
                  const action = parseJson(selectedRecovery.action);
                  return (
                    <View>
                      <Text style={s.recipeMeta}>{formatDate(selectedRecovery.createdAt)} · {statusLabels[selectedRecovery.status] || selectedRecovery.status}</Text>
                      {selectedRecovery.problemDescription ? <><Text style={styles.detailLabel}>SORUN</Text><Text style={s.body}>{selectedRecovery.problemDescription}</Text></> : null}
                      {assessment?.advice ? <><Text style={styles.detailLabel}>METİN ÖNERİSİ</Text><Text style={s.body}>{assessment.advice}</Text></> : null}
                      {assessment?.caution ? <><Text style={styles.detailLabel}>DİKKAT</Text><Text style={s.body}>{assessment.caution}</Text></> : null}
                      {assessment?.reason ? <><Text style={styles.detailLabel}>DEĞERLENDİRME</Text><Text style={s.body}>{assessment.reason}</Text></> : null}
                      {action?.instruction ? <><Text style={styles.detailLabel}>ÖNERİLEN DÜZELTME</Text><Text style={s.body}>{action.instruction}</Text></> : null}
                      {selectedRecovery.executions?.length ? (
                        <><Text style={styles.detailLabel}>AI İŞLEMLERİ</Text>{selectedRecovery.executions.map((execution: any, i: number) => (
                          <Text key={i} style={s.recipeMeta}>{execution.operation} · {execution.provider} · {execution.status}</Text>
                        ))}</>
                      ) : null}
                    </View>
                  );
                }}
                renderSectionHeader={() => null}
                showsVerticalScrollIndicator={false}
              />
            ) : null}
            {!detailLoading && selectedRecipe ? (
              <SectionList
                sections={[{ title: "Tarif", data: [selectedRecipe] }]}
                keyExtractor={() => selectedRecipe.id}
                renderItem={() => {
                  const ingredients = parseJson(selectedRecipe.ingredients);
                  const stages = parseJson(selectedRecipe.stages);
                  return (
                    <View>
                      <Text style={s.recipeMeta}>{formatDate(selectedRecipe.createdAt)} · {selectedRecipe.preparationTime} dk hazırlık · {selectedRecipe.cookingTime} dk pişirme · {selectedRecipe.servings} porsiyon</Text>
                      {selectedRecipe.description ? <Text style={[s.body, { marginTop: 12 }]}>{selectedRecipe.description}</Text> : null}
                      {Array.isArray(ingredients) ? <><Text style={styles.detailLabel}>MALZEMELER</Text>{ingredients.map((ingredient: any, i: number) => <Text key={i} style={s.body}>• {ingredient.name || ingredient.ingredient || String(ingredient)}</Text>)}</> : null}
                      {Array.isArray(stages) ? <><Text style={styles.detailLabel}>HAZIRLANIŞ</Text>{stages.map((stage: any, i: number) => <Text key={i} style={[s.body, { marginBottom: 8 }]}>{i + 1}. {stage.title || stage.instructions || stage.instruction || String(stage)}</Text>)}</> : null}
                    </View>
                  );
                }}
                renderSectionHeader={() => null}
                showsVerticalScrollIndicator={false}
              />
            ) : null}
          </View>
        </View>
      </Modal>
    </AppShell>
  );
}

const styles = {
  sectionHeader: { paddingTop: 8, paddingBottom: 4 },
  row: { flexDirection: "row" as const, alignItems: "center" as const, backgroundColor: colors.white,
    borderWidth: 1, borderColor: colors.line, borderRadius: 17, padding: 14, marginBottom: 9 },
  rowIcon: { width: 42, height: 42, borderRadius: 14, alignItems: "center" as const,
    justifyContent: "center" as const, marginRight: 12 },
  rowTitle: { color: colors.ink, fontSize: 15, fontWeight: "800" as const },
  rowDate: { color: colors.muted, fontSize: 12, marginTop: 4 },
  dateRangeRow: { flexDirection: "row" as const, gap: 10, marginTop: 12, marginBottom: 12 },
  dateButton: { flex: 1, backgroundColor: "#FBFCF9", borderColor: colors.line, borderWidth: 1,
    borderRadius: 13, paddingHorizontal: 12, paddingVertical: 13 },
  dateButtonLabel: { color: colors.muted, fontSize: 10, fontWeight: "800" as const, letterSpacing: 1 },
  dateButtonValue: { color: colors.ink, fontSize: 13, fontWeight: "700" as const, marginTop: 7 },
  datePickerSheet: { backgroundColor: colors.cream, borderTopLeftRadius: 26, borderTopRightRadius: 26,
    padding: 20, paddingBottom: 34 },

  appliedFilter: { color: colors.green, fontSize: 12, fontWeight: "700" as const, marginTop: 10 },
  viewAllButton: { alignSelf: "flex-start" as const, paddingVertical: 10, paddingHorizontal: 4, marginBottom: 10 },
  viewAllText: { color: colors.orange, fontSize: 14, fontWeight: "800" as const },
  modalBackdrop: { flex: 1, backgroundColor: "rgba(16, 26, 20, 0.48)", justifyContent: "flex-end" as const },
  modalSheet: { maxHeight: "88%" as const, minHeight: 280, backgroundColor: colors.cream,
    borderTopLeftRadius: 26, borderTopRightRadius: 26, padding: 20, paddingBottom: 34 },
  modalHeader: { flexDirection: "row" as const, alignItems: "center" as const,
    justifyContent: "space-between" as const, gap: 12, marginBottom: 12 },
  detailLabel: { color: colors.green, fontSize: 11, fontWeight: "800" as const,
    letterSpacing: 1.2, marginTop: 18, marginBottom: 6 },
};
