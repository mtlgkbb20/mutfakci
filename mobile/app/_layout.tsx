import { Pressable, Text } from "react-native";
import { Stack, useRouter } from "expo-router";
import { StatusBar } from "expo-status-bar";

export default function Layout() {
  const router = useRouter();

  return (
    <>
      <StatusBar style="dark" />
      <Stack
        screenOptions={{
          headerShown: true,
          headerTitle: "",
          headerLeft: () => (
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Geri dön"
              hitSlop={10}
              onPress={() => {
                if (router.canGoBack()) {
                  router.back();
                } else {
                  router.replace("/dashboard");
                }
              }}
              style={{
                width: 44,
                height: 44,
                alignItems: "flex-start",
                justifyContent: "center",
              }}
            >
              <Text
                style={{
                  color: "#c55a36",
                  fontSize: 27,
                  fontWeight: "500",
                }}
              >
                ←
              </Text>
            </Pressable>
          ),
          headerTintColor: "#c55a36",
          headerStyle: { backgroundColor: "#fbf8f3" },
          headerShadowVisible: false,
          animation: "slide_from_right",
          contentStyle: { backgroundColor: "#fbf8f3" },
        }}
      >
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="auth" options={{ headerShown: false }} />
        <Stack.Screen name="index" options={{ headerShown: false }} />
      </Stack>
    </>
  );
}
