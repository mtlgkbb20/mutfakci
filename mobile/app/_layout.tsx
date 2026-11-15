import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";

export default function Layout() {
  return (
    <>
      <StatusBar style="dark" />
      <Stack
        screenOptions={{
          headerShown: true,
          headerTitle: "",
          headerBackButtonDisplayMode: "minimal",
          headerBackTitle: "Geri",
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
