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
- PostgreSQL ve Docker Compose ile calisma

## Teknolojiler

```text
Java 25
Spring Boot 4.1
Spring Web MVC
Spring Data JPA
PostgreSQL
Docker Compose
Maven Wrapper
```

## Calistirma

Projeyi Docker ile baslat:

```powershell
docker compose up -d --build
```

API adresi:

```text
http://localhost:8080
```

Projeyi durdur:

```powershell
docker compose down
```

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

## Gelecek Calismalar

Bu proje final hedefindeki production-ready AI chatbot sisteminin temel API katmanidir.
Sonraki adimlarda asagidaki ozellikler eklenecektir:

### Chat ve Natural Language Sorgulama

- `POST /api/chat` endpointi eklenecek.
- Kullanici mesajlari natural language olarak alinacak.
- Ornek soru desteklenecek:

```text
Merhaba benim icin dun gerceklesen alarmlari bul
```

- Sistem kullanicinin yetkili oldugu cihazlari bulacak.
- Bu cihazlarda dun olusan alarmlari sorgulayacak.
- Emin oldugu cevaplarda net sonuc donecek.
- Emin olamadigi veya veri bulamadigi durumlarda bunu acikca belirtecek.

Ornek hedef cevap:

```text
Dun yetkili oldugunuz cihazlarda 2 adet ariza gerceklesti:
1. Jenerator arizasi, alarm id: 1
2. UPS arizasi, alarm id: 2
```

### Conversation History

- `conversations` tablosu eklenecek.
- `messages` tablosu eklenecek.
- Kullanici ve asistan mesajlari veritabaninda tutulacak.
- Multi-turn conversation destegi eklenecek.
- Onceki mesajlara gore context-aware cevap uretilecek.

### AI Agent Layer

- Controller ve Service katmanlarindan ayri bir AI Agent Layer kurulacak.
- Intent detection eklenecek.
- Alarm sorgulama, cihaz listeleme gibi gorevler agent tarafindan yonlendirilecek.
- Opsiyonel olarak cihaz ekleme gibi agent task'lari desteklenecek.

### Guvenlik ve Veri Maskeleme

- Modele gonderilecek kurumsal veriler icin maskeleme katmani eklenecek.
- Hassas alanlar modele gonderilmeden once temizlenecek veya maskelenecek.
- Guvenlik riski tasiyan sorular tespit edilecek.
- Riskli durumlarda yoneticiye bilgilendirme maili gonderilecek.

### RabbitMQ ve Event-Driven Communication

- RabbitMQ eklenecek.
- Alarm olusumu, chat mesaji ve simulator olaylari event olarak yayinlanacak.
- Asenkron mesaj isleme destegi eklenecek.
- Cihaz simulator servisi RabbitMQ uzerinden alarm/event uretecek.

### Rate Limiting ve Caching

- Chat ve API endpointleri icin rate limiting eklenecek.
- Sik sorgulanan veriler icin cache mekanizmasi eklenecek.
- Gereksiz veritabani sorgulari azaltilecek.

### API Documentation

- Swagger/OpenAPI dokumantasyonu eklenecek.
- Endpointler Swagger UI uzerinden test edilebilir hale getirilecek.

### Testler

- Service katmani icin unit testler eklenecek.
- Controller katmani icin API testleri eklenecek.
- Repository sorgulari icin integration testler eklenecek.

### Deployment

- Kubernetes deployment dosyalari eklenecek.
- PostgreSQL, uygulama ve RabbitMQ icin manifestler hazirlanacak.
- Proje tak-calistir sekilde calisacak hale getirilecek.
