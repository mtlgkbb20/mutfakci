import { useCallback, useEffect, useState } from "react";
import { Alert, Pressable, ScrollView, Text, View } from "react-native";
import { useRouter } from "expo-router";
import { api } from "../src/api";
import { AppShell, Button, Field, Header, s, colors } from "../src/ui";

type PantryItem = { id:string; name:string; category:string; quantity:number; unit:string; expiryDate?:string|null; expired:boolean; expiringSoon:boolean; version:number };
type Match = { recipeId:string; title:string; servings:number; coverage:number; available:{name:string;pantryItemId:string;required:number;unit:string;stock:number;stockUnit:string}[]; missing:{name:string;required:number;unit:string}[] };

export default function PantryScreen(){
 const router=useRouter();
 const [items,setItems]=useState<PantryItem[]>([]);
 const [expiring,setExpiring]=useState<PantryItem[]>([]);
 const [matches,setMatches]=useState<Match[]>([]);
 const [name,setName]=useState("");
 const [quantity,setQuantity]=useState("1");
 const [unit,setUnit]=useState("piece");
 const [category,setCategory]=useState("OTHER");
 const [expiry,setExpiry]=useState("");
 const [loading,setLoading]=useState(false);
 const [showForm,setShowForm]=useState(false);
 const [showMatches,setShowMatches]=useState(false);
 const [editingId,setEditingId]=useState<string|null>(null);
 const refresh=useCallback(async()=>{
   const [inventory,soon]=await Promise.all([api("/pantry/items"),api("/pantry/expiring?withinDays=7")]);
   setItems(inventory);setExpiring(soon);
 },[]);
 useEffect(()=>{refresh().catch(e=>Alert.alert("Kiler yüklenemedi",e.message));},[refresh]);
 const add=async()=>{
   const amount=Number(quantity);
   if(!name.trim()||!Number.isFinite(amount)||amount<=0){Alert.alert("Bilgileri kontrol et","Ürün adı ve sıfırdan büyük miktar gerekli.");return;}
   setLoading(true);
   try{
     await api(editingId?("/pantry/items/"+editingId):"/pantry/items",{method:editingId?"PATCH":"POST",body:JSON.stringify({name,quantity:amount,unit,category,expiryDate:expiry.trim()||null})});
     setName("");setQuantity("1");setExpiry("");setEditingId(null);setShowForm(false);await refresh();
   }catch(e:any){Alert.alert("Ürün eklenemedi",e.message);}finally{setLoading(false);}
 };
 const edit=(item:PantryItem)=>{setEditingId(item.id);setName(item.name);setQuantity(String(item.quantity));setUnit(item.unit);setCategory(item.category);setExpiry(item.expiryDate||"");setShowForm(true);};
 const remove= (item:PantryItem)=>Alert.alert("Ürünü kaldır",item.name+" kilerden arşivlensin mi?",[{text:"Vazgeç",style:"cancel"},{text:"Kaldır",style:"destructive",onPress:async()=>{try{await api("/pantry/items/"+item.id,{method:"DELETE"});await refresh();}catch(e:any){Alert.alert("İşlem başarısız",e.message);}}}]);
 const loadMatches=async()=>{try{setMatches(await api("/pantry/recipe-matches",{method:"POST",body:"{}"}));setShowMatches(true);}catch(e:any){Alert.alert("Tarifler getirilemedi",e.message);}};
 const confirmUse=(match:Match)=>{
   if(match.available.length===0){Alert.alert("Stok hareketi yok","Bu tarif için kilerden düşülecek eşleşen ürün bulunamadı.");return;}
   Alert.alert("Malzemeleri stoktan düş",match.title+" için eşleşen "+match.available.length+" malzeme stoktan düşülecek. Onaylıyor musun?",[{text:"İptal",style:"cancel"},{text:"Onayla",onPress:async()=>{
     try{
       const operationKey="pantry-"+Date.now()+"-"+Math.random().toString(36).slice(2,9);
       const lines=match.available.map(x=>({pantryItemId:x.pantryItemId,quantity:x.required,unit:x.unit}));
       await api("/pantry/recipe-consumptions",{method:"POST",body:JSON.stringify({operationKey,items:lines})});
       Alert.alert("Stok güncellendi","Kullanılan malzemeler kilerden düşüldü.");await refresh();await loadMatches();
     }catch(e:any){Alert.alert("Stok güncellenemedi",e.message);}
   }}]);
 };
 return <AppShell><ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={s.scroll}>
   <Header title="Akıllı Kiler" subtitle="Evindeki malzemeleri takip et"/>
   <View style={s.card}>
    <Text style={s.eyebrow}>ENVANTER</Text>
    <View style={s.sectionRow}><Text style={s.heading}>{items.length} ürün</Text><Pressable onPress={()=>setShowForm(v=>!v)}><Text style={s.smallLink}>{showForm?"Kapat":editingId?"Düzenle":"+ Ürün ekle"}</Text></Pressable></View>
    {showForm&&<View>
      <Field value={name} onChangeText={setName} placeholder="Ürün adı (örn. Yumurta)"/>
      <View style={{flexDirection:"row",gap:8}}><Field value={quantity} onChangeText={setQuantity} keyboardType="decimal-pad" placeholder="Miktar" style={{flex:1}}/><Field value={unit} onChangeText={setUnit} placeholder="Birim (g, adet...)" style={{flex:1}}/></View>
      <Field value={category} onChangeText={setCategory} placeholder="Kategori (DAIRY, VEGETABLE...)"/>
      <Field value={expiry} onChangeText={setExpiry} placeholder="Son kullanma (YYYY-AA-GG) — isteğe bağlı"/>
      <Button onPress={add} loading={loading}>{editingId?"Değişiklikleri kaydet":"Kilere ekle"}</Button>
    </View>}
    {items.length===0?<Text style={s.body}>Kilerin henüz boş. İlk ürününü ekleyerek başla.</Text>:items.map(item=><View key={item.id} style={{paddingVertical:12,borderTopWidth:1,borderTopColor:colors.line,flexDirection:"row",alignItems:"center",justifyContent:"space-between"}}>
      <View style={{flex:1,paddingRight:10}}><Pressable onPress={()=>edit(item)}><Text style={{fontSize:16,fontWeight:"800",color:colors.ink}}>{item.name} · Düzenle</Text></Pressable><Text style={s.recipeMeta}>{item.quantity} {item.unit} · {item.category}</Text>{item.expiryDate&&<Text style={{fontSize:12,color:item.expired?"#b42318":item.expiringSoon?"#a65335":colors.muted,marginTop:3}}>{item.expired?"Son kullanma tarihi geçti":item.expiringSoon?"Yakında sona eriyor":"SKT"} · {item.expiryDate}</Text>}</View>
      <Pressable onPress={()=>remove(item)}><Text style={{color:"#b42318",fontWeight:"800"}}>Kaldır</Text></Pressable>
    </View>)}
   </View>
   <View style={s.card}><Text style={s.eyebrow}>ÖNCE BUNLARI KULLAN</Text><Text style={s.heading}>Son kullanma tarihi yaklaşanlar</Text>
    {expiring.length===0?<Text style={[s.body,{marginTop:8}]}>7 gün içinde tarihi dolacak ürün yok.</Text>:expiring.map(item=><Text key={item.id} style={[s.body,{marginTop:8}]}>{item.expired?"⚠️":"⏳"} {item.name} — {item.expiryDate}</Text>)}
   </View>
   <View style={s.card}><Text style={s.eyebrow}>ELİNDEKİLERLE NE PİŞİRİRSİN?</Text><Text style={s.heading}>Tarif önerileri</Text><Text style={[s.body,{marginTop:6}]}>Kilerindeki malzemeleri sabit tariflerle karşılaştır. Eksikler ayrıca gösterilir.</Text><Button onPress={loadMatches}>Tarifleri bul</Button>
    {showMatches&&matches.map(match=><View key={match.recipeId} style={{borderTopWidth:1,borderTopColor:colors.line,marginTop:14,paddingTop:14}}>
      <Text style={{fontSize:17,fontWeight:"800",color:colors.ink}}>{match.title}</Text><Text style={s.recipeMeta}>Malzeme eşleşmesi: %{Math.round(match.coverage*100)} · {match.servings} porsiyon</Text>
      {match.missing.length>0&&<Text style={[s.body,{marginTop:7}]}>Eksikler: {match.missing.map(x=>x.name+" ("+x.required+" "+x.unit+")").join(", ")}</Text>}
      {match.available.length>0&&<Text style={[s.body,{marginTop:5}]}>Kilerden kullanılacak: {match.available.map(x=>x.name).join(", ")}</Text>}
      <Button secondary onPress={()=>confirmUse(match)}>Kullanılanları stoktan düş</Button>
    </View>)}
   </View>
   <Button secondary onPress={()=>router.back()}>Geri dön</Button>
 </ScrollView></AppShell>;
}
