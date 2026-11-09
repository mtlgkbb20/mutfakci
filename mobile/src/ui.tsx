import {
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  TextInputProps,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
export const colors = {
  ink: "#302a25",
  muted: "#786e64",
  orange: "#c55a36",
  cream: "#fbf8f3",
  peach: "#ead7c7",
  line: "#e8ded3",
  white: "#fff",
  green: "#607b62",
  gold: "#d49a46",
  sand: "#f3ebe2",
};
export function Button({
  children,
  onPress,
  loading = false,
  secondary = false,
  disabled = false,
}: {
  children: React.ReactNode;
  onPress: () => void;
  loading?: boolean;
  secondary?: boolean;
  disabled?: boolean;
}) {
  const isDisabled = loading || disabled;
  return (
    <Pressable
      disabled={isDisabled}
      onPress={onPress}
      style={({ pressed }) => [
        s.button,
        secondary && s.secondary,
        isDisabled && { opacity: 0.5 },
        pressed && !isDisabled && s.pressed,
      ]}
    >
      <Text style={[s.buttonText, secondary && s.secondaryText]}>
        {loading ? "Hazırlanıyor..." : children}
      </Text>
    </Pressable>
  );
}
export function Field(props: TextInputProps) {
  return (
    <TextInput
      placeholderTextColor="#9b9188"
      {...props}
      style={[
        s.field,
        props.multiline && { height: 100, textAlignVertical: "top" },
        props.style,
      ]}
    />
  );
}
export function Screen({ children }: { children: React.ReactNode }) {
  return <SafeAreaView style={s.screen}>{children}</SafeAreaView>;
}
export function AppShell({
  children,
}: {
  children: React.ReactNode;
  active?: "home" | "history" | "profile";
}) {
  return (
    <SafeAreaView edges={["top"]} style={s.shell}>
      <View style={s.shellContent}>{children}</View>
    </SafeAreaView>
  );
}
export function Header({
  title,
  subtitle,
}: {
  title: string;
  subtitle?: string;
}) {
  return (
    <View style={s.header}>
      <View>
        <Text style={s.headerTitle}>{title}</Text>
        {subtitle && <Text style={s.headerSubtitle}>{subtitle}</Text>}
      </View>
      <View style={s.avatar}>
        <Text style={s.avatarText}>K</Text>
      </View>
    </View>
  );
}
export const s = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.cream, padding: 20 },
  shell: { flex: 1, backgroundColor: colors.cream },
  shellContent: { flex: 1, paddingHorizontal: 20 },
  scroll: { paddingTop: 8, paddingBottom: 28 },
  card: {
    backgroundColor: colors.white,
    borderColor: colors.line,
    borderWidth: 1,
    borderRadius: 22,
    padding: 22,
    marginBottom: 16,
    shadowColor: "#6d4728",
    shadowOpacity: 0.06,
    shadowRadius: 14,
    shadowOffset: { width: 0, height: 5 },
    elevation: 2,
  },
  button: {
    backgroundColor: colors.orange,
    borderRadius: 13,
    padding: 15,
    alignItems: "center",
    marginTop: 10,
  },
  buttonText: { color: colors.white, fontWeight: "800", fontSize: 16 },
  secondary: { backgroundColor: colors.peach },
  secondaryText: { color: "#874329" },
  pressed: { opacity: 0.78, transform: [{ scale: 0.98 }] },
  field: {
    backgroundColor: colors.white,
    borderColor: "#ded4c9",
    borderWidth: 1,
    borderRadius: 11,
    padding: 14,
    fontSize: 16,
    color: colors.ink,
    marginTop: 10,
  },
  title: { fontSize: 34, lineHeight: 39, fontWeight: "800", color: colors.ink },
  heading: { fontSize: 22, fontWeight: "800", color: colors.ink },
  body: { fontSize: 16, lineHeight: 24, color: colors.muted },
  eyebrow: {
    fontSize: 12,
    fontWeight: "800",
    letterSpacing: 1.5,
    color: "#a65335",
    marginBottom: 8,
  },
  error: { color: "#b42318", marginTop: 10 },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingTop: 7,
    paddingBottom: 20,
  },
  headerTitle: { fontSize: 28, fontWeight: "900", color: colors.ink },
  headerSubtitle: { fontSize: 14, color: colors.muted, marginTop: 3 },
  avatar: {
    width: 42,
    height: 42,
    borderRadius: 21,
    backgroundColor: colors.peach,
    alignItems: "center",
    justifyContent: "center",
  },
  avatarText: { fontWeight: "900", color: "#874329", fontSize: 18 },
  hero: {
    backgroundColor: colors.peach,
    borderRadius: 28,
    padding: 24,
    minHeight: 205,
    justifyContent: "space-between",
    marginBottom: 14,
  },
  heroTitle: {
    fontSize: 30,
    lineHeight: 35,
    fontWeight: "900",
    color: colors.ink,
    maxWidth: 305,
  },
  heroMark: { fontSize: 43, alignSelf: "flex-end", color: "#a65335" },
  actionRow: { flexDirection: "row", gap: 10, marginBottom: 16 },
  actionButton: {
    flex: 1,
    minHeight: 65,
    borderRadius: 18,
    paddingHorizontal: 15,
    backgroundColor: colors.white,
    borderWidth: 1,
    borderColor: colors.line,
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    shadowColor: "#6d4728",
    shadowOpacity: 0.05,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 3 },
    elevation: 1,
  },
  actionIcon: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: colors.sand,
    textAlign: "center",
    lineHeight: 32,
    fontSize: 25,
    color: colors.orange,
    fontWeight: "500",
  },
  actionLabel: { fontSize: 16, fontWeight: "900", color: colors.ink },
  statRow: { flexDirection: "row", gap: 10 },
  stat: {
    flex: 1,
    backgroundColor: colors.white,
    borderRadius: 18,
    padding: 17,
    borderWidth: 1,
    borderColor: colors.line,
  },
  statValue: { fontSize: 25, fontWeight: "900", color: colors.ink },
  statLabel: { fontSize: 12, color: colors.muted, marginTop: 3 },
  progressTrack: {
    height: 8,
    borderRadius: 4,
    backgroundColor: "#f0e6da",
    overflow: "hidden",
    marginTop: 12,
  },
  progressFill: {
    height: "100%",
    backgroundColor: colors.orange,
    borderRadius: 4,
  },
  recipeMeta: { fontSize: 12, color: colors.muted, marginTop: 6 },
  recipeMini: {
    width: 205,
    minHeight: 125,
    marginRight: 12,
    padding: 16,
    backgroundColor: colors.white,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: colors.line,
  },
  recipeEmoji: { fontSize: 28, marginBottom: 8 },
  recipeTitle: { fontSize: 16, fontWeight: "800", color: colors.ink },
  sectionRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 12,
  },
  smallLink: { color: "#a65335", fontWeight: "800" },
});
