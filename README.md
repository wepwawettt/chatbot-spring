# Device Alarm API

Spring Boot ile gelistirilmis sade bir cihaz ve alarm yonetimi REST API'si.

Proje su temel akisi gostermek icin hazirlandi:

```text
Controller -> DTO -> Service -> Repository -> Entity -> PostgreSQL
```

## Ozellikler

- Kullanici olusturma
- Cihaz ekleme, listeleme, guncelleme ve silme
- Kullaniciyi cihaza yetkilendirme
- Kullaniciya ait cihazlari listeleme
- Cihaz icin alarm olusturma
- Cihaza ait alarmlari listeleme
- Gemini API destekli chatbot ile cihaz ve alarm verilerini sorgulama
- PostgreSQL ve Docker Compose ile calisma

## Teknolojiler

```text
Java 25
Spring Boot 4.1
Spring Web MVC
Spring Data JPA
PostgreSQL
Swagger/OpenAPI
Docker Compose
RabbitMQ
Caffeine Cache
Spring Actuator
Maven Wrapper
```

## Production-ready AI parcalari

- Conversation history varsayilan olarak Caffeine cache'te tutulur; `CONVERSATION_PERSISTENCE_ENABLED=true` ile PostgreSQL persistence acilabilir.
- Multi-turn context desteklenir; onceki kullanici mesajlari referans cozmek icin Gemini'ye kisitli context olarak verilir.
- AI security layer mesajlari modelden once kontrol eder ve email, token, API key, JDBC URL gibi degerleri maskeler.
- Riskli promptlar Gemini'ye gonderilmez; admin alert loglanir, SMTP ayari varsa mail atilir.
- RabbitMQ ile event-driven akis desteklenir: `device.telemetry.generated`, `alarm.created`.
- Cihaz telemetry simulator servisinden RabbitMQ'ya event basilir; consumer kritik degerlerde alarm olusturur.
- Chat endpoint kullanici bazinda rate limit uygular.
- Cihaz/alarm sorgulari Caffeine cache ile cache'lenir; yazma islemlerinde cache temizlenir.
- Actuator health endpoint'i Kubernetes probe icin aciktir.
- Demo data initializer mevcut PostgreSQL verisini uygulama acilisinda zenginlestirir ve alarm tarihlerini bugune gore gunceller.

## Calistirma

Gelistirme icin sadece PostgreSQL'i Docker ile baslat:

```powershell
docker compose up -d postgres
```

API adresi:

```text
http://localhost:8080
```

Spring Boot uygulamasini IntelliJ'den veya Maven ile lokal calistir:

```powershell
.\mvnw.cmd spring-boot:run
```

Projeyi durdur:

```powershell
docker compose down
```

API'yi de Docker icinde calistirmak istersen:

```powershell
docker compose --profile app up -d --build
```

RabbitMQ management UI:

```text
http://localhost:15672
guest / guest
```

Kubernetes manifestlerini uygulamak icin:

```powershell
kubectl apply -f k8s/
```

`k8s/app.yaml` icindeki `device-alarm-demo:latest` image'i cluster tarafinda bulunmali veya kendi registry image adinla degistirilmelidir.

Database verisini de silerek sifirlamak icin:

```powershell
docker compose down -v
```

## Test

```powershell
.\mvnw.cmd test
```

## pgAdmin Baglantisi

```text
Host: 127.0.0.1
Port: 5433
Database: device_alarm_demo
Username: postgres
Password: postgres
```

Tablolar:

```text
users
devices
user_devices
alarms
conversations
conversation_messages
```

Not: `conversations` ve `conversation_messages` tablolari DB persistence opsiyonel kullanimi icin kalir. Varsayilan ayarda aktif chat context'i cache'tedir ve bu tablolar dolmaz.

Demo verisini kapatmak istersen:

```powershell
$env:DEMO_DATA_ENABLED="false"
```

## Temel API Akisi

1. Kullanici olustur.
2. Cihaz olustur.
3. Cihazi kullaniciya ata.
4. Cihaz icin alarm olustur.
5. Kullaniciya ait cihazlari veya cihaza ait alarmlari sorgula.

## Endpointler

### Users

```text
POST   /api/users
GET    /api/users
GET    /api/users/{id}
POST   /api/users/{userId}/devices
GET    /api/users/{userId}/devices
DELETE /api/users/{userId}/devices/{deviceId}
```

### Devices

```text
POST   /api/devices
GET    /api/devices
GET    /api/devices/{id}
PUT    /api/devices/{id}
DELETE /api/devices/{id}
```

### Alarms

```text
POST   /api/alarms
GET    /api/alarms/{id}
GET    /api/devices/{deviceId}/alarms
```

### Swagger

Uygulama calisirken Swagger UI adresi:

```text
http://localhost:8080/swagger-ui.html
```

OpenAPI JSON adresi:

```text
http://localhost:8080/v3/api-docs
```

### Chat

```text
POST /api/chat
GET  /api/chat/conversations
GET  /api/chat/conversations/{conversationId}/messages
DELETE /api/chat/conversations
```

## Ornek Requestler

Kullanici olustur:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/users `
  -ContentType "application/json" `
  -Body '{"username":"selin","email":"selin@example.com"}'
```

Cihaz olustur:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/devices `
  -ContentType "application/json" `
  -Body '{"name":"Kamera 1","deviceType":"CAMERA","location":"Depo"}'
```

Cihazi kullaniciya ata:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/users/1/devices `
  -ContentType "application/json" `
  -Body '{"deviceId":1}'
```

Alarm olustur:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/alarms `
  -ContentType "application/json" `
  -Body '{"deviceId":1,"alarmType":"MOTION","severity":"HIGH","description":"Hareket algilandi"}'
```

Kullanicinin cihazlarini listele:

```powershell
Invoke-RestMethod http://localhost:8080/api/users/1/devices
```

Cihazin alarmlarini listele:

```powershell
Invoke-RestMethod http://localhost:8080/api/devices/1/alarms
```

## Gemini API

Gemini API key kod icine yazilmaz. Uygulamayi baslatmadan once ortam degiskeni olarak ver:

Windows PowerShell:

```powershell
$env:GEMINI_API_KEY="BURAYA_KEY"
```

Linux/Mac:

```bash
export GEMINI_API_KEY="BURAYA_KEY"
```

Gercek API key'i koda yazma. Lokal secret dosyalari icin `.env` ve `.env.*` dosyalari `.gitignore` icindedir.

Yeni dogal dil endpoint'i:

```text
POST /api/chat
```

Ornek body:

```json
{
  "message": "Aktif cihazlari getir"
}
```

Gemini SQL uretmez ve veritabanina erisemez. Sadece `action + operation + filters` JSON'u dondurur; yetki, limit ve veritabani islemleri backend servislerinde uygulanir.

Chatbot yazma komutlarini dogrudan uygulamaz. Once backend icinde pending komut olusturur ve kullanicidan onay ister:

```text
Depo Kamerasi cihazini pasif yap
```

```text
#4 Depo Kamerasi cihazinin durumunu PASSIVE yapayim mi? Onay icin evet, iptal icin hayir yaz.
```

Kullanici `evet` derse backend servisleri calisir; `hayir` derse islem iptal edilir. Yazma komutlari admin yetkisi gerektirir.
