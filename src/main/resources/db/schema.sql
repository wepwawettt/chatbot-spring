-- Sistemi kullanacak kullanici kayitlarini tutar.
CREATE TABLE users (
    -- BIGSERIAL otomatik artan id uretir.
    id BIGSERIAL PRIMARY KEY,

    -- Kullanici adi unique olmali; iki kullanici ayni username'i alamaz.
    username VARCHAR(100) NOT NULL UNIQUE,

    -- Email unique olmali; iki kullanici ayni email'i alamaz.
    email VARCHAR(255) NOT NULL UNIQUE,

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
