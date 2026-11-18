import { useLocalSearchParams, useRouter } from "expo-router";
import { useState } from "react";
import { Image, ScrollView, Text, View, Pressable } from "react-native";
import * as ImagePicker from "expo-image-picker";
import * as ImageManipulator from "expo-image-manipulator";
import { File as ExpoFile } from "expo-file-system";
import { api } from "../src/api";
import { Button, Field, Screen, s, colors } from "../src/ui";

const categories = [
  ["TOO_HARD","Çok sert"],["TOO_SOFT","Çok yumuşak"],["TOO_STICKY","Elime çok yapışıyor"],
  ["NOT_RISING","Yeterince kabarmadı"],["NOT_HOLDING_SHAPE","Şekil almıyor"],
  ["SPLITTING","Dağılıyor / kapanmıyor"],["CURDLED","Kesildi"],["OVERCOOKED","Fazla pişti"],
  ["UNDERCOOKED","Az pişti"],["OTHER","Başka bir sorun"],
] as const;

type ProblemCategory = typeof categories[number][0];

type RecoveryResult = {
  status: string;
  attemptId: string;
  message: string;
  imageTrustScore?: number;
  targetProximityScore?: number;
  problem?: { summary: string };
  assessment?: { targetState: string; gapSummary: string; reason: string };
  action?: { type: string; instruction: string; durationSeconds?: number };
  followUp?: { required: boolean; prompt: string };
};

export default function Recovery() {
  const router = useRouter();
  const { sessionId: initial } = useLocalSearchParams<{ sessionId?: string }>();
  const [sessionId, setSessionId] = useState(initial);
  const [image, setImage] = useState<ImagePicker.ImagePickerAsset>();
  const [recipeName, setRecipeName] = useState("");
  const [selectedProblems, setSelectedProblems] = useState<ProblemCategory[]>([]);
  const [statusText, setStatusText] = useState("");
  const [result, setResult] = useState<RecoveryResult>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [followUpMode, setFollowUpMode] = useState(false);
  const [awaitingFollowUp, setAwaitingFollowUp] = useState(false);
  const [fallbackMode, setFallbackMode] = useState(false);
  const [fallbackAdvice, setFallbackAdvice] = useState<{advice?:string;caution?:string;message?:string;status?:string;recipeName?:string}>();

  const choose = async (camera = false) => {
    const permission = camera
      ? await ImagePicker.requestCameraPermissionsAsync()
      : await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) { setError("Fotoğraf izni gerekli."); return; }
    const options = { mediaTypes: ImagePicker.MediaTypeOptions.Images, quality: 0.85 };
    const picked = camera
      ? await ImagePicker.launchCameraAsync(options)
      : await ImagePicker.launchImageLibraryAsync(options);
    if (!picked.canceled) setImage(picked.assets[0]);
  };

  const ensureSession = async () => {
    if (sessionId) return sessionId;
    const session = await api("/cooking-sessions", {
      method: "POST",
      body: JSON.stringify({}),
    });
    setSessionId(session.id);
    return session.id as string;
  };

  const toggleProblem = (key: ProblemCategory) => {
    setSelectedProblems(current =>
      current.includes(key) ? current.filter(item => item !== key) : [...current, key]
    );
  };

  const selectedProblemLabels = () => categories
    .filter(([key]) => selectedProblems.includes(key))
    .map(([, label]) => label);

  const validateRequiredFields = () => {
    if (!recipeName.trim()) {
      setError("Tarifin adını yazmalısın.");
      return false;
    }
    if (selectedProblems.length === 0) {
      setError("Yaşadığın sorunlardan en az birini seçmelisin.");
      return false;
    }
    return true;
  };

  // iOS cihazlar HEIC/HEIF üretebilir. Backend'e her zaman gerçek JPEG
  // byte'ları göndererek MIME tipi ve dosya içeriğinin eşleşmesini sağla.
  const photoPart = async (form: FormData) => {
    if (!image) throw new Error("Önce yeni bir fotoğraf seç.");

    const converted = await ImageManipulator.manipulateAsync(
      image.uri,
      [],
      {
        compress: 0.85,
        format: ImageManipulator.SaveFormat.JPEG,
      },
    );

    const file = new ExpoFile(converted.uri);
    form.append("image", file, "food.jpg");
  };

  const submitTextFallback = async () => {
    if (!validateRequiredFields()) return;
    setLoading(true); setError("");
    try {
      const id = await ensureSession();
      const response = await api(`/cooking-sessions/${id}/recovery-attempts/text-fallback`, {
        method: "POST",
        body: JSON.stringify({
          recipeName: recipeName.trim(),
          problemDescription: selectedProblemLabels().join(", "),
        }),
      }, 90000);
      setFallbackAdvice(response);
    } catch (e: any) {
      setError(e.message || "Metin önerisi alınamadı.");
    } finally { setLoading(false); }
  };

  const submitInitial = async () => {
    if (!validateRequiredFields()) return;
    if (!image) { setError("Bir fotoğraf seçmelisin."); return; }
    setLoading(true); setError("");
    try {
      const id = await ensureSession();
      const form = new FormData();
      await photoPart(form);
      form.append("recipeName", recipeName.trim());
      // Backend tek bir kategori alanı saklıyor; seçilen tüm sorunlar
      // problemDescription içinde korunurken ilk kategori de indeksleniyor.
      form.append("problemCategory", selectedProblems[0]);
      form.append("problemDescription", selectedProblemLabels().join(", "));
      const response = await api(`/cooking-sessions/${id}/recovery-attempts`, {
        method: "POST", body: form,
      }, 90000);
      setResult(response);
      setAwaitingFollowUp(false);
    } catch (e: any) { setError(e.message || "Fotoğraf değerlendirilemedi."); setFallbackMode(true); }
    finally { setLoading(false); }
  };

  const confirmApplied = async () => {
    if (!sessionId || !result?.attemptId) return;
    setLoading(true); setError("");
    try {
      await api(`/cooking-sessions/${sessionId}/recovery-attempts/${result.attemptId}/confirm-action`, {
        method: "POST", body: JSON.stringify({ confirmed: true }),
      });
      setAwaitingFollowUp(true);
      setFollowUpMode(true);
      setImage(undefined);
    } catch (e: any) { setError(e.message); }
    finally { setLoading(false); }
  };

  const submitFollowUp = async () => {
    if (!sessionId || !result?.attemptId || !image || !statusText.trim()) {
      setError("Yeni fotoğraf ve kısa durum açıklaması zorunlu."); return;
    }
    setLoading(true); setError("");
    try {
      const form = new FormData();
      await photoPart(form);
      form.append("statusText", statusText.trim());
      const response = await api(
        `/cooking-sessions/${sessionId}/recovery-attempts/${result.attemptId}/follow-up`,
        { method: "POST", body: form }, 90000,
      );
      setResult(response);
      if (response.status === "REQUEST_NEW_IMAGE" || response.status === "NEEDS_CLARIFICATION") {
        setAwaitingFollowUp(true);
        setFollowUpMode(true);
        setImage(undefined);
      } else {
        setAwaitingFollowUp(false);
        setFollowUpMode(false);
        setStatusText("");
      }
    } catch (e: any) { setError(e.message || "Takip fotoğrafı değerlendirilemedi."); setFallbackMode(true); }
    finally { setLoading(false); }
  };

  const chooseDecision = async (choice: "TRY_RECOVERY" | "REMAKE_COMPONENT") => {
    if (!sessionId || !result?.attemptId) return;
    setLoading(true); setError("");
    try {
      const response = await api(
        `/cooking-sessions/${sessionId}/recovery-attempts/${result.attemptId}/decision`,
        { method: "POST", body: JSON.stringify({ choice }) },
      );
      setResult(response);
    } catch (e: any) { setError(e.message); }
    finally { setLoading(false); }
  };

  const canConfirm = result?.status === "CORRECTION_PROPOSED";
  const canChoose = result?.status === "OFFER_RECOVERY_OR_REMAKE";
  const isReady = result?.status === "READY_TO_CONTINUE";
  const isRemake = result?.status === "REMAKE_COMPONENT";

  const problemFields = (
    <>
      <Field
        placeholder="Tarifin adı (zorunlu)"
        value={recipeName}
        onChangeText={setRecipeName}
      />
      <Text style={[s.eyebrow, { marginTop: 12, marginBottom: 8 }]}>YAŞADIĞIN SORUNLAR (EN AZ BİRİ ZORUNLU)</Text>
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 7 }}>
        {categories.map(([key, label]) => {
          const selected = selectedProblems.includes(key);
          return (
            <Pressable key={key} onPress={() => toggleProblem(key)}
              accessibilityRole="checkbox"
              accessibilityState={{ checked: selected }}
              style={{
                flexDirection: "row", alignItems: "center", gap: 6,
                paddingHorizontal: 11, paddingVertical: 9, borderRadius: 12,
                borderWidth: 1, borderColor: selected ? colors.orange : colors.peach,
                backgroundColor: selected ? colors.orange : colors.peach,
              }}>
              <Text style={{ color: selected ? colors.white : colors.ink, fontWeight: "900" }}>
                {selected ? "☑" : "□"}
              </Text>
              <Text style={{ color: selected ? colors.white : colors.ink, fontWeight: "700" }}>{label}</Text>
            </Pressable>
          );
        })}
      </View>
    </>
  );

  return (
    <Screen>
      <ScrollView contentContainerStyle={s.scroll}>
        <Text style={s.eyebrow}>TARİFİMİ KURTAR</Text>
        <Text style={s.title}>{followUpMode ? "Düzeltme işe yaradı mı?" : "Yemeğin şu an nasıl?"}</Text>
        <Text style={[s.body, { marginVertical: 10 }]}>
          {followUpMode
            ? "Düzeltmeyi uyguladıktan sonra yeni bir fotoğraf çek ve değişimi kısaca anlat."
            : "Tarifin adını yaz, yaşadığın sorunları işaretle ve yemeğinin net bir fotoğrafını yükle."}
        </Text>
        <Text style={[s.recipeMeta, { marginBottom: 10 }]}>
          Fotoğraf tek başına gıda güvenliğini veya iç sıcaklığı kanıtlamaz. Şüpheli yiyeceğin tadına bakma.
        </Text>

        {image && <Image source={{ uri: image.uri }} style={{ height: 230, borderRadius: 18, marginBottom: 10 }} resizeMode="cover" />}
        <View style={{ flexDirection: "row", gap: 8 }}>
          <View style={{ flex: 1 }}><Button secondary onPress={() => choose(true)}>Kamera</Button></View>
          <View style={{ flex: 1 }}><Button secondary onPress={() => choose(false)}>Galeriden seç</Button></View>
        </View>

        {!followUpMode && !result && (
          <>
            {problemFields}
            <Button onPress={submitInitial} loading={loading}>Fotoğrafı değerlendir</Button>
          </>
        )}

        {followUpMode && awaitingFollowUp && (
          <>
            <Field placeholder="Uygulayınca ne değişti? (zorunlu)" value={statusText} onChangeText={setStatusText} multiline />
            <Button onPress={submitFollowUp} loading={loading}>Yeni fotoğrafı değerlendir</Button>
          </>
        )}

        {(fallbackMode || result?.status === "ANALYSIS_INCOMPLETE" || result?.status === "VISION_PROVIDER_UNAVAILABLE" || result?.status === "REQUEST_NEW_IMAGE") && !fallbackAdvice && (
          <View style={[s.card, { marginTop: 18 }]}>
            <Text style={s.eyebrow}>FOTOĞRAFSIZ YARDIM</Text>
            <Text style={s.heading}>Sorunu tarif ederek devam et</Text>
            <Text style={s.body}>Görsel analiz kullanılamadığında seçtiğin sorunlara göre metin önerisi alabilirsin.</Text>
            {problemFields}
            <Button onPress={submitTextFallback} loading={loading}>Metinle öneri al</Button>
          </View>
        )}
        {fallbackAdvice && (
          <View style={[s.card, { marginTop: 18 }]}>
            <Text style={s.eyebrow}>METİN TABANLI ÖNERİ</Text>
            <Text style={s.heading}>{fallbackAdvice.recipeName || recipeName}</Text>
            {fallbackAdvice.advice ? <Text style={s.body}>{fallbackAdvice.advice}</Text> : null}
            {fallbackAdvice.caution ? <Text style={s.recipeMeta}>{fallbackAdvice.caution}</Text> : null}
            {fallbackAdvice.message ? <Text style={s.body}>{fallbackAdvice.message}</Text> : null}
            <Button secondary onPress={() => router.replace("/dashboard")}>Kapat</Button>
          </View>
        )}
        {result && (
          <View style={[s.card, { marginTop: 18 }]}>
            <Text style={s.eyebrow}>{result.status.replaceAll("_", " ")}</Text>
            <Text style={s.heading}>{result.message}</Text>
            {typeof result.targetProximityScore === "number" && (
              <View style={{ marginTop: 14 }}>
                <Text style={s.eyebrow}>HEDEF KIVAMA YAKINLIK</Text>
                <Text style={{ fontSize: 36, fontWeight: "900", color: colors.orange }}>
                  %{result.targetProximityScore}
                </Text>
              </View>
            )}
            {result.problem?.summary && <Text style={[s.body, { marginTop: 10 }]}>{result.problem.summary}</Text>}
            {result.assessment?.gapSummary && <Text style={[s.body, { marginTop: 8 }]}>{result.assessment.gapSummary}</Text>}
            {result.action?.instruction && (
              <View style={{ backgroundColor: colors.sand, borderRadius: 14, padding: 14, marginTop: 14 }}>
                <Text style={s.eyebrow}>ÖNERİLEN DÜZELTME · {result.action.type}</Text>
                <Text style={s.body}>{result.action.instruction}</Text>
                {result.action.durationSeconds ? <Text style={s.recipeMeta}>Süre: {Math.ceil(result.action.durationSeconds / 60)} dakika</Text> : null}
              </View>
            )}
            {canConfirm && <Button onPress={confirmApplied} loading={loading}>Düzeltmeyi uyguladım</Button>}
            {canChoose && (
              <>
                <Button onPress={() => chooseDecision("TRY_RECOVERY")} loading={loading}>Kurtarmayı dene</Button>
                <Button secondary onPress={() => chooseDecision("REMAKE_COMPONENT")} loading={loading}>Bu bileşeni yeniden yap</Button>
              </>
            )}
            {isReady && <Button onPress={() => router.replace("/dashboard")}>Tarife devam et</Button>}
            {isRemake && <Button secondary onPress={() => router.replace("/dashboard")}>Dashboard’a dön</Button>}
            {result.status === "REQUEST_NEW_IMAGE" && (
              <Button secondary onPress={() => { setImage(undefined); if (!followUpMode) setResult(undefined); }}>
                Yeni fotoğraf çek
              </Button>
            )}
            {result.status === "ANALYSIS_INCOMPLETE" && (
              <Button secondary onPress={() => router.replace("/dashboard")}>Kapat</Button>
            )}
            {result.status === "VISION_PROVIDER_UNAVAILABLE" && (
              <Button secondary onPress={() => router.replace("/dashboard")}>Dashboard’a dön</Button>
            )}
            {result.status === "NEEDS_CLARIFICATION" && (
              <Button secondary onPress={() => { setImage(undefined); if (!followUpMode) setResult(undefined); }}>
                {followUpMode ? "Yeni fotoğraf ve açıklama ekle" : "Tekrar dene"}
              </Button>
            )}
          </View>
        )}
        {error ? <Text style={s.error}>{error}</Text> : null}
        <Button secondary onPress={() => router.replace("/dashboard")}>Dashboard’a dön</Button>
      </ScrollView>
    </Screen>
  );
}
