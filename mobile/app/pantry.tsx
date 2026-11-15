import { useCallback, useEffect, useState } from "react";
import { Alert, Modal, Pressable, ScrollView, Text, View } from "react-native";
import { api } from "../src/api";
import { AppShell, Button, Field, Header, s, colors } from "../src/ui";

type PantryItem = { id:string; name:string; category:string; quantity:number; unit:string; expiryDate?:string|null; expired:boolean; expiringSoon:boolean; version:number };
type Movement = { id:string; pantryItemId:string; movementType:string; quantityDelta:number; unit:string; reason?:string; createdAt:string };
type Match = { recipeId:string; title:string; servings:number; coverage:number; available:{name:string;pantryItemId:string;required:number;unit:string;stock:number;stockUnit:string}[]; missing:{name:string;required:number;unit:string}[] };

const modalCard = { backgroundColor: colors.white, borderRadius: 22, padding: 20, maxHeight: "82%" as const, width: "100%" as const };
const modalBackdrop = { flex: 1 as const, backgroundColor: "rgba(30,24,20,0.48)", justifyContent: "center" as const, padding: 20 };

export default function PantryScreen(){
 const [items,setItems]=useState<PantryItem[]>([]);
 const [expiring,setExpiring]=useState<PantryItem[]>([]);
 const [matches,setMatches]=useState<Match[]>([]);
 const [movements,setMovements]=useState<Movement[]>([]);
 const [name,setName]=useState("");
 const [quantity,setQuantity]=useState("");
 const [unit,setUnit]=useState("");
 const [category,setCategory]=useState("");
 const [expiry,setExpiry]=useState("");
 const [loading,setLoading]=useState(false);
 const [showForm,setShowForm]=useState(false);
 const [showMatches,setShowMatches]=useState(false);
 const [showInventoryModal,setShowInventoryModal]=useState(false);
 const [showMovementsModal,setShowMovementsModal]=useState(false);
 const [showRecipeInfo,setShowRecipeInfo]=useState(false);
 const [editingId,setEditingId]=useState<string|null>(null);
 const refresh=useCallback(async()=>{
   const [inventory,soon,history]=await Promise.all([api("/pantry/items"),api("/pantry/expiring?withinDays=7"),api("/pantry/movements")]);
   setItems(inventory);setExpiring(soon);setMovements(history);
 },[]);
 useEffect(()=>{refresh().catch(e=>Alert.alert("Buzdolabım yüklenemedi",e.message));},[refresh]);
 const resetForm=()=>{setName("");setQuantity("");setUnit("");setCategory("");setExpiry("");setEditingId(null);setShowForm(false);};
 const add=async()=>{
   const amount=Number(quantity);
   if(!name.trim()||!quantity.trim()||!Number.isFinite(amount)||amount<=0||!unit.trim()){
     Alert.alert("Bilgileri kontrol et","Ürün adı, miktar ve birim alanlarını doldur.");return;
   }
   setLoading(true);
   try{
     await api(editingId?("/pantry/items/"+editingId):"/pantry/items",{method:editingId?"PATCH":"POST",body:JSON.stringify({name:name.trim(),quantity:amount,unit:unit.trim(),category:category.trim()||null,expiryDate:expiry.trim()||null})});
     resetForm();await refresh();
   }catch(e:any){Alert.alert("Ürün kaydedilemedi",e.message);}finally{setLoading(false);}
 };
 const edit=(item:PantryItem)=>{setEditingId(item.id);setName(item.name);setQuantity(String(item.quantity));setUnit(item.unit);setCategory(item.category==="OTHER"?"":item.category);setExpiry(item.expiryDate||"");setShowInventoryModal(false);setShowForm(true);};
 const remove=(item:PantryItem)=>Alert.alert("Ürünü kaldır",item.name+" buzdolabından kaldırılsın mı?",[{text:"Vazgeç",style:"cancel"},{text:"Kaldır",style:"destructive",onPress:async()=>{try{await api("/pantry/items/"+item.id,{method:"DELETE"});await refresh();}catch(e:any){Alert.alert("İşlem başarısız",e.message);}}}]);
 const loadMatches=async()=>{try{setMatches(await api("/pantry/recipe-matches",{method:"POST",body:"{}"}));setShowMatches(true);}catch(e:any){Alert.alert("Tarifler getirilemedi",e.message);}};
 const confirmUse=(match:Match)=>{
   if(match.available.length===0){Alert.alert("Stok hareketi yok","Bu tarif için buzdolabından düşülecek eşleşen ürün bulunamadı.");return;}
   Alert.alert("Malzemeleri stoktan düş",match.title+" için eşleşen "+match.available.length+" malzeme stoktan düşülecek. Onaylıyor musun?",[{text:"İptal",style:"cancel"},{text:"Onayla",onPress:async()=>{
     try{const operationKey="pantry-"+Date.now()+"-"+Math.random().toString(36).slice(2,9);const lines=match.available.map(x=>({pantryItemId:x.pantryItemId,quantity:x.required,unit:x.unit}));await api("/pantry/recipe-consumptions",{method:"POST",body:JSON.stringify({operationKey,items:lines})});Alert.alert("Stok güncellendi","Kullanılan malzemeler buzdolabından düşüldü.");await refresh();await loadMatches();}
     catch(e:any){Alert.alert("Stok güncellenemedi",e.message);}
   }}]);
 };
 const movementLabel=(m:Movement)=>{const itemName=items.find(i=>i.id===m.pantryItemId)?.name||"Ürün";const prefix=m.movementType==="CONSUME"?"−":"+";return prefix+Math.abs(m.quantityDelta)+" "+m.unit+" · "+itemName+" · "+new Date(m.createdAt).toLocaleDateString("tr-TR");};
 const inventoryRows=(list:PantryItem[])=>list.map(item=><View key={item.id} style={{paddingVertical:12,borderTopWidth:1,borderTopColor:colors.line,flexDirection:"row",alignItems:"center",gap:12}}><Pressable style={{flex:1}} onPress={()=>edit(item)}><Text style={{fontSize:16,fontWeight:"700",color:colors.ink}}>{item.name}</Text></Pressable><Pressable onPress={()=>remove(item)}><Text style={{color:"#b42318",fontWeight:"700"}}>Kaldır</Text></Pressable></View>);
 return <AppShell><ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={s.scroll}>
   <Header title="Buzdolabım" subtitle="Evindeki malzemeleri takip et"/>
   <View style={s.card}>
    <Text style={s.eyebrow}>ENVANTER</Text>
    <View style={s.sectionRow}><Text style={s.heading}>{items.length} ürün</Text><Pressable onPress={()=>{setEditingId(null);setName("");setQuantity("");setUnit("");setCategory("");setExpiry("");setShowForm(v=>!v);}}><Text style={s.smallLink}>{showForm?"Kapat":"+ Ürün ekle"}</Text></Pressable></View>
    {showForm&&<View>
      <Field value={name} onChangeText={setName} placeholder="Ürün adı"/>
      <View style={{flexDirection:"row",gap:8}}><Field value={quantity} onChangeText={setQuantity} keyboardType="decimal-pad" placeholder="Miktar" style={{flex:1}}/><Field value={unit} onChangeText={setUnit} placeholder="Birim (isteğe göre)" style={{flex:1}}/></View>
      <Field value={category} onChangeText={setCategory} placeholder="Kategori (isteğe bağlı)"/>
      <Field value={expiry} onChangeText={setExpiry} placeholder="Son kullanma tarihi (YYYY-AA-GG) — isteğe bağlı"/>
      <Button onPress={add} loading={loading}>{editingId?"Değişiklikleri kaydet":"Ekle"}</Button>
    </View>}
    {items.length===0?<Text style={s.body}>Buzdolabın henüz boş. İlk ürününü ekleyerek başla.</Text>:inventoryRows(items.slice(0,3))}
    {items.length>3&&<Pressable onPress={()=>setShowInventoryModal(true)} style={{paddingTop:12}}><Text style={s.smallLink}>Tüm ürünleri gör ({items.length})</Text></Pressable>}
   </View>
   <View style={s.card}><Text style={s.eyebrow}>ÖNCE BUNLARI KULLAN</Text><Text style={s.heading}>Son kullanma tarihi yaklaşanlar</Text>
    {expiring.length===0?<Text style={[s.body,{marginTop:8}]}>7 gün içinde tarihi dolacak ürün yok.</Text>:expiring.slice(0,3).map(item=><Text key={item.id} style={[s.body,{marginTop:8}]}>{item.expired?"⚠️":"⏳"} {item.name} — {item.expiryDate}</Text>)}
    {expiring.length>3&&<Text style={[s.recipeMeta,{marginTop:8}]}>{expiring.length-3} ürün daha</Text>}
   </View>
   <View style={s.card}><Text style={s.eyebrow}>STOK HAREKETLERİ</Text><View style={s.sectionRow}><Text style={s.heading}>Son değişiklikler</Text>{movements.length>3&&<Pressable onPress={()=>setShowMovementsModal(true)}><Text style={s.smallLink}>Tümünü gör</Text></Pressable>}</View>
    {movements.length===0?<Text style={[s.body,{marginTop:8}]}>Henüz stok hareketi yok.</Text>:movements.slice(0,3).map(m=><Text key={m.id} style={[s.body,{marginTop:7}]}>{movementLabel(m)}</Text>)}
   </View>
   <View style={s.card}><Text style={s.eyebrow}>ELİNDEKİLERLE NE PİŞİRİRSİN?</Text><Text style={s.heading}>Tarif önerileri</Text><Text style={[s.body,{marginTop:6}]}>Buzdolabındaki malzemeleri tariflerle karşılaştırıp eksiklerini gösterelim.</Text><Button onPress={loadMatches}>Tarifleri bul</Button>
    <Pressable onPress={()=>setShowRecipeInfo(true)} style={{paddingTop:12}}><Text style={s.smallLink}>Tarifler nasıl bulunuyor?</Text></Pressable>
    {showMatches&&matches.map(match=><View key={match.recipeId} style={{borderTopWidth:1,borderTopColor:colors.line,marginTop:14,paddingTop:14}}>
      <Text style={{fontSize:17,fontWeight:"800",color:colors.ink}}>{match.title}</Text><Text style={s.recipeMeta}>Malzeme eşleşmesi: %{Math.round(match.coverage*100)} · {match.servings} porsiyon</Text>
      {match.missing.length>0&&<Text style={[s.body,{marginTop:7}]}>Eksikler: {match.missing.map(x=>x.name+" ("+x.required+" "+x.unit+")").join(", ")}</Text>}
      {match.available.length>0&&<Text style={[s.body,{marginTop:5}]}>Buzdolabından kullanılacak: {match.available.map(x=>x.name).join(", ")}</Text>}
      <Button secondary onPress={()=>confirmUse(match)}>Kullanılanları stoktan düş</Button>
    </View>)}
   </View>
 </ScrollView>
 <Modal visible={showInventoryModal} transparent animationType="fade" onRequestClose={()=>setShowInventoryModal(false)}><View style={modalBackdrop}><View style={modalCard}><View style={s.sectionRow}><Text style={s.heading}>Tüm ürünler</Text><Pressable onPress={()=>setShowInventoryModal(false)}><Text style={s.smallLink}>Kapat</Text></Pressable></View><ScrollView>{inventoryRows(items)}</ScrollView></View></View></Modal>
 <Modal visible={showMovementsModal} transparent animationType="fade" onRequestClose={()=>setShowMovementsModal(false)}><View style={modalBackdrop}><View style={modalCard}><View style={s.sectionRow}><Text style={s.heading}>Tüm değişiklikler</Text><Pressable onPress={()=>setShowMovementsModal(false)}><Text style={s.smallLink}>Kapat</Text></Pressable></View><ScrollView>{movements.map(m=><Text key={m.id} style={[s.body,{paddingVertical:10,borderTopWidth:1,borderTopColor:colors.line}]}>{movementLabel(m)}</Text>)}</ScrollView></View></View></Modal>
 <Modal visible={showRecipeInfo} transparent animationType="fade" onRequestClose={()=>setShowRecipeInfo(false)}><View style={modalBackdrop}><View style={modalCard}><View style={s.sectionRow}><Text style={s.heading}>Tarifler nasıl bulunuyor?</Text><Pressable onPress={()=>setShowRecipeInfo(false)}><Text style={s.smallLink}>Kapat</Text></Pressable></View><Text style={s.body}>Şu anda yapay zekâ ile internetten tarif aranmıyor. Uygulamadaki sabit tarif kataloğu kullanılıyor. Buzdolabındaki ürün adları tariflerin malzeme listeleriyle karşılaştırılıyor; ad ve birim eşleşen, miktarı yeterli malzemeler mevcut sayılıyor. Eksik veya miktarı yetmeyen malzemeler eksik listesine giriyor. Tarifler eşleşme oranına göre sıralanıyor. Stok, yalnızca “Kullanılanları stoktan düş” onayını verdiğinde azaltılıyor.</Text></View></View></Modal>
 </AppShell>;
}
