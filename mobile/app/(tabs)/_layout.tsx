import { Tabs, Redirect } from "expo-router";
import { useEffect, useState } from "react";
import { ActivityIndicator, View } from "react-native";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { colors } from "../../src/ui";

export default function TabsLayout() {
  const insets = useSafeAreaInsets();
  const [ready, setReady] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);

  useEffect(() => {
    let active = true;
    AsyncStorage.getItem("token")
      .then((token) => {
        if (!active) return;
        setAuthenticated(Boolean(token));
        setReady(true);
      })
      .catch(() => {
        if (!active) return;
        setAuthenticated(false);
        setReady(true);
      });
    return () => {
      active = false;
    };
  }, []);

  if (!ready) {
    return (
      <View style={{ flex: 1, alignItems: "center", justifyContent: "center" }}>
        <ActivityIndicator color={colors.orange} />
      </View>
    );
  }

  if (!authenticated) return <Redirect href="/auth" />;

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.orange,
        tabBarInactiveTintColor: "#a79c91",
        tabBarLabelStyle: { fontSize: 11, fontWeight: "700" },
        tabBarStyle: {
          height: 58 + insets.bottom,
          paddingTop: 7,
          paddingBottom: Math.max(insets.bottom, 8),
          backgroundColor: colors.white,
          borderTopColor: colors.line,
        },
        tabBarHideOnKeyboard: true,
      }}
    >
      <Tabs.Screen
        name="dashboard"
        options={{ title: "Ana sayfa", tabBarIcon: () => null }}
      />
      <Tabs.Screen
        name="history"
        options={{ title: "Geçmiş", tabBarIcon: () => null }}
      />
      <Tabs.Screen
        name="pantry"
        options={{ title: "Buzdolabı", tabBarIcon: () => null }}
      />
      <Tabs.Screen
        name="profile"
        options={{ title: "Profil", tabBarIcon: () => null }}
      />
    </Tabs>
  );
}
