package org.example.repository;

import org.example.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    // Verilen kullanicinin devices listesinden cihazlari getirir.
    @Query("""
            SELECT d
            FROM User u
            JOIN u.devices d
            WHERE u.id = :userId
            """)
    List<Device> findByUserId(@Param("userId") Long userId);

    // Chatbot icin belirli bir soru fonksiyonu degil; sadece kullanicinin gorebilecegi cihaz kapsamidir.
    // LLM action/filtre cikarir, filtreleme ve limit uygulamasi backend servis katmaninda yapilir.
    @Query("""
            SELECT d
            FROM Device d
            WHERE :isAdmin = true
               OR EXISTS (
                    SELECT 1
                    FROM User u
                    JOIN u.devices ud
                    WHERE u.username = :username
                      AND ud.id = d.id
               )
            ORDER BY d.id
            """)
    List<Device> findVisibleDeviceScope(@Param("username") String username, @Param("isAdmin") boolean isAdmin);
}
