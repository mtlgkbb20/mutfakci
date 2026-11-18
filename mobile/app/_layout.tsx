import { Pressable, Text } from "react-native";
import { Stack, useRouter } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { colors } from "../src/ui";

export default function Layout() {
  const router = useRouter();
  return <>
    <StatusBar style="dark" />
    <Stack screenOptions={{
      headerShown: true, headerTitle: "",
      headerLeft: () => <Pressable accessibilityRole="button" accessibilityLabel="Geri dön" hitSlop={10}
        onPress={() => router.canGoBack() ? router.back() : router.replace("/dashboard")}
        style={{ width: 44, height: 44, alignItems: "flex-start", justifyContent: "center" }}>
        <Text style={{ color: colors.green, fontSize: 28, fontWeight: "500" }}>‹</Text>
      </Pressable>,
      headerTintColor: colors.green, headerStyle: { backgroundColor: colors.cream },
      headerShadowVisible: false, animation: "slide_from_right",
      contentStyle: { backgroundColor: colors.cream },
    }}>
      <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
      <Stack.Screen name="auth" options={{ headerShown: false }} />
      <Stack.Screen name="index" options={{ headerShown: false }} />
    </Stack>
  </>;
}
