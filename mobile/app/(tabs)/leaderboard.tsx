import { useEffect, useState } from "react";
import { ScrollView, Text, View } from "react-native";
import { api } from "../../src/api";
import { AppShell, Header, s } from "../../src/ui";

export default function Leaderboard() {
  const [data, setData] = useState<any>();
  const [mine, setMine] = useState<any>();
  useEffect(() => {
    Promise.all([api("/gamification/leaderboard/current"), api("/gamification/me")])
      .then(([board, progress]) => { setData(board); setMine(progress); })
      .catch(error => console.error("Leaderboard load failed", error));
  }, []);
  return <AppShell><ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={s.scroll}>
    <Header title="Haftalık sıralama" subtitle="Her hafta yeni bir mutfak yarışı" />
    <View style={s.card}>
      <Text style={s.eyebrow}>BU HAFTA</Text>
      <Text style={s.title}>{mine?.weeklyXp || 0} XP</Text>
      <Text style={s.body}>Senin sıralaman: #{mine?.rank || "—"}</Text>
      <Text style={[s.recipeMeta,{marginTop:8}]}>{data?.weekStart} – {data?.weekEnd}</Text>
      <Text style={[s.body,{marginTop:12}]}>Haftanın birincisi ücretsiz bir AI tarif üretimi kazanır.</Text>
    </View>
    <View style={[s.card,{marginTop:16}]}>
      <Text style={s.heading}>Liderler</Text>
      {(data?.entries || []).length===0 ? <Text style={s.body}>Bu hafta henüz puan yok.</Text> : data.entries.map((entry:any)=>(
        <View key={entry.userId} style={{flexDirection:"row",alignItems:"center",paddingVertical:13,borderBottomWidth:1,borderBottomColor:"#eee5da"}}>
          <Text style={[s.heading,{width:38,color:entry.rank<=3?"#c55a36":"#786e64"}]}>#{entry.rank}</Text>
          <View style={{flex:1}}><Text style={s.heading}>{entry.displayName} {entry.badgeIcon ? <Text accessibilityLabel={`${entry.badgeName}: ${entry.badgeDescription}`}>{entry.badgeIcon}</Text> : null}</Text><Text style={s.recipeMeta}>Seviye deneyimi · {entry.lifetimeXp} toplam XP</Text></View>
          <Text style={s.heading}>{entry.weeklyXp} XP</Text>
        </View>
      ))}
    </View>
  </ScrollView></AppShell>;
}
