package org.example.entity;

import java.io.Serializable;
import java.util.Objects;

public class UserDeviceId implements Serializable {
    // UserDevice entity'sindeki @Id alanlariyla ayni isimde olmalidir.
    // Bu iki alan birlikte user_devices tablosunun primary key'ini olusturur.
    private Long userId;
    private Long deviceId;

    public UserDeviceId() {
    }

    public UserDeviceId(Long userId, Long deviceId) {
        this.userId = userId;
        this.deviceId = deviceId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    @Override
    public boolean equals(Object object) {
        // Ayni nesneyse zaten esittir.
        if (this == object) {
            return true;
        }
        // Karsilastirilan nesne UserDeviceId degilse esit sayilmaz.
        if (!(object instanceof UserDeviceId that)) {
            return false;
        }
        // Iki id'nin esit olmasi icin hem userId hem deviceId ayni olmalidir.
        return Objects.equals(userId, that.userId) && Objects.equals(deviceId, that.deviceId);
    }

    @Override
    public int hashCode() {
        // equals ile ayni alanlari kullanir; JPA ve Java koleksiyonlari icin gereklidir.
        return Objects.hash(userId, deviceId);
    }
}
