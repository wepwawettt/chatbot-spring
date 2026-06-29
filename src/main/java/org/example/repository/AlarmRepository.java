package org.example.repository;

import org.example.entity.Alarm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AlarmRepository extends JpaRepository<Alarm, Long> {
    List<Alarm> findByDevice_Id(Long deviceId);

    // Chatbot icin belirli bir soru fonksiyonu degil; sadece kullanicinin gorebilecegi alarm kapsamidir.
    // LLM action/filtre cikarir, filtreleme ve limit uygulamasi backend servis katmaninda yapilir.
    @Query("""
            SELECT a
            FROM Alarm a
            JOIN FETCH a.device d
            WHERE :isAdmin = true
               OR EXISTS (
                    SELECT 1
                    FROM User u
                    JOIN u.devices ud
                    WHERE u.username = :username
                      AND ud.id = d.id
               )
            ORDER BY a.occurredAt DESC
            """)
    List<Alarm> findVisibleAlarmScope(@Param("username") String username, @Param("isAdmin") boolean isAdmin);
}
