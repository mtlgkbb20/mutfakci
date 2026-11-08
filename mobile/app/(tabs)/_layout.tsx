import { Tabs } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { colors } from "../../src/ui";
export default function TabsLayout() {
  const insets = useSafeAreaInsets();
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
        name="profile"
        options={{ title: "Profil", tabBarIcon: () => null }}
      />
    </Tabs>
  );
}
