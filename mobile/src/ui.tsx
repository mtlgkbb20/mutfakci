import { Pressable, StyleSheet, Text, TextInput, TextInputProps, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

export const colors = {
  ink: "#202B25",
  muted: "#758078",
  orange: "#C96B45",
  cream: "#F6F5EF",
  peach: "#F1E3D8",
  line: "#E6E8DF",
  white: "#FFFFFF",
  green: "#527A62",
  gold: "#D5A34D",
  sand: "#EAEFE7",
  sage: "#E4ECE2",
  dark: "#263B30",
  danger: "#B54747",
};
export function Button({ children, onPress, loading = false, secondary = false }: {
  children: React.ReactNode; onPress: () => void; loading?: boolean; secondary?: boolean;
}) {
  return <Pressable disabled={loading} onPress={onPress} style={({ pressed }) => [
    s.button, secondary && s.secondary, pressed && s.pressed, loading && { opacity: 0.65 },
  ]}>
    <Text style={[s.buttonText, secondary && s.secondaryText]}>{loading ? "Hazırlanıyor..." : children}</Text>
  </Pressable>;
}
export function Field(props: TextInputProps) {
  return <TextInput placeholderTextColor="#929A91" {...props} style={[
    s.field, props.multiline && { minHeight: 112, textAlignVertical: "top" }, props.style,
  ]} />;
}
export function Screen({ children }: { children: React.ReactNode }) {
  return <SafeAreaView style={s.screen}>{children}</SafeAreaView>;
}
export function AppShell({ children }: { children: React.ReactNode; active?: "home" | "history" | "profile" }) {
  return <SafeAreaView edges={["top"]} style={s.shell}><View style={s.shellContent}>{children}</View></SafeAreaView>;
}
export function Header({ title, subtitle }: { title: string; subtitle?: string }) {
  return <View style={s.header}>
    <View style={{ flex: 1 }}>
      <Text style={s.headerTitle}>{title}</Text>
      {subtitle ? <Text style={s.headerSubtitle}>{subtitle}</Text> : null}
    </View>
    <View style={s.avatar}><Text style={s.avatarText}>✦</Text></View>
  </View>;
}
export const s = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.cream, padding: 20 },
  shell: { flex: 1, backgroundColor: colors.cream },
  shellContent: { flex: 1, paddingHorizontal: 20 },
  scroll: { paddingTop: 8, paddingBottom: 32 },
  card: { backgroundColor: colors.white, borderColor: colors.line, borderWidth: 1, borderRadius: 24,
    padding: 20, marginBottom: 16, shadowColor: "#314637", shadowOpacity: 0.045, shadowRadius: 16,
    shadowOffset: { width: 0, height: 6 }, elevation: 2 },
  button: { backgroundColor: colors.dark, borderRadius: 16, paddingVertical: 16, paddingHorizontal: 18,
    alignItems: "center", justifyContent: "center", minHeight: 52, marginTop: 12 },
  buttonText: { color: colors.white, fontWeight: "800", fontSize: 15, letterSpacing: 0.1 },
  secondary: { backgroundColor: colors.sage, borderWidth: 1, borderColor: "#D7E2D4" },
  secondaryText: { color: colors.dark },
  pressed: { opacity: 0.82, transform: [{ scale: 0.985 }] },
  field: { backgroundColor: "#FBFCF9", borderColor: colors.line, borderWidth: 1, borderRadius: 14,
    paddingHorizontal: 15, paddingVertical: 14, fontSize: 16, color: colors.ink, marginTop: 10 },
  title: { fontSize: 34, lineHeight: 40, fontWeight: "800", color: colors.ink, letterSpacing: -0.8 },
  heading: { fontSize: 20, lineHeight: 26, fontWeight: "800", color: colors.ink, letterSpacing: -0.3 },
  body: { fontSize: 15, lineHeight: 23, color: colors.muted },
  eyebrow: { fontSize: 11, fontWeight: "800", letterSpacing: 1.35, color: colors.green, marginBottom: 8 },
  error: { color: colors.danger, marginTop: 10, fontSize: 13 },
  header: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", paddingTop: 8, paddingBottom: 22 },
  headerTitle: { fontSize: 27, fontWeight: "800", color: colors.ink, letterSpacing: -0.7 },
  headerSubtitle: { fontSize: 13, color: colors.muted, marginTop: 4 },
  avatar: { width: 44, height: 44, borderRadius: 16, backgroundColor: colors.sage,
    alignItems: "center", justifyContent: "center", borderWidth: 1, borderColor: "#D9E4D7" },
  avatarText: { fontWeight: "900", color: colors.green, fontSize: 21 },
  hero: { backgroundColor: colors.dark, borderRadius: 28, padding: 23, minHeight: 218,
    justifyContent: "space-between", marginBottom: 18, overflow: "hidden" },
  heroTitle: { fontSize: 29, lineHeight: 35, fontWeight: "800", color: "#FFFFFF", maxWidth: 290, letterSpacing: -0.7 },
  heroMark: { fontSize: 38, alignSelf: "flex-end", color: "#D9B77C" },
  actionRow: { flexDirection: "row", gap: 12, marginBottom: 18 },
  actionButton: { flex: 1, minHeight: 76, borderRadius: 20, paddingHorizontal: 14, paddingVertical: 12,
    backgroundColor: colors.white, borderWidth: 1, borderColor: colors.line, flexDirection: "row",
    alignItems: "center", gap: 10 },
  actionIcon: { width: 38, height: 38, borderRadius: 14, backgroundColor: colors.sage,
    textAlign: "center", textAlignVertical: "center", lineHeight: 38, fontSize: 21, color: colors.green, fontWeight: "700" },
  actionLabel: { flex: 1, fontSize: 14, fontWeight: "800", color: colors.ink },
  statRow: { flexDirection: "row", gap: 10 },
  stat: { flex: 1, backgroundColor: colors.white, borderRadius: 18, padding: 16,
    borderWidth: 1, borderColor: colors.line },
  statValue: { fontSize: 25, fontWeight: "800", color: colors.ink },
  statLabel: { fontSize: 12, color: colors.muted, marginTop: 4 },
  progressTrack: { height: 8, borderRadius: 8, backgroundColor: "#E9EDE5", overflow: "hidden", marginTop: 12 },
  progressFill: { height: "100%", backgroundColor: colors.gold, borderRadius: 8 },
  recipeMeta: { fontSize: 12, color: colors.muted, marginTop: 6, lineHeight: 18 },
  recipeMini: { width: 208, minHeight: 136, marginRight: 12, padding: 17,
    backgroundColor: colors.white, borderRadius: 20, borderWidth: 1, borderColor: colors.line },
  recipeEmoji: { fontSize: 27, marginBottom: 9 },
  recipeTitle: { fontSize: 15, lineHeight: 21, fontWeight: "800", color: colors.ink },
  sectionRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginBottom: 13 },
  smallLink: { color: colors.green, fontWeight: "800" },
});
