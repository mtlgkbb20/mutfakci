import { Redirect } from "expo-router";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { useEffect, useState } from "react";
import { ActivityIndicator, View } from "react-native";
export default function Index() {
  const [ready, setReady] = useState(false);
  const [signed, setSigned] = useState(false);
  useEffect(() => {
    AsyncStorage.getItem("token").then((t) => {
      setSigned(!!t);
      setReady(true);
    });
  }, []);
  if (!ready)
    return (
      <View style={{ flex: 1, alignItems: "center", justifyContent: "center" }}>
        <ActivityIndicator />
      </View>
    );
  return <Redirect href={signed ? "/(tabs)/dashboard" : "/auth"} />;
}
