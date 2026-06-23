# Device Alarm Demo

Bu proje sade bir Spring Boot REST API ornegidir. Ana akis:

```text
Controller -> Service -> Repository -> Entity -> Database
```

## Kalan Paketler

```text
controller  HTTP endpointleri
dto         Request ve response modelleri
entity      Veritabani tablo modelleri
repository  Veritabani erisim katmani
service     Is kurallari
```

## Endpointler

```text
POST   /api/users
GET    /api/users
GET    /api/users/{id}
POST   /api/users/{userId}/devices
GET    /api/users/{userId}/devices
DELETE /api/users/{userId}/devices/{deviceId}

POST   /api/devices
GET    /api/devices
GET    /api/devices/{id}
PUT    /api/devices/{id}
DELETE /api/devices/{id}

POST   /api/alarms
GET    /api/alarms/{id}
GET    /api/devices/{deviceId}/alarms
```

## Ornek Device Request

```json
{
  "name": "Kamera 1",
  "deviceType": "CAMERA",
  "location": "Depo"
}
```

## Ornek User Request

```json
{
  "username": "selin",
  "email": "selin@example.com"
}
```

## Ornek User Device Assign Request

```json
{
  "deviceId": 1
}
```

## pgAdmin Baglantisi

```text
Host: 127.0.0.1
Port: 5433
Database: device_alarm_demo
Username: postgres
Password: postgres
```

## Ornek Alarm Request

```json
{
  "deviceId": 1,
  "alarmType": "MOTION",
  "severity": "HIGH",
  "description": "Hareket algilandi"
}
```
