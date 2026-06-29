-- Sistemi kullanacak kullanici kayitlarini tutar.
CREATE TABLE users (
    -- BIGSERIAL otomatik artan id uretir.
    id BIGSERIAL PRIMARY KEY,

    -- Kullanici adi unique olmali; iki kullanici ayni username'i alamaz.
    username VARCHAR(100) NOT NULL UNIQUE,

    -- Email unique olmali; iki kullanici ayni email'i alamaz.
    email VARCHAR(255) NOT NULL UNIQUE,

    -- Kullanici sifresinin BCrypt hash degeri.
    password_hash VARCHAR(255),

    -- Kullanici rolu. Deger verilmezse USER olur.
    role VARCHAR(50) NOT NULL DEFAULT 'USER',

    -- Kullanici aktif mi bilgisini tutar.
    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    -- Kaydin olusturulma ve son guncellenme zamanlari.
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Cihaz kayitlarini tutan ana tablo.
CREATE TABLE devices (
    -- BIGSERIAL otomatik artan id uretir.
    id BIGSERIAL PRIMARY KEY,

    -- Cihazin kullaniciya gorunen adi.
    name VARCHAR(150) NOT NULL,

    -- Kamera, sensor gibi cihaz turu.
    device_type VARCHAR(100) NOT NULL,

    -- Cihazin durum bilgisi. Deger verilmezse ACTIVE olur.
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',

    -- Cihazin bulundugu yer. Bos olabilir.
    location VARCHAR(255),

    -- Kaydin olusturulma ve son guncellenme zamanlari.
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Hangi kullanici hangi cihazi gorebilir bilgisini tutar.
CREATE TABLE user_devices (
    -- Iliskinin kullanici tarafi.
    user_id BIGINT NOT NULL,

    -- Iliskinin cihaz tarafi.
    device_id BIGINT NOT NULL,

    -- Kullaniciya cihaz yetkisinin verildigi zaman.
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Ayni kullaniciya ayni cihaz ikinci kez eklenemez.
    PRIMARY KEY (user_id, device_id),

    -- Kullanici silinirse yetki kayitlari da silinir.
    CONSTRAINT fk_user_devices_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    -- Cihaz silinirse yetki kayitlari da silinir.
    CONSTRAINT fk_user_devices_device
        FOREIGN KEY (device_id) REFERENCES devices (id) ON DELETE CASCADE
);

-- Cihazlara bagli alarm kayitlarini tutan tablo.
CREATE TABLE alarms (
    -- Alarm kaydinin otomatik artan id degeri.
    id BIGSERIAL PRIMARY KEY,

    -- Alarm hangi cihaza aitse o cihazin devices.id degeri burada tutulur.
    device_id BIGINT NOT NULL,

    -- Alarm tipi: MOTION, TEMPERATURE, CONNECTION gibi.
    alarm_type VARCHAR(100) NOT NULL,

    -- Alarm onemi. Deger verilmezse MEDIUM olur.
    severity VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',

    -- Alarm aciklamasi.
    description TEXT NOT NULL,

    -- Alarmin gerceklestigi zaman.
    occurred_at TIMESTAMPTZ NOT NULL,

    -- Alarm cozulduyse cozulme zamani. Cozulmediyse NULL kalabilir.
    resolved_at TIMESTAMPTZ,

    -- Alarm kaydinin sisteme eklenme zamani.
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- device_id ile devices tablosundaki id arasinda bag kurar.
    CONSTRAINT fk_alarms_device
        -- Cihaz silinirse ona ait alarmlar da otomatik silinir.
        FOREIGN KEY (device_id) REFERENCES devices (id) ON DELETE CASCADE
);

-- Username ile kullanici aramayi hizlandirir.
CREATE INDEX idx_users_username ON users (username);

-- Status alanina gore cihaz aramayi hizlandirir.
CREATE INDEX idx_devices_status ON devices (status);

-- Kullanicinin cihazlarini listelemeyi hizlandirir.
CREATE INDEX idx_user_devices_user_id ON user_devices (user_id);

-- Cihaza yetkili kullanicilari listelemeyi hizlandirir.
CREATE INDEX idx_user_devices_device_id ON user_devices (device_id);

-- Bir cihazin alarmlarini listelemeyi hizlandirir.
CREATE INDEX idx_alarms_device_id ON alarms (device_id);

-- Alarm zamanina gore siralama/filtreleme yapmayi hizlandirir.
CREATE INDEX idx_alarms_occurred_at ON alarms (occurred_at);

-- Demo verileri. Kullanici sifresi tum demo kullanicilar icin: user123
INSERT INTO users (username, email, password_hash, role, enabled, created_at, updated_at)
VALUES
    ('selin', 'selin@example.com', '$2a$10$AFAIR9FFIfrGe9TnglPCP.y14oSo7D/JlhwGPx9Y1GhQ3y.gU7cz.', 'USER', TRUE, NOW(), NOW()),
    ('burak', 'burak@example.com', '$2a$10$AFAIR9FFIfrGe9TnglPCP.y14oSo7D/JlhwGPx9Y1GhQ3y.gU7cz.', 'USER', TRUE, NOW(), NOW()),
    ('zeynep.admin', 'zeynep.admin@example.com', '$2a$10$AFAIR9FFIfrGe9TnglPCP.y14oSo7D/JlhwGPx9Y1GhQ3y.gU7cz.', 'ADMIN', TRUE, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

INSERT INTO devices (name, device_type, status, location, created_at, updated_at)
SELECT 'Depo Kamerasi', 'CAMERA', 'ACTIVE', 'Depo Girisi', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM devices WHERE name = 'Depo Kamerasi');

INSERT INTO devices (name, device_type, status, location, created_at, updated_at)
SELECT 'Sicaklik Sensoru', 'SENSOR', 'ACTIVE', 'Uretim Hatti', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM devices WHERE name = 'Sicaklik Sensoru');

INSERT INTO devices (name, device_type, status, location, created_at, updated_at)
SELECT 'Ana Gateway', 'GATEWAY', 'ACTIVE', 'Sunucu Odasi', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM devices WHERE name = 'Ana Gateway');

INSERT INTO devices (name, device_type, status, location, created_at, updated_at)
SELECT 'Kapi Sensoru', 'SENSOR', 'MAINTENANCE', 'Arka Kapi', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM devices WHERE name = 'Kapi Sensoru');

INSERT INTO devices (name, device_type, status, location, created_at, updated_at)
SELECT 'Yangin Sensoru', 'SENSOR', 'PASSIVE', 'Ofis Kat 2', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM devices WHERE name = 'Yangin Sensoru');

INSERT INTO devices (name, device_type, status, location, created_at, updated_at)
SELECT 'Jenerator', 'GENERATOR', 'ACTIVE', 'Enerji Odasi', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM devices WHERE name = 'Jenerator');

INSERT INTO devices (name, device_type, status, location, created_at, updated_at)
SELECT 'UPS', 'UPS', 'ACTIVE', 'Sunucu Odasi', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM devices WHERE name = 'UPS');

INSERT INTO user_devices (user_id, device_id)
SELECT u.id, d.id
FROM users u
JOIN devices d ON d.name IN ('Depo Kamerasi', 'Sicaklik Sensoru', 'Ana Gateway')
WHERE u.username = 'selin'
ON CONFLICT (user_id, device_id) DO NOTHING;

INSERT INTO user_devices (user_id, device_id)
SELECT u.id, d.id
FROM users u
JOIN devices d ON d.name IN ('Kapi Sensoru', 'Yangin Sensoru')
WHERE u.username = 'burak'
ON CONFLICT (user_id, device_id) DO NOTHING;

INSERT INTO user_devices (user_id, device_id)
SELECT u.id, d.id
FROM users u
JOIN devices d ON d.name IN ('Depo Kamerasi', 'Ana Gateway', 'Yangin Sensoru')
WHERE u.username = 'zeynep.admin'
ON CONFLICT (user_id, device_id) DO NOTHING;

INSERT INTO user_devices (user_id, device_id)
SELECT u.id, d.id
FROM users u
JOIN devices d ON d.name IN ('Jenerator', 'UPS')
WHERE u.username IN ('selin', 'zeynep.admin')
ON CONFLICT (user_id, device_id) DO NOTHING;

INSERT INTO alarms (device_id, alarm_type, severity, description, occurred_at, created_at)
SELECT d.id, 'MOTION', 'HIGH', 'Depo girisinde hareket algilandi', NOW() - INTERVAL '2 hours', NOW()
FROM devices d
WHERE d.name = 'Depo Kamerasi'
  AND NOT EXISTS (SELECT 1 FROM alarms WHERE description = 'Depo girisinde hareket algilandi');

INSERT INTO alarms (device_id, alarm_type, severity, description, occurred_at, created_at)
SELECT d.id, 'TEMPERATURE', 'MEDIUM', 'Uretim hattinda sicaklik esik degerine yaklasti', NOW() - INTERVAL '45 minutes', NOW()
FROM devices d
WHERE d.name = 'Sicaklik Sensoru'
  AND NOT EXISTS (SELECT 1 FROM alarms WHERE description = 'Uretim hattinda sicaklik esik degerine yaklasti');

INSERT INTO alarms (device_id, alarm_type, severity, description, occurred_at, created_at)
SELECT d.id, 'CONNECTION', 'LOW', 'Ana gateway kisa sureli baglanti gecikmesi yasadi', NOW() - INTERVAL '20 minutes', NOW()
FROM devices d
WHERE d.name = 'Ana Gateway'
  AND NOT EXISTS (SELECT 1 FROM alarms WHERE description = 'Ana gateway kisa sureli baglanti gecikmesi yasadi');

INSERT INTO alarms (device_id, alarm_type, severity, description, occurred_at, created_at)
SELECT d.id, 'CONNECTION', 'HIGH', 'Kapi sensoru bakim modunda sinyal uretmiyor', NOW() - INTERVAL '10 minutes', NOW()
FROM devices d
WHERE d.name = 'Kapi Sensoru'
  AND NOT EXISTS (SELECT 1 FROM alarms WHERE description = 'Kapi sensoru bakim modunda sinyal uretmiyor');

INSERT INTO alarms (device_id, alarm_type, severity, description, occurred_at, created_at)
SELECT d.id, 'FAILURE', 'HIGH', 'Jenerator arizasi', NOW() - INTERVAL '1 day' + INTERVAL '2 hours', NOW()
FROM devices d
WHERE d.name = 'Jenerator'
  AND NOT EXISTS (SELECT 1 FROM alarms WHERE description = 'Jenerator arizasi');

INSERT INTO alarms (device_id, alarm_type, severity, description, occurred_at, created_at)
SELECT d.id, 'FAILURE', 'HIGH', 'UPS arizasi', NOW() - INTERVAL '1 day' + INTERVAL '4 hours', NOW()
FROM devices d
WHERE d.name = 'UPS'
  AND NOT EXISTS (SELECT 1 FROM alarms WHERE description = 'UPS arizasi');
