import { useLocalSearchParams, useRouter } from "expo-router";
import { useState } from "react";
import { Image, ScrollView, Text, View, Pressable } from "react-native";
import * as ImagePicker from "expo-image-picker";
import * as ImageManipulator from "expo-image-manipulator";
import { File as ExpoFile } from "expo-file-system";
import { api } from "../src/api";
import { Button, Field, Screen, s, colors } from "../src/ui";
import { File as ExpoFile } from "expo-file-system";

const categories = [
  ["TOO_HARD","Çok sert"],["TOO_SOFT","Çok yumuşak"],["TOO_STICKY","Yapışkan"],
  ["NOT_RISING","Kabarmıyor"],["NOT_HOLDING_SHAPE","Şeklini korumuyor"],
  ["SPLITTING","Kesiliyor"],["CURDLED","Kesildi"],["OVERCOOKED","Fazla pişti"],
  ["UNDERCOOKED","Az pişti"],["OTHER","Diğer"],
] as const;

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
  const [outcome, setOutcome] = useState("");
  const [description, setDescription] = useState("");
  const [category, setCategory] = useState("");
  const [statusText, setStatusText] = useState("");
  const [result, setResult] = useState<RecoveryResult>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [followUpMode, setFollowUpMode] = useState(false);
  const [awaitingFollowUp, setAwaitingFollowUp] = useState(false);

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
      body: JSON.stringify({ desiredOutcome: outcome || null }),
    });
    setSessionId(session.id);
    return session.id as string;
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

  const submitInitial = async () => {
    if (!image) { setError("Bir fotoğraf seçmelisin."); return; }
    setLoading(true); setError("");
    try {
      const id = await ensureSession();
      const form = new FormData();
      await photoPart(form);
      if (category) form.append("problemCategory", category);
      if (description.trim()) form.append("problemDescription", description.trim());
      if (outcome.trim()) form.append("desiredOutcome", outcome.trim());
      const response = await api(`/cooking-sessions/${id}/recovery-attempts`, {
        method: "POST", body: form,
      }, 90000);
      setResult(response);
      setAwaitingFollowUp(false);
    } catch (e: any) { setError(e.message || "Fotoğraf değerlendirilemedi."); }
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
    } catch (e: any) { setError(e.message || "Takip fotoğrafı değerlendirilemedi."); }
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

  return (
    <Screen>
      <ScrollView contentContainerStyle={s.scroll}>
        <Text style={s.eyebrow}>TARİFİMİ KURTAR</Text>
        <Text style={s.title}>{followUpMode ? "Düzeltme işe yaradı mı?" : "Yemeğin şu an nasıl?"}</Text>
        <Text style={[s.body, { marginVertical: 10 }]}>
          {followUpMode
            ? "Düzeltmeyi uyguladıktan sonra yeni bir fotoğraf çek ve değişimi kısaca anlat."
            : "Net bir fotoğraf yükle. Sorunu ve istediğin sonucu belirtmek isteğe bağlı."}
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
            <Text style={[s.eyebrow, { marginTop: 18 }]}>SORUN (İSTEĞE BAĞLI)</Text>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 7 }}>
              {categories.map(([key, label]) => (
                <Pressable key={key} onPress={() => setCategory(category === key ? "" : key)}
                  style={{ paddingHorizontal: 11, paddingVertical: 8, borderRadius: 18,
                    backgroundColor: category === key ? colors.orange : colors.peach }}>
                  <Text style={{ color: category === key ? colors.white : colors.ink, fontWeight: "700" }}>{label}</Text>
                </Pressable>
              ))}
            </View>
            <Field placeholder="Sorunu biraz anlat (isteğe bağlı)" value={description} onChangeText={setDescription} multiline />
            <Field placeholder="İstediğin sonuç (isteğe bağlı)" value={outcome} onChangeText={setOutcome} />
            <Button onPress={submitInitial} loading={loading}>Fotoğrafı değerlendir</Button>
          </>
        )}

        {followUpMode && awaitingFollowUp && (
          <>
            <Field placeholder="Uygulayınca ne değişti? (zorunlu)" value={statusText} onChangeText={setStatusText} multiline />
            <Button onPress={submitFollowUp} loading={loading}>Yeni fotoğrafı değerlendir</Button>
          </>
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
