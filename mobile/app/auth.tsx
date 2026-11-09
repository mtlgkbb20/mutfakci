import { useRouter } from "expo-router";
import { useState } from "react";
import { Text, View } from "react-native";
import { api, setSession } from "../src/api";
import { Button, Field, Screen, s } from "../src/ui";
export default function Auth() {
 const router=useRouter();
 const [register,setRegister]=useState(false),[verifying,setVerifying]=useState(false);
 const [email,setEmail]=useState(""),[password,setPassword]=useState(""),[name,setName]=useState(""),[code,setCode]=useState("");
 const [error,setError]=useState(""),[notice,setNotice]=useState(""),[loading,setLoading]=useState(false);
 const submit=async()=>{
  setLoading(true);setError("");setNotice("");
  try {
   if(verifying){
    const data=await api("/auth/verify-email",{method:"POST",body:JSON.stringify({email,code})});
    await setSession(data.token,data.user);router.replace("/(tabs)/dashboard");
   } else if(register){
    const data=await api("/auth/register",{method:"POST",body:JSON.stringify({email,password,displayName:name})});
    setVerifying(true);setNotice(data.message||"Doğrulama e-postası gönderildiyse gelen kutunu kontrol et.");
   } else {
    const data=await api("/auth/login",{method:"POST",body:JSON.stringify({email,password})});
    await setSession(data.token,data.user);router.replace("/(tabs)/dashboard");
   }
  } catch(e:any){setError(e.message);} finally{setLoading(false);}
 };
 const resend=async()=>{
  setLoading(true);setError("");setNotice("");
  try{const data=await api("/auth/resend-verification",{method:"POST",body:JSON.stringify({email})});setNotice(data.message||"İstek alındı. E-posta adresini kontrol et.");}
  catch(e:any){setError(e.message);}finally{setLoading(false);}
 };
 return <Screen><View style={{flex:1,justifyContent:"center"}}><View style={s.card}>
  <Text style={s.eyebrow}>KITCHEN AI</Text>
  <Text style={s.title}>{verifying?"E-posta adresini doğrula":"Mutfağındaki akıllı yardımcı."}</Text>
  <Text style={[s.body,{marginTop:10,marginBottom:14}]}>{verifying?"E-posta adresine gönderilen 6 haneli kodu gir.":"İstediğin sonucu anlat, birlikte ulaşalım."}</Text>
  {!verifying&&register&&<Field placeholder="Adın" value={name} onChangeText={setName}/>}
  <Field placeholder="E-posta" keyboardType="email-address" autoCapitalize="none" value={email} onChangeText={setEmail} editable={!verifying}/>
  {!verifying&&<Field placeholder="Şifre (en az 12 karakter)" secureTextEntry value={password} onChangeText={setPassword}/>}
  {verifying&&<Field placeholder="6 haneli doğrulama kodu" keyboardType="number-pad" maxLength={6} value={code} onChangeText={setCode}/>}
  {!!error&&<Text style={s.error}>{error}</Text>}{!!notice&&<Text style={s.body}>{notice}</Text>}
  <Button onPress={submit} loading={loading}>{verifying?"E-postayı doğrula":register?"Kayıt ol":"Giriş yap"}</Button>
  {verifying?<><Button secondary onPress={resend} loading={loading}>Yeni kod gönder</Button><Button secondary onPress={()=>{setVerifying(false);setRegister(false);setCode("");}}>Giriş ekranına dön</Button></>:
   <Button secondary onPress={()=>{setRegister(!register);setError("");}}>{register?"Giriş yap":"Hesap oluştur"}</Button>}
 </View></View></Screen>;
}