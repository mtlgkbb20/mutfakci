import { useCallback, useEffect, useState } from "react";
import { Alert, Modal, Pressable, ScrollView, Text, View } from "react-native";
import { api } from "../src/api";
import { AppShell, Button, Field, Header, s, colors } from "../src/ui";

type PantryItem = { id:string; name:string; category:string; quantity:number; unit:string; expiryDate?:string|null; expired:boolean; expiringSoon:boolean; version:number };
type Movement = { id:string; pantryItemId:string; movementType:string; quantityDelta:number; unit:string; reason?:string; createdAt:string };
type RecipeCategory = "MEAL" | "DESSERT" | "DRINK";
type Match = { recipeId:string; title:string; servings:number; coverage:number; available:{name:string;pantryItemId:string;required:number;unit:string;stock:number;stockUnit:string}[]; missing:{name:string;required:number;unit:string}[] };
type MatchPage = { category:RecipeCategory; page:number; size:number; hasMore:boolean; recipes:Match[] };

const modalCard = { backgroundColor: colors.white, borderRadius: 22, padding: 20, maxHeight: "82%" as const, width: "100%" as const };
const modalBackdrop = { flex: 1 as const, backgroundColor: "rgba(30,24,20,0.48)", justifyContent: "center" as const, padding: 20 };
const recipeCategories: {value:RecipeCategory;label:string;emoji:string}[] = [
 {value:"MEAL",label:"Yemek",emoji:"🍲"},
 {value:"DESSERT",label:"Tatlı",emoji:"🍰"},
 {value:"DRINK",label:"İçecek",emoji:"🥤"}
];

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
 const [loadingMatches,setLoadingMatches]=useState(false);
 const [loadingMore,setLoadingMore]=useState(false);
 const [showForm,setShowForm]=useState(false);
 const [showMatches,setShowMatches]=useState(false);
 const [showInventoryModal,setShowInventoryModal]=useState(false);
 const [selectedInventoryItem,setSelectedInventoryItem]=useState<PantryItem|null>(null);
 const [showMovementsModal,setShowMovementsModal]=useState(false);
 const [showRecipeInfo,setShowRecipeInfo]=useState(false);
 const [editingId,setEditingId]=useState<string|null>(null);
 const [recipeCategory,setRecipeCategory]=useState<RecipeCategory>("MEAL");
 const [matchPage,setMatchPage]=useState(0);
 const [hasMoreMatches,setHasMoreMatches]=useState(false);
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
 const loadMatches=async()=>{
   if(loadingMatches||loadingMore)return;
   setLoadingMatches(true);
   try{
     const result:MatchPage=await api("/pantry/recipe-matches?category="+recipeCategory+"&page=0");
     setMatches(result.recipes);setMatchPage(result.page);setHasMoreMatches(result.hasMore);setShowMatches(true);
   }catch(e:any){Alert.alert("Tarifler getirilemedi",e.message);}finally{setLoadingMatches(false);}
 };
 const loadMoreMatches=async()=>{
   if(!hasMoreMatches||loadingMore||loadingMatches)return;
   const nextPage=matchPage+1;setLoadingMore(true);
   try{
     const result:MatchPage=await api("/pantry/recipe-matches?category="+recipeCategory+"&page="+nextPage);
     setMatches(current=>[...current,...result.recipes]);setMatchPage(result.page);setHasMoreMatches(result.hasMore);
   }catch(e:any){Alert.alert("Diğer tarifler getirilemedi",e.message);}finally{setLoadingMore(false);}
 };
 const selectRecipeCategory=(next:RecipeCategory)=>{
   if(next===recipeCategory||loadingMatches||loadingMore)return;
   setRecipeCategory(next);setMatches([]);setShowMatches(false);setHasMoreMatches(false);setMatchPage(0);
 };
 const confirmUse=(match:Match)=>{
   if(match.available.length===0){Alert.alert("Stok hareketi yok","Bu tarif için buzdolabından düşülecek eşleşen ürün bulunamadı.");return;}
   Alert.alert("Malzemeleri stoktan düş",match.title+" için eşleşen "+match.available.length+" malzeme stoktan düşülecek. Onaylıyor musun?",[{text:"İptal",style:"cancel"},{text:"Onayla",onPress:async()=>{
     try{const operationKey="pantry-"+Date.now()+"-"+Math.random().toString(36).slice(2,9);const lines=match.available.map(x=>({pantryItemId:x.pantryItemId,quantity:x.required,unit:x.unit}));await api("/pantry/recipe-consumptions",{method:"POST",body:JSON.stringify({operationKey,items:lines})});Alert.alert("Stok güncellendi","Kullanılan malzemeler buzdolabından düşüldü.");await refresh();await loadMatches();}
     catch(e:any){Alert.alert("Stok güncellenemedi",e.message);}
   }}]);
 };
 const movementRows=(list:Movement[])=>list.map(m=>{
   const itemName=items.find(i=>i.id===m.pantryItemId)?.name||"Ürün";
   const isIncrease=Number(m.quantityDelta)>=0;
   const unitLabel=m.unit==="piece"?"adet":m.unit;
   return <View key={m.id} style={{paddingVertical:9,borderTopWidth:1,borderTopColor:colors.line,flexDirection:"row",alignItems:"center",gap:9}}>
     <View style={{width:30,height:30,borderRadius:15,backgroundColor:isIncrease?"#e8f5ec":"#fdecec",alignItems:"center",justifyContent:"center"}}>
       <Text style={{fontSize:18,fontWeight:"800",color:isIncrease?"#16803c":"#c62828"}}>{isIncrease?"↑":"↓"}</Text>
     </View>
     <View style={{flex:1}}>
       <Text style={{fontSize:14,fontWeight:"700",color:colors.ink}}>{itemName}</Text>
       <Text style={s.recipeMeta}>{new Date(m.createdAt).toLocaleDateString("tr-TR")}</Text>
     </View>
     <Text style={{fontSize:14,fontWeight:"800",color:isIncrease?"#16803c":"#c62828"}}>{Math.abs(m.quantityDelta)} {unitLabel}</Text>
   </View>;
 });
 const inventoryRows=(list:PantryItem[])=>list.map(item=><Pressable key={item.id} onPress={()=>{setSelectedInventoryItem(item);setShowInventoryModal(false);}} style={{paddingVertical:13,borderTopWidth:1,borderTopColor:colors.line,flexDirection:"row",alignItems:"center",gap:12}}>
   <Text numberOfLines={1} style={{flex:1,fontSize:15,fontWeight:"700",color:colors.ink}}>{item.name}</Text>
   <Text style={{fontSize:14,fontWeight:"700",color:colors.muted}}>{item.quantity} {item.unit==="piece"?"adet":item.unit}</Text>
   <Text style={{fontSize:20,color:colors.muted}}>›</Text>
 </Pressable>);
 const inventorySummary=items.slice(0,3).map(item=>item.name).join(" · ")+(items.length>3?" · "+(items.length-3)+" ürün daha":"");
 const openEditFromDetail=()=>{if(selectedInventoryItem){const item=selectedInventoryItem;setSelectedInventoryItem(null);edit(item);}};

 return <AppShell><ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={s.scroll}>
   <Header title="Buzdolabım" subtitle="Evindeki malzemeleri takip et"/>
   <View style={s.card}>
    <Text style={s.eyebrow}>ENVANTER</Text>
    <Text style={[s.body,{marginTop:5,marginBottom:10}]}>Buzdolabındaki ürünlerini ve kalan miktarlarını buradan takip edebilirsin.</Text>
    <View style={s.sectionRow}><Text style={s.heading}>{items.length} ürün</Text><Pressable onPress={()=>{setEditingId(null);setName("");setQuantity("");setUnit("");setCategory("");setExpiry("");setShowForm(v=>!v);}}><Text style={s.smallLink}>{showForm?"Kapat":"+ Ürün ekle"}</Text></Pressable></View>
    {showForm&&<View>
      <Field value={name} onChangeText={setName} placeholder="Ürün adı"/>
      <View style={{flexDirection:"row",gap:8}}><Field value={quantity} onChangeText={setQuantity} keyboardType="decimal-pad" placeholder="Miktar" style={{flex:1}}/><Field value={unit} onChangeText={setUnit} placeholder="Birim" style={{flex:1}}/></View>
      <Field value={category} onChangeText={setCategory} placeholder="Kategori (isteğe bağlı)"/>
      <Field value={expiry} onChangeText={setExpiry} placeholder="Son kullanma tarihi (YYYY-AA-GG) — isteğe bağlı"/>
      <Button onPress={add} loading={loading}>{editingId?"Değişiklikleri kaydet":"Ekle"}</Button>
    </View>}
    {items.length===0?<Text style={s.body}>Buzdolabın henüz boş. İlk ürününü ekleyerek başla.</Text>:<Text style={[s.body,{lineHeight:22}]}>{inventorySummary}</Text>}
    {items.length>0&&<Pressable onPress={()=>setShowInventoryModal(true)} style={{paddingTop:12}}><Text style={s.smallLink}>Tüm ürünleri gör ({items.length})</Text></Pressable>}
   </View>
   <View style={s.card}><Text style={s.eyebrow}>ÖNCE BUNLARI KULLAN</Text><Text style={s.heading}>Son kullanma tarihi yaklaşanlar</Text>
    {expiring.length===0?<Text style={[s.body,{marginTop:8}]}>7 gün içinde tarihi dolacak ürün yok.</Text>:expiring.slice(0,3).map(item=><Text key={item.id} style={[s.body,{marginTop:8}]}>{item.expired?"⚠️":"⏳"} {item.name} — {item.expiryDate}</Text>)}
    {expiring.length>3&&<Text style={[s.recipeMeta,{marginTop:8}]}>{expiring.length-3} ürün daha</Text>}
   </View>
   <View style={s.card}><Text style={s.eyebrow}>STOK HAREKETLERİ</Text><View style={s.sectionRow}><Text style={s.heading}>Son değişiklikler</Text>{movements.length>3&&<Pressable onPress={()=>setShowMovementsModal(true)}><Text style={s.smallLink}>Tümünü gör</Text></Pressable>}</View>
    {movements.length===0?<Text style={[s.body,{marginTop:8}]}>Henüz stok hareketi yok.</Text>:movementRows(movements.slice(0,3))}
   </View>
   <View style={s.card}>
    <Text style={s.eyebrow}>ELİNDEKİLERLE NE PİŞİRİRSİN?</Text>
    <Text style={s.heading}>Tarif önerileri</Text>
    <Text style={[s.body,{marginTop:6}]}>Önce görmek istediğin tarif türünü seç.</Text>
    <View style={{flexDirection:"row",gap:8,marginTop:12}}>
      {recipeCategories.map(option=><Pressable key={option.value} onPress={()=>selectRecipeCategory(option.value)} disabled={loadingMatches||loadingMore} style={{flex:1,paddingVertical:12,paddingHorizontal:5,borderRadius:14,borderWidth:recipeCategory===option.value?2:1,borderColor:recipeCategory===option.value?colors.orange:colors.line,backgroundColor:recipeCategory===option.value?"#fff7ed":colors.white,alignItems:"center"}}>
        <Text style={{fontSize:20}}>{option.emoji}</Text>
        <Text style={{fontWeight:"700",fontSize:13,color:colors.ink,marginTop:4}}>{option.label}</Text>
      </Pressable>)}
    </View>
    <View style={{marginTop:12}}><Button onPress={loadMatches} loading={loadingMatches||loadingMore}>İlk 3 tarifi bul</Button></View>
    <Pressable onPress={()=>setShowRecipeInfo(true)} style={{paddingTop:12}}><Text style={s.smallLink}>Tarifler nasıl bulunuyor?</Text></Pressable>
    {showMatches&&matches.length===0&&<Text style={[s.body,{marginTop:12}]}>Bu kategoride şu an eşleşen tarif bulunamadı.</Text>}
    {showMatches&&matches.map(match=><View key={match.recipeId} style={{borderTopWidth:1,borderTopColor:colors.line,marginTop:14,paddingTop:14}}>
      <Text style={{fontSize:17,fontWeight:"800",color:colors.ink}}>{match.title}</Text>
      <Text style={s.recipeMeta}>Malzeme eşleşmesi: %{Math.round(match.coverage*100)} · {match.servings} porsiyon</Text>
      {match.available.length>0&&<View style={{marginTop:8}}><Text style={[s.recipeMeta,{fontWeight:"700",marginBottom:5}]}>Elindekiler</Text><Text style={s.body}>{match.available.map(x=>x.name).join(", ")}</Text></View>}
      {match.missing.length>0&&<View style={{marginTop:8}}><Text style={[s.recipeMeta,{fontWeight:"700",marginBottom:5}]}>Eksik malzemeler</Text><Text style={s.body}>{match.missing.map(x=>x.name+" ("+x.required+" "+x.unit+")").join(", ")}</Text></View>}
      {match.available.length>0&&<View style={{marginTop:10}}><Button secondary onPress={()=>confirmUse(match)}>Kullanılanları stoktan düş</Button></View>}
    </View>)}
    {showMatches&&hasMoreMatches&&<View style={{marginTop:14}}><Button secondary onPress={loadMoreMatches} loading={loadingMore}>3 tarif daha göster</Button></View>}
   </View>
 </ScrollView>
 <Modal visible={showInventoryModal} transparent animationType="fade" onRequestClose={()=>setShowInventoryModal(false)}><View style={modalBackdrop}><View style={modalCard}><View style={s.sectionRow}><Text style={s.heading}>Tüm ürünler</Text><Pressable onPress={()=>setShowInventoryModal(false)}><Text style={s.smallLink}>Kapat</Text></Pressable></View><ScrollView>{inventoryRows(items)}</ScrollView></View></View></Modal>
 <Modal visible={selectedInventoryItem!==null} transparent animationType="fade" onRequestClose={()=>setSelectedInventoryItem(null)}><View style={modalBackdrop}><View style={modalCard}>{selectedInventoryItem&&<><View style={s.sectionRow}><Text style={s.heading}>{selectedInventoryItem.name}</Text><Pressable onPress={()=>setSelectedInventoryItem(null)}><Text style={s.smallLink}>Kapat</Text></Pressable></View><View style={{paddingVertical:16,borderTopWidth:1,borderTopColor:colors.line,marginTop:12}}><Text style={s.eyebrow}>KALAN MİKTAR</Text><Text style={{fontSize:25,fontWeight:"800",color:colors.ink,marginTop:5}}>{selectedInventoryItem.quantity} {selectedInventoryItem.unit==="piece"?"adet":selectedInventoryItem.unit}</Text></View><View style={{paddingVertical:12,borderTopWidth:1,borderTopColor:colors.line}}><Text style={s.recipeMeta}>Kategori</Text><Text style={[s.body,{marginTop:4}]}>{selectedInventoryItem.category&&selectedInventoryItem.category!=="OTHER"?selectedInventoryItem.category:"Belirtilmemiş"}</Text></View><View style={{paddingVertical:12,borderTopWidth:1,borderTopColor:colors.line}}><Text style={s.recipeMeta}>Son kullanma tarihi</Text><Text style={[s.body,{marginTop:4}]}>{selectedInventoryItem.expiryDate||"Belirtilmemiş"}</Text>{selectedInventoryItem.expired&&<Text style={{color:"#b42318",fontWeight:"700",marginTop:4}}>Son kullanma tarihi geçmiş</Text>}{!selectedInventoryItem.expired&&selectedInventoryItem.expiringSoon&&<Text style={{color:"#b7791f",fontWeight:"700",marginTop:4}}>Son kullanma tarihi yaklaşıyor</Text>}</View><View style={{flexDirection:"row",gap:10,marginTop:12}}><View style={{flex:1}}><Button onPress={openEditFromDetail}>Düzenle</Button></View><View style={{flex:1}}><Button secondary onPress={()=>{const item=selectedInventoryItem;setSelectedInventoryItem(null);remove(item);}}>Ürünü kaldır</Button></View></View></>}</View></View></Modal>
 <Modal visible={showMovementsModal} transparent animationType="fade" onRequestClose={()=>setShowMovementsModal(false)}><View style={modalBackdrop}><View style={modalCard}><View style={s.sectionRow}><Text style={s.heading}>Tüm değişiklikler</Text><Pressable onPress={()=>setShowMovementsModal(false)}><Text style={s.smallLink}>Kapat</Text></Pressable></View><ScrollView>{movementRows(movements)}</ScrollView></View></View></Modal>
 <Modal visible={showRecipeInfo} transparent animationType="fade" onRequestClose={()=>setShowRecipeInfo(false)}><View style={modalBackdrop}><View style={modalCard}><View style={s.sectionRow}><Text style={s.heading}>Tarifler nasıl bulunuyor?</Text><Pressable onPress={()=>setShowRecipeInfo(false)}><Text style={s.smallLink}>Kapat</Text></Pressable></View><Text style={s.body}>Tarifler PostgreSQL kataloğundan SQL ile bulunur. Buzdolabındaki ürün adları standart malzeme kataloğundaki alias kayıtlarıyla eşleştirilir. Seçtiğin yemek, tatlı veya içecek kategorisindeki tarifler; mevcut malzeme oranı, eksik malzeme sayısı ve miktar uyumluluğuna göre sıralanır. İlk üç tarif gösterilir; daha fazlasını istediğinde sonraki üç tarif getirilir. Eksik malzemeler tarif kartında ayrıca listelenir. Stok yalnızca “Kullanılanları stoktan düş” onayından sonra azaltılır.</Text></View></View></Modal>
 </AppShell>;
}
