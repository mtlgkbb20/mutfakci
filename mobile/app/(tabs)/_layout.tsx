import { Tabs, Redirect } from "expo-router";
import { useEffect, useState } from "react";
import { ActivityIndicator, Text, View } from "react-native";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { colors } from "../../src/ui";

const tabIcons: Record<string, string> = { dashboard: "⌂", leaderboard: "🏆", events: "🎉", history: "◷", pantry: "▤", profile: "◎" };
export default function TabsLayout() {
  const insets = useSafeAreaInsets();
  const [ready, setReady] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  useEffect(() => {
    let active = true;
    AsyncStorage.getItem("token").then(token => {
      if (!active) return; setAuthenticated(Boolean(token)); setReady(true);
    }).catch(() => { if (!active) return; setAuthenticated(false); setReady(true); });
    return () => { active = false; };
  }, []);
  if (!ready) return <View style={{ flex: 1, alignItems: "center", justifyContent: "center", backgroundColor: colors.cream }}><ActivityIndicator color={colors.green} /></View>;
  if (!authenticated) return <Redirect href="/auth" />;
  return <Tabs screenOptions={({ route }) => ({
    headerShown: false, tabBarActiveTintColor: colors.dark, tabBarInactiveTintColor: "#9AA39A",
    tabBarLabelStyle: { fontSize: 10, fontWeight: "700", marginTop: 1 },
    tabBarIcon: ({ focused }) => <View style={{ width: 34, height: 28, borderRadius: 10, alignItems: "center", justifyContent: "center", backgroundColor: focused ? colors.sage : "transparent" }}>
      <Text style={{ fontSize: 20, color: focused ? colors.green : "#9AA39A", fontWeight: focused ? "800" : "500" }}>{tabIcons[route.name] || "•"}</Text>
    </View>,
    tabBarStyle: { height: 62 + insets.bottom, paddingTop: 6, paddingBottom: Math.max(insets.bottom, 8), backgroundColor: "#FFFFFF", borderTopColor: colors.line, borderTopWidth: 1, elevation: 0 },
    tabBarHideOnKeyboard: true,
  })}>
    <Tabs.Screen name="dashboard" options={{ title: "Ana sayfa" }} />
    <Tabs.Screen name="leaderboard" options={{ title: "Sıralama" }} />
    <Tabs.Screen name="events" options={{ title: "Etkinlikler" }} />
    <Tabs.Screen name="history" options={{ title: "Geçmiş" }} />
    <Tabs.Screen name="pantry" options={{ title: "Buzdolabım" }} />
    <Tabs.Screen name="profile" options={{ title: "Profil" }} />
  </Tabs>;
}
