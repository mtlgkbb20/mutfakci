import { useEffect, useState } from "react";
import { Alert, Image, Pressable, ScrollView, Text, TextInput, View } from "react-native";
import * as ImagePicker from "expo-image-picker";
import { api } from "../../src/api";
import { AppShell, Button, Header, s } from "../../src/ui";

export default function Events() {
  const [events,setEvents]=useState<any[]>([]);
  const [selected,setSelected]=useState<any>(null);
  const [submissions,setSubmissions]=useState<any[]>([]);
  const [photos,setPhotos]=useState<ImagePicker.ImagePickerAsset[]>([]);
  const [caption,setCaption]=useState("");
  const [scores,setScores]=useState<Record<string,number>>({});
  const [busy,setBusy]=useState(false);

  const refresh=async()=>{ const data=await api("/events"); setEvents(data); };
  useEffect(()=>{refresh().catch(error=>console.error("Events load failed",error));},[]);

  const openEvent=async(event:any)=>{
    try {
      const [detail,entries]=await Promise.all([api(`/events/${event.id}`),api(`/events/${event.id}/submissions`)]);
      setSelected({...event,...detail}); setSubmissions(entries); setPhotos([]); setScores({});
    } catch(error:any) { Alert.alert("Etkinlik açılamadı",error?.message||"Tekrar dene."); }
  };

  const join=async()=>{
    if(!selected)return;
    try { await api(`/events/${selected.id}/participation`,{method:"POST"}); setSelected({...selected,joined:true}); }
    catch(error:any){Alert.alert("Katılım başarısız",error?.message||"Tekrar dene.");}
  };

  const pickPhotos=async()=>{
    const permission=await ImagePicker.requestMediaLibraryPermissionsAsync();
    if(!permission.granted){Alert.alert("İzin gerekli","Etkinliğe fotoğraf eklemek için galeri izni vermelisin.");return;}
    const result=await ImagePicker.launchImageLibraryAsync({mediaTypes:["images"],allowsMultipleSelection:true,selectionLimit:selected?.maxImages||4,quality:0.85});
    if(!result.canceled)setPhotos(result.assets.slice(0,selected?.maxImages||4));
  };

  const submit=async()=>{
    if(!selected||photos.length===0){Alert.alert("Fotoğraf seç","En az bir fotoğraf eklemelisin.");return;}
    setBusy(true);
    try{
      const form=new FormData();
      form.append("caption",caption);
      photos.forEach((photo,index)=>form.append("images",{
        uri:photo.uri,name:photo.fileName||`mutfak-etkinligi-${index}.jpg`,type:photo.mimeType||"image/jpeg"
      } as any));
      await api(`/events/${selected.id}/submissions`,{method:"POST",body:form});
      Alert.alert("Gönderildi","Fotoğrafların moderasyon için gönderildi. Onaylandıktan sonra etkinlikte görünür.");
      const [detail,entries]=await Promise.all([api(`/events/${selected.id}`),api(`/events/${selected.id}/submissions`)]);
      setSelected({...selected,...detail});setSubmissions(entries);setPhotos([]);
    }catch(error:any){Alert.alert("Gönderim başarısız",error?.message||"Tekrar dene.");}
    finally{setBusy(false);}
  };

  const vote=async(submissionId:string)=>{
    if(!selected)return;
    try{
      await api(`/events/${selected.id}/submissions/${submissionId}/vote`,{method:"PUT",body:JSON.stringify({score:scores[submissionId]||0})});
      Alert.alert("Oyun kaydedildi","Puanın kaydedildi.");
    }catch(error:any){Alert.alert("Oy verilemedi",error?.message||"Tekrar dene.");}
  };

  if(selected){
    const mySubmission=Array.isArray(selected.submission)&&selected.submission.length>0;
    return <AppShell><ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={s.scroll}>
      <Pressable onPress={()=>setSelected(null)}><Text style={s.smallLink}>‹ Etkinliklere dön</Text></Pressable>
      <Header title={selected.title} subtitle={selected.theme}/>
      <View style={s.card}>
        <Text style={s.body}>{selected.description}</Text>
        <Text style={[s.recipeMeta,{marginTop:10}]}>Fotoğraf yükleme: {new Date(selected.submissionOpensAt).toLocaleString("tr-TR")} – {new Date(selected.submissionClosesAt).toLocaleString("tr-TR")}</Text>
        <Text style={s.recipeMeta}>Oylama: {new Date(selected.votingOpensAt).toLocaleString("tr-TR")} – {new Date(selected.votingClosesAt).toLocaleString("tr-TR")}</Text>
        {!selected.joined && <Button onPress={join}>Etkinliğe katıl</Button>}
        {selected.joined && !mySubmission && selected.status==="SCHEDULED" && <View style={{marginTop:16}}>
          <Text style={s.heading}>Yemeğinin fotoğraflarını paylaş</Text>
          <TextInput value={caption} onChangeText={setCaption} placeholder="Yemeğin hakkında kısa bir not" style={{borderWidth:1,borderColor:"#e5d9cd",borderRadius:10,padding:12,marginVertical:10}}/>
          <Button secondary onPress={pickPhotos}>Fotoğraf seç ({photos.length}/{selected.maxImages||4})</Button>
          <ScrollView horizontal>{photos.map((photo,index)=><Image key={index} source={{uri:photo.uri}} style={{width:100,height:100,borderRadius:12,marginRight:8,marginTop:12}}/>)}</ScrollView>
          <Button onPress={submit} loading={busy}>Gönder</Button>
        </View>}
        {mySubmission && <Text style={[s.body,{marginTop:12}]}>Katılımın: {selected.submission[0].status==="PENDING"?"Moderasyon bekliyor":selected.submission[0].status==="APPROVED"?"Yayında":"Reddedildi"}</Text>}
      </View>
      <View style={[s.card,{marginTop:16}]}>
        <Text style={s.heading}>Katılımcı tarifleri</Text>
        {submissions.length===0?<Text style={s.body}>Henüz onaylanmış gönderi yok.</Text>:submissions.map(entry=><View key={entry.id} style={{paddingVertical:14,borderBottomWidth:1,borderBottomColor:"#eee5da"}}>
          <Text style={s.heading}>{entry.displayName}</Text>
          {!!entry.caption&&<Text style={s.body}>{entry.caption}</Text>}
          <Text style={s.recipeMeta}>{entry.imageIds?.length||0} fotoğraf</Text>
          {selected.canVote && <View style={{marginTop:10}}>
            <Text style={s.recipeMeta}>10 üzerinden puan ver</Text>
            <View style={{flexDirection:"row",flexWrap:"wrap",gap:6,marginVertical:8}}>
              {Array.from({length:10},(_,i)=>i+1).map(score=><Pressable key={score} onPress={()=>setScores(v=>({...v,[entry.id]:score}))} style={{padding:9,borderRadius:8,backgroundColor:scores[entry.id]===score?"#c55a36":"#f1e7dd"}}>
                <Text style={{color:scores[entry.id]===score?"white":"#55483d",fontWeight:"700"}}>{score}</Text>
              </Pressable>)}
            </View>
            <Button onPress={()=>vote(entry.id)}>Puanı gönder</Button>
          </View>}
        </View>)}
      </View>
    </ScrollView></AppShell>;
  }

  return <AppShell><ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={s.scroll}>
    <Header title="Mutfak etkinlikleri" subtitle="Toplulukla birlikte pişir, paylaş ve yarış"/>
    {events.length===0?<View style={s.card}><Text style={s.body}>Şu anda yayınlanmış bir etkinlik yok. Yeni etkinlikler burada görünecek.</Text></View>:
      events.map(event=><Pressable key={event.id} onPress={()=>openEvent(event)} style={[s.card,{marginBottom:14}]}>
        <Text style={s.eyebrow}>{event.theme}</Text><Text style={s.heading}>{event.title}</Text>
        <Text style={[s.body,{marginTop:8}]}>{event.description}</Text>
        <Text style={[s.recipeMeta,{marginTop:10}]}>Başlangıç: {new Date(event.startsAt).toLocaleDateString("tr-TR")}</Text>
        <Text style={[s.smallLink,{marginTop:12}]}>Etkinliği aç →</Text>
      </Pressable>)}
  </ScrollView></AppShell>;
}
