import { useRouter } from "expo-router";
import { useState } from "react";
import { Text, View } from "react-native";
import { api, setSession } from "../src/api";
import { Button, Field, Screen, s } from "../src/ui";
export default function Auth() {
  const router = useRouter();
  const [register, setRegister] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const submit = async () => {
    setLoading(true);
    setError("");
    try {
      const data = await api(`/auth/${register ? "register" : "login"}`, {
        method: "POST",
        body: JSON.stringify(
          register
            ? { email, password, displayName: name }
            : { email, password },
        ),
      });
      await setSession(data.token, data.user);
      router.replace("/(tabs)/dashboard");
    } catch (e: any) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };
  return (
    <Screen>
      <View style={{ flex: 1, justifyContent: "center" }}>
        <View style={s.card}>
          <Text style={s.eyebrow}>KITCHEN AI</Text>
          <Text style={s.title}>Mutfağındaki akıllı yardımcı.</Text>
          <Text style={[s.body, { marginTop: 10, marginBottom: 14 }]}>
            İstediğin sonucu anlat, birlikte ulaşalım.
          </Text>
          {register && (
            <Field placeholder="Adın" value={name} onChangeText={setName} />
          )}
          <Field
            placeholder="E-posta"
            keyboardType="email-address"
            autoCapitalize="none"
            value={email}
            onChangeText={setEmail}
          />
          <Field
            placeholder="Şifre"
            secureTextEntry
            value={password}
            onChangeText={setPassword}
          />
          {error && <Text style={s.error}>{error}</Text>}
          <Button onPress={submit} loading={loading}>
            {register ? "Kayıt ol" : "Giriş yap"}
          </Button>
          <Button secondary onPress={() => setRegister(!register)}>
            {register ? "Giriş yap" : "Hesap oluştur"}
          </Button>
        </View>
      </View>
    </Screen>
  );
}
