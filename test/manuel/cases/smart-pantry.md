# Smart Pantry — Manuel Test Case'leri

**Modül:** Akıllı Kiler  
**Branch:** `feature/smart-pantry`  
**İlgili PR:** [#17](https://github.com/mtlgkbb20/mutfakci/pull/17)  
**Test türü:** Manuel fonksiyonel, API, güvenlik ve veri tutarlılığı testleri  
**Durum:** Testler dokümante edildi; henüz manuel olarak çalıştırılmış kabul edilmez.

## 1. Test ortamı ve ön koşullar

1. Backend'i ve Expo mobil uygulamasını çalıştır.
2. Flyway migration `V13__smart_pantry.sql`'in uygulandığını doğrula.
3. API istekleri için geçerli JWT token'ı olan iki ayrı kullanıcı hazırla: **Kullanıcı A** ve **Kullanıcı B**.
4. Tarif eşleştirmesi için tarif kataloğunda malzemeleri bilinen en az bir tarif bul.
5. Mümkünse izole/test veritabanı kullan. Aşağıdaki tarih testlerinde `TODAY`, testin yapıldığı yerel tarihtir.

### Örnek stok verisi

| Ürün | Miktar | Birim | Son kullanma |
|---|---:|---|---|
| Un | 1000 | g | TODAY + 30 gün |
| Süt | 1000 | ml | TODAY + 3 gün |
| Yumurta | 6 | adet | TODAY + 2 gün |
| Şeker | 500 | g | TODAY + 90 gün |
| Tereyağı | 250 | g | TODAY - 1 gün |

> Testleri birbirinden bağımsız çalıştır. Aynı test verisini tekrar kullanacaksan stok miktarlarını ve operation key değerlerini sıfırla/değiştir.

## 2. Test sonuçlarının kaydı

Her case için aşağıdaki sonucu kullan:

- **PASS:** Gerçek sonuç beklenen sonuçla aynı.
- **FAIL:** Gerçek sonuç beklenen sonuçtan farklı.
- **BLOCKED:** Ortam/veri/erişim engeli nedeniyle çalıştırılamadı.
- **NOT RUN:** Henüz çalıştırılmadı.

| Case ID | Sonuç | Gerçek sonuç / kanıt | Tarih |
|---|---|---|---|
| PAN-001 – PAN-032 | NOT RUN | | |

Test sırasında FAIL alınırsa ekran görüntüsü, request/response ve ilgili backend logunu ekle. Bu dokümandaki test case'ler çalıştırılmadan PASS olarak işaretlenmemelidir.

---

## 3. Kiler ürün yönetimi

### PAN-001 — Yeni ürün ekleme
**Ön koşul:** Kullanıcı A oturum açmış olmalı.  
**Adımlar:**
1. Akıllı Kiler ekranını aç.
2. Ürün adı `Un`, miktar `1000`, birim `g` gir.
3. Kategoriyi seç ve kaydet.
4. Ürün listesini yenile.

**Beklenen:** Ürün bir kez listelenir; adı, miktarı, birimi ve kategorisi doğru görünür. İlk stok hareketi `ADD` olarak kaydedilir.

### PAN-002 — Ürün bilgisi ve miktarı güncelleme
**Adımlar:**
1. PAN-001'de oluşturulan Un ürününü aç.
2. Miktarı 1500 g, adını Tam Buğday Unu olarak güncelle.
3. Listeyi ve hareket geçmişini kontrol et.

**Beklenen:** Güncel bilgiler görünür. 500 g fark için `ADJUST` hareketi oluşur.

### PAN-003 — Ürünü arşivleme
**Adımlar:**
1. Bir ürünün silme/arşivleme aksiyonunu kullan.
2. Aktif ürün listesini yenile.
3. Ürünün hareket geçmişini kontrol et.

**Beklenen:** API `ARCHIVED` durumu döndürür; ürün aktif listeden kalkar. Önceki hareket kayıtları korunur.

### PAN-004 — Boş ürün adı
**Adımlar:** Ürün adını boş veya yalnızca boşluk girerek kaydetmeyi dene.  
**Beklenen:** Validasyon hatası; ürün oluşturulmamalı.

### PAN-005 — Negatif miktar
**Adımlar:** Miktarı `-10` girerek ürün oluşturmayı dene.  
**Beklenen:** İstek reddedilmeli; stok kaydı ve hareket oluşmamalı.

### PAN-006 — Sıfır miktar
**Adımlar:** Miktarı `0` girerek ürün oluşturmayı dene.  
**Beklenen:** `quantity >= 0.001` doğrulaması nedeniyle istek reddedilmeli.

### PAN-007 — Desteklenmeyen birim
**Adımlar:** Birim alanına `litre-küp` veya rastgele bir değer gir.  
**Beklenen:** Desteklenmeyen birim hatası; kayıt oluşmamalı.

### PAN-008 — Desteklenen birim takma adı
**Adımlar:** Ürünü `adet` birimiyle oluştur.  
**Beklenen:** Birim normalize edilerek `piece` olarak saklanmalı/gösterilmeli.

---

## 4. Son kullanma tarihi

### PAN-009 — Süresi yaklaşan ürün
**Adımlar:** Son kullanma tarihi bugünden 3 gün sonrası olan ürün ekle.  
**Beklenen:** `GET /api/v1/pantry/expiring?withinDays=7` sonucunda yer almalı; `expiringSoon=true` olmalı.

### PAN-010 — Süresi geçmiş ürün
**Adımlar:** Son kullanma tarihi dünden önce olan ürün ekle.  
**Beklenen:** Expiring endpoint'inde yer almalı; `expired=true` olmalı.

### PAN-011 — Uzak tarihli ürün
**Adımlar:** Son kullanma tarihi bugünden 30 gün sonrası olan ürün ekle.  
**Beklenen:** 7 günlük expiring sorgusunda yer almamalı.

### PAN-012 — Son kullanma tarihi olmayan ürün
**Adımlar:** Expiry date boş olacak şekilde ürün ekle.  
**Beklenen:** Ürün kaydedilmeli; expiring sorgusunda yer almamalı.

### PAN-013 — Expiring gün sınırı
**Adımlar:**
1. `withinDays=0` ile sorgula.
2. `withinDays=7` ile sorgula.
3. `withinDays=91` ile sorgula.

**Beklenen:** 0 ve 7 kabul edilmeli. 90'dan büyük değer validasyondan geçmemeli. Sorgu, geçmiş tarihleri ve belirtilen gün sınırına kadar olan tarihleri kapsamalı.

---

## 5. Stok hareketleri ve birimler

### PAN-014 — İlk stok hareketi
**Adımlar:** Yeni bir ürün oluştur ve `GET /api/v1/pantry/movements` çağır.  
**Beklenen:** Ürün için `ADD` tipinde pozitif miktarlı bir hareket bulunmalı.

### PAN-015 — Manuel miktar artışı
**Adımlar:** 500 g ürünü 800 g olarak güncelle.  
**Beklenen:** `ADJUST` tipinde +300 g hareketi oluşmalı.

### PAN-016 — Manuel miktar azalışı
**Adımlar:** 800 g ürünü 650 g olarak güncelle.  
**Beklenen:** `ADJUST` tipinde -150 g hareketi oluşmalı; stok 650 g olmalı.

### PAN-017 — Gram/kilogram dönüşümü
**Adımlar:**
1. 1 kg un oluştur.
2. Tüketim endpoint'inde aynı ürün için 200 g düşüm gönder.

**Beklenen:** Stok 0.8 kg kalmalı (saklanan birim kg ise); dönüşüm 1000 g = 1 kg esasına göre yapılmalı.

### PAN-018 — Mililitre/litre dönüşümü
**Adımlar:** 1 l süt oluştur ve 250 ml tüketim gönder.  
**Beklenen:** Stok 0.75 l kalmalı (saklanan birim l ise).

### PAN-019 — Boyutlar arası uyumsuz birim
**Adımlar:** Gram cinsinden üründen ml cinsinden tüketim yapmayı dene.  
**Beklenen:** HTTP 400; stok ve hareket geçmişi değişmemeli.

### PAN-020 — Özel birim eşleşmesi
**Adımlar:** `paket` birimindeki üründen `paket` tüket; ardından aynı üründen `adet` tüketmeyi dene.  
**Beklenen:** Aynı özel birim kabul edilmeli; farklı özel birimler arasında dönüşüm yapılmamalı.

### PAN-021 — Ürün bazlı hareket geçmişi
**Adımlar:** `GET /api/v1/pantry/movements?itemId={itemId}` çağır.  
**Beklenen:** Yalnızca ilgili kullanıcıya ait, ilgili ürüne bağlı hareketler dönmeli.

---

## 6. Tarif eşleştirme

### PAN-022 — Tam malzeme eşleşmesi
**Ön koşul:** Katalogdan malzemeleri ve miktarları bilinen bir tarif seç.  
**Adımlar:** Tarifin tüm malzemelerini gereken miktarlarda kilere ekle; `POST /api/v1/pantry/recipe-matches` çağır.  
**Beklenen:** İlgili tarifin coverage değeri 1.0 olmalı; missing listesi boş olmalı.

### PAN-023 — Eksik malzeme
**Adımlar:** Seçilen tarifin bir malzemesini kilerden kaldır ve eşleştirmeyi tekrarla.  
**Beklenen:** Eksik malzeme `missing` listesinde, gerekli miktarı ve birimiyle görünmeli; coverage düşmeli.

### PAN-024 — Yetersiz miktar
**Adımlar:** Bir malzemeyi tarifin gerektirdiğinden daha az miktarda ekle.  
**Beklenen:** Bu malzeme mevcut olsa bile yeterli stok yoksa `missing` listesinde yer almalı.

### PAN-025 — Birim uyumlu eşleştirme
**Adımlar:** Tarif gram, kiler kilogram kullanacak şekilde aynı malzemeyi kaydet.  
**Beklenen:** g/kg dönüşümüyle miktar karşılaştırılmalı; yeterli stok varsa available listesinde olmalı.

### PAN-026 — Birim uyumsuz eşleştirme
**Adımlar:** Tarifin malzeme birimi ile kilerdeki ürünün birimini uyumsuz boyutlarda ayarla.  
**Beklenen:** Sistem malzemeyi mevcut/yeterli kabul etmemeli; missing listesinde göstermeli.

### PAN-027 — Eşleşmeyen ürün adı
**Adımlar:** Tarif malzemesini farklı bir ürün adıyla, eşleşmeyecek şekilde kaydet.  
**Beklenen:** Tahmini/eş anlamlı eşleşme yapılmamalı; exact normalized-name eşleşmesi bulunmadığı için malzeme eksik sayılmalı.

---

## 7. Tarif tüketimi ve işlem güvenliği

### PAN-028 — Onaylı stok tüketimi
**Ön koşul:** Tüketilecek ürünlerde yeterli stok bulunmalı.  
**Adımlar:**
1. Kullanıcı arayüzünde tüketim onayını ver veya API'ye istek gönder.
2. `POST /api/v1/pantry/recipe-consumptions` çağır.

**Örnek body:**
```json
{
  "operationKey": "manual-test-consume-001",
  "items": [
    {
      "pantryItemId": "PRODUCT_UUID",
      "quantity": 200,
      "unit": "g"
    }
  ]
}
```

**Beklenen:** `status=APPLIED`; stok 200 g azalmalı ve `CONSUME` hareketi oluşmalı.

### PAN-029 — Kullanıcı onayı olmadan tüketim olmaması
**Adımlar:** Mobil ekranda tüketim onay penceresini aç, vazgeç/iptal et.  
**Beklenen:** API tüketim isteği gönderilmemeli; stok ve hareket geçmişi değişmemeli.

### PAN-030 — Yetersiz stokta atomik ret
**Adımlar:**
1. İki ürün oluştur; ilkinde yeterli, ikincisinde yetersiz stok bulunsun.
2. İkisini aynı tüketim isteğinde gönder.

**Beklenen:** İstek başarısız olmalı; ilk ürün dahil hiçbir ürünün stoğu azalmamalı ve hiçbir tüketim hareketi oluşmamalı.

### PAN-031 — Aynı operation key ile tekrar istek
**Adımlar:**
1. PAN-028'deki isteği gönder.
2. Aynı body ve aynı `operationKey` ile tekrar gönder.

**Beklenen:** İlk cevap `APPLIED`; tekrar cevap `ALREADY_APPLIED`. İkinci istekte stok tekrar düşmemeli; ikinci tüketim hareketi oluşmamalı.

### PAN-032 — Aynı istekte aynı ürünün birden fazla satırı
**Adımlar:** Aynı `pantryItemId` için aynı istekte iki tüketim satırı gönder.  
**Beklenen:** Miktarlar toplanarak doğrulanmalı ve toplam kadar tek stok düşümü/hareketi uygulanmalı.

### PAN-033 — Eş zamanlı tüketimde negatif stok oluşmaması
**Adımlar:**
1. Stok miktarı 100 g olan tek ürün hazırla.
2. İki ayrı oturum/istemci üzerinden farklı operation key'lerle aynı anda 70 g tüketim isteği gönder.

**Beklenen:** En fazla bir istek başarılı olmalı; diğer istek yetersiz stok nedeniyle reddedilmeli. Son stok negatif olmamalı.

---

## 8. Kimlik doğrulama ve kullanıcı izolasyonu

### PAN-034 — Token olmadan listeleme
**Adımlar:** Authorization header olmadan `GET /api/v1/pantry/items` çağır.  
**Beklenen:** Yetkilendirme reddedilmeli (uygulama güvenlik konfigürasyonuna göre 401/403).

### PAN-035 — Kullanıcı A'nın verisinin Kullanıcı B'ye kapalı olması
**Adımlar:**
1. Kullanıcı A ile ürün oluştur ve UUID'sini kaydet.
2. Kullanıcı B token'ıyla `GET /items` çağır.
3. Kullanıcı B token'ıyla A'nın ürün UUID'sine PATCH ve DELETE çağır.
4. Kullanıcı B token'ıyla A'nın ürün UUID'si için movement history sorgula.

**Beklenen:** B, A'nın ürününü listeleyememeli veya değiştirememeli. Sahiplik gerektiren tekil kaynak istekleri kaynak bulunamadı/erişim reddi ile sonuçlanmalı; veri sızmamalı.

### PAN-036 — İstekten user ID enjekte etme
**Adımlar:** İstek body/query parametresine başka bir user ID eklemeyi dene.  
**Beklenen:** Sahiplik yalnızca doğrulanmış authentication principal üzerinden belirlenmeli; istemcinin gönderdiği user ID sahipliği değiştirememeli.

### PAN-037 — Arşivlenmiş ürüne tekrar erişim
**Adımlar:** Ürünü arşivle; aynı UUID ile PATCH, DELETE ve item history filtreli sorgularını dene.  
**Beklenen:** Arşivlenmiş ürün aktif kaynak olarak bulunmamalı/değiştirilememeli. Önceden oluşmuş hareket kayıtları korunmalı.

---

## 9. API hızlı referansı

Base path: `/api/v1/pantry`

| Method | Endpoint | Amaç |
|---|---|---|
| GET | `/items` | Kullanıcının aktif ürünleri |
| POST | `/items` | Ürün oluşturma |
| PATCH | `/items/{id}` | Ürün güncelleme |
| DELETE | `/items/{id}` | Ürünü arşivleme |
| GET | `/movements` | Hareket geçmişi |
| GET | `/movements?itemId={id}` | Ürüne ait hareket geçmişi |
| GET | `/expiring?withinDays=7` | Süresi geçmiş/yaklaşan ürünler |
| POST | `/recipe-matches` | Tarif-kiler eşleştirmesi |
| POST | `/recipe-consumptions` | Onaylı stok tüketimi |

### Ürün oluşturma örneği

```http
POST /api/v1/pantry/items
Authorization: Bearer <USER_A_TOKEN>
Content-Type: application/json
```

```json
{
  "name": "Un",
  "quantity": 1000,
  "unit": "g",
  "category": "BAKING",
  "expiryDate": "2026-12-20"
}
```

### Ürün güncelleme örneği

```http
PATCH /api/v1/pantry/items/PRODUCT_UUID
Authorization: Bearer <USER_A_TOKEN>
Content-Type: application/json
```

```json
{
  "name": "Un",
  "quantity": 1500,
  "unit": "g",
  "category": "BAKING",
  "expiryDate": "2026-12-20"
}
```

### Tarif eşleştirme örneği

```http
POST /api/v1/pantry/recipe-matches
Authorization: Bearer <USER_A_TOKEN>
Content-Type: application/json
```

Body gerekmez.

---

## 10. Manuel test tamamlanma kriterleri

- [ ] Ürün CRUD ve arşivleme case'leri tamamlandı.
- [ ] Son kullanma tarihi sınırları doğrulandı.
- [ ] ADD/ADJUST/CONSUME hareketlerinin doğruluğu kontrol edildi.
- [ ] g/kg ve ml/l dönüşümleri doğrulandı.
- [ ] Tarif eşleştirme ve eksik malzeme davranışı doğrulandı.
- [ ] Tüketim onayı, atomiklik ve idempotency doğrulandı.
- [ ] Eş zamanlı tüketim denendi; negatif stok oluşmadı.
- [ ] Kullanıcılar arası veri izolasyonu doğrulandı.
- [ ] FAIL/BLOCKED sonuçları ve kanıtları kaydedildi.

**Not:** Bu dosya test senaryolarını tanımlar; herhangi bir manuel testin çalıştırıldığı veya başarılı olduğu anlamına gelmez.
