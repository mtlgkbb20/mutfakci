import { useLocalSearchParams, useRouter } from "expo-router";
import { useState } from "react";
import { Image, ScrollView, Text, View } from "react-native";
import * as ImagePicker from "expo-image-picker";
import { api } from "../src/api";
import { Button, Field, Screen, s } from "../src/ui";
export default function Recovery() {
  const router = useRouter();
  const { sessionId: initial } = useLocalSearchParams<{ sessionId?: string }>();
  const [sessionId, setSessionId] = useState(initial);
  const [image, setImage] = useState<ImagePicker.ImagePickerAsset>();
  const [outcome, setOutcome] = useState("");
  const [feedback, setFeedback] = useState<any>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const choose = async (camera = false) => {
    const permission = camera
      ? await ImagePicker.requestCameraPermissionsAsync()
      : await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      setError("Fotoğraf izni gerekli.");
      return;
    }
    const options = {
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      quality: 0.8,
    };
    const result = camera
      ? await ImagePicker.launchCameraAsync(options)
      : await ImagePicker.launchImageLibraryAsync(options);
    if (!result.canceled) setImage(result.assets[0]);
  };
  const submit = async () => {
    if (!image) return;
    setLoading(true);
    setError("");
    try {
      let id = sessionId;
      if (!id) {
        const session = await api("/cooking-sessions", {
          method: "POST",
          body: JSON.stringify({ desiredOutcome: outcome || "İdeal kıvam" }),
        });
        id = session.id;
        setSessionId(id);
      }
      const form = new FormData();
      form.append("image", {
        uri: image.uri,
        name: image.fileName || "food.jpg",
        type: image.mimeType || "image/jpeg",
      } as any);
      form.append("desiredOutcome", outcome);
      setFeedback(
        await api(`/cooking-sessions/${id}/observations`, {
          method: "POST",
          body: form,
        }),
      );
    } catch (e: any) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };
  return (
    <Screen>
      <ScrollView contentContainerStyle={s.scroll}>
        <Text style={s.eyebrow}>TARİFİMİ KURTAR</Text>
        <Text style={s.title}>Yemeğin şu an nasıl?</Text>
        <Text style={[s.body, { marginVertical: 10 }]}>
          Net bir fotoğraf yükle. Ek açıklama isteğe bağlı.
        </Text>
        {image && (
          <Image
            source={{ uri: image.uri }}
            style={{ height: 240, borderRadius: 18, marginBottom: 10 }}
            resizeMode="cover"
          />
        )}
        <View style={{ flexDirection: "row", gap: 8 }}>
          <View style={{ flex: 1 }}>
            <Button secondary onPress={() => choose(true)}>
              Kamera
            </Button>
          </View>
          <View style={{ flex: 1 }}>
            <Button secondary onPress={() => choose(false)}>
              Galeriden seç
            </Button>
          </View>
        </View>
        <Field
          placeholder="İstediğin sonuç (isteğe bağlı)"
          value={outcome}
          onChangeText={setOutcome}
        />
        <Button onPress={submit} loading={loading}>
          {feedback ? "Tekrar değerlendir" : "Fotoğrafı değerlendir"}
        </Button>
        {error && <Text style={s.error}>{error}</Text>}
        {feedback && (
          <View style={s.card}>
            <Text style={s.eyebrow}>{feedback.status}</Text>
            <Text style={s.heading}>{feedback.message}</Text>
            {feedback.requiresUserInput && (
              <Text style={[s.body, { marginTop: 8 }]}>
                Daha aydınlık ve net bir fotoğraf deneyebilirsin.
              </Text>
            )}
          </View>
        )}
        <Button secondary onPress={() => router.replace("/dashboard")}>
          Dashboard’a dön
        </Button>
      </ScrollView>
    </Screen>
  );
}
