package org.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Configuration
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer {
    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);
    private static final ZoneId APP_ZONE = ZoneId.of("Europe/Istanbul");
    private static final String DEMO_PASSWORD = "user123";

    @Bean
    public ApplicationRunner seedDemoData(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        return args -> {
            DemoDataSeeder seeder = new DemoDataSeeder(jdbcTemplate, passwordEncoder);
            seeder.seed();
            log.info("Demo data refreshed with current alarm dates.");
        };
    }

    private static class DemoDataSeeder {
        private final JdbcTemplate jdbcTemplate;
        private final PasswordEncoder passwordEncoder;

        private DemoDataSeeder(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
            this.jdbcTemplate = jdbcTemplate;
            this.passwordEncoder = passwordEncoder;
        }

        private void seed() {
            seedUsers();
            seedDevices();
            seedAssignments();
            seedAlarms();
        }

        private void seedUsers() {
            String passwordHash = passwordEncoder.encode(DEMO_PASSWORD);
            upsertUser("selin", "selin@example.com", "USER", passwordHash);
            upsertUser("burak", "burak@example.com", "USER", passwordHash);
            upsertUser("ayse.operator", "ayse.operator@example.com", "USER", passwordHash);
            upsertUser("mehmet.teknik", "mehmet.teknik@example.com", "USER", passwordHash);
            upsertUser("deniz.viewer", "deniz.viewer@example.com", "USER", passwordHash);
            upsertUser("zeynep.admin", "zeynep.admin@example.com", "ADMIN", passwordHash);
        }

        private void seedDevices() {
            upsertDevice("Depo Kamerasi", "CAMERA", "ACTIVE", "Depo Girisi");
            upsertDevice("Paketleme Kamerasi", "CAMERA", "ACTIVE", "Paketleme Hatti");
            upsertDevice("Otopark Kamerasi", "CAMERA", "PASSIVE", "Otopark B2");
            upsertDevice("Sicaklik Sensoru", "SENSOR", "ACTIVE", "Uretim Hatti");
            upsertDevice("Soguk Oda Sensoru", "THERMOSTAT", "ACTIVE", "Soguk Oda");
            upsertDevice("Nem Sensoru", "SENSOR", "ACTIVE", "Hammadde Deposu");
            upsertDevice("Kapi Sensoru", "SENSOR", "MAINTENANCE", "Arka Kapi");
            upsertDevice("Yangin Sensoru", "SENSOR", "PASSIVE", "Ofis Kat 2");
            upsertDevice("Su Baskini Sensoru", "SENSOR", "ACTIVE", "Bodrum Pompa Odasi");
            upsertDevice("Ana Gateway", "GATEWAY", "ACTIVE", "Sunucu Odasi");
            upsertDevice("Kompresor Gateway", "GATEWAY", "MAINTENANCE", "Kompresor Odasi");
            upsertDevice("Jenerator", "GENERATOR", "ACTIVE", "Enerji Odasi");
            upsertDevice("UPS", "UPS", "ACTIVE", "Sunucu Odasi");
            upsertDevice("Cati UPS", "UPS", "PASSIVE", "Cati Teknik Alan");
            upsertDevice("Ofis Termostat", "THERMOSTAT", "ACTIVE", "Ofis Kat 1");
        }

        private void seedAssignments() {
            assign("selin", List.of("Depo Kamerasi", "Sicaklik Sensoru", "Soguk Oda Sensoru", "Ana Gateway", "Jenerator", "UPS"));
            assign("burak", List.of("Kapi Sensoru", "Yangin Sensoru", "Paketleme Kamerasi", "Su Baskini Sensoru", "Cati UPS"));
            assign("ayse.operator", List.of("Depo Kamerasi", "Paketleme Kamerasi", "Sicaklik Sensoru", "Nem Sensoru", "Su Baskini Sensoru"));
            assign("mehmet.teknik", List.of("Ana Gateway", "Kompresor Gateway", "Jenerator", "UPS", "Cati UPS", "Ofis Termostat"));
            assign("deniz.viewer", List.of("Otopark Kamerasi", "Ofis Termostat", "Yangin Sensoru"));
            assign("zeynep.admin", List.of("Depo Kamerasi", "Ana Gateway", "Yangin Sensoru", "Jenerator", "UPS", "Kompresor Gateway"));
        }

        private void seedAlarms() {
            OffsetDateTime now = OffsetDateTime.now(APP_ZONE);
            OffsetDateTime todayStart = now.toLocalDate().atStartOfDay(APP_ZONE).toOffsetDateTime();
            OffsetDateTime yesterdayStart = todayStart.minusDays(1);

            upsertAlarm("Depo Kamerasi", "MOTION", "HIGH",
                    "Depo girisinde hareket algilandi",
                    now.minusHours(2), null);
            upsertAlarm("Sicaklik Sensoru", "TEMPERATURE", "MEDIUM",
                    "Uretim hattinda sicaklik esik degerine yaklasti",
                    now.minusMinutes(45), now.minusMinutes(20));
            upsertAlarm("Ana Gateway", "CONNECTION", "LOW",
                    "Ana gateway kisa sureli baglanti gecikmesi yasadi",
                    now.minusMinutes(20), now.minusMinutes(12));
            upsertAlarm("Kapi Sensoru", "CONNECTION", "HIGH",
                    "Kapi sensoru bakim modunda sinyal uretmiyor",
                    now.minusMinutes(10), null);
            upsertAlarm("Soguk Oda Sensoru", "TEMPERATURE", "HIGH",
                    "Soguk oda sicakligi kritik seviyeye yukseldi",
                    now.minusHours(5), null);
            upsertAlarm("Su Baskini Sensoru", "CONNECTION", "MEDIUM",
                    "Bodrum pompa odasinda sensor baglanti kaybi",
                    now.minusHours(7), now.minusHours(6));

            upsertAlarm("Jenerator", "FAILURE", "HIGH",
                    "Jenerator arizasi",
                    yesterdayStart.plusHours(9).plusMinutes(15), null);
            upsertAlarm("UPS", "FAILURE", "HIGH",
                    "UPS arizasi",
                    yesterdayStart.plusHours(14).plusMinutes(35), yesterdayStart.plusHours(16));

            upsertAlarm("Paketleme Kamerasi", "MOTION", "MEDIUM",
                    "Paketleme hattinda vardiya disi hareket algilandi",
                    now.minusDays(2).minusHours(3), now.minusDays(2).minusHours(2));
            upsertAlarm("Kompresor Gateway", "CONNECTION", "HIGH",
                    "Kompresor gateway periyodik baglanti kopmasi",
                    now.minusDays(3).minusHours(4), null);
            upsertAlarm("Nem Sensoru", "TEMPERATURE", "LOW",
                    "Hammadde deposunda nem degeri izleme esigini asti",
                    now.minusDays(5).minusHours(1), now.minusDays(5));
            upsertAlarm("Cati UPS", "FAILURE", "MEDIUM",
                    "Cati UPS batarya sagligi dusuk",
                    now.minusDays(6).minusHours(2), null);
            upsertAlarm("Otopark Kamerasi", "CONNECTION", "LOW",
                    "Otopark kamerasi gece kaydinda gecikme yasadi",
                    now.minusDays(8), now.minusDays(8).plusHours(1));
        }

        private void upsertUser(String username, String email, String role, String passwordHash) {
            jdbcTemplate.update("""
                    INSERT INTO users (username, email, password_hash, role, enabled, created_at, updated_at)
                    VALUES (?, ?, ?, ?, TRUE, NOW(), NOW())
                    ON CONFLICT (username) DO UPDATE
                    SET email = EXCLUDED.email,
                        role = EXCLUDED.role,
                        enabled = TRUE,
                        updated_at = NOW()
                    """, username, email, passwordHash, role);
        }

        private void upsertDevice(String name, String deviceType, String status, String location) {
            Optional<Long> deviceId = findId("SELECT id FROM devices WHERE name = ? ORDER BY id LIMIT 1", name);
            if (deviceId.isPresent()) {
                jdbcTemplate.update("""
                        UPDATE devices
                        SET device_type = ?, status = ?, location = ?, updated_at = NOW()
                        WHERE id = ?
                        """, deviceType, status, location, deviceId.get());
                return;
            }

            jdbcTemplate.update("""
                    INSERT INTO devices (name, device_type, status, location, created_at, updated_at)
                    VALUES (?, ?, ?, ?, NOW(), NOW())
                    """, name, deviceType, status, location);
        }

        private void assign(String username, List<String> deviceNames) {
            Optional<Long> userId = findId("SELECT id FROM users WHERE username = ?", username);
            if (userId.isEmpty()) {
                return;
            }
            for (String deviceName : deviceNames) {
                Optional<Long> deviceId = findId("SELECT id FROM devices WHERE name = ? ORDER BY id LIMIT 1", deviceName);
                deviceId.ifPresent(id -> jdbcTemplate.update("""
                        INSERT INTO user_devices (user_id, device_id, created_at)
                        VALUES (?, ?, NOW())
                        ON CONFLICT (user_id, device_id) DO NOTHING
                        """, userId.get(), id));
            }
        }

        private void upsertAlarm(String deviceName,
                                 String alarmType,
                                 String severity,
                                 String description,
                                 OffsetDateTime occurredAt,
                                 OffsetDateTime resolvedAt) {
            Optional<Long> deviceId = findId("SELECT id FROM devices WHERE name = ? ORDER BY id LIMIT 1", deviceName);
            if (deviceId.isEmpty()) {
                return;
            }

            Optional<Long> alarmId = findId("SELECT id FROM alarms WHERE description = ? ORDER BY id LIMIT 1", description);
            if (alarmId.isPresent()) {
                jdbcTemplate.update("""
                        UPDATE alarms
                        SET device_id = ?,
                            alarm_type = ?,
                            severity = ?,
                            occurred_at = ?,
                            resolved_at = ?,
                            created_at = NOW()
                        WHERE id = ?
                        """, deviceId.get(), alarmType, severity, occurredAt, resolvedAt, alarmId.get());
                return;
            }

            jdbcTemplate.update("""
                    INSERT INTO alarms (device_id, alarm_type, severity, description, occurred_at, resolved_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, NOW())
                    """, deviceId.get(), alarmType, severity, description, occurredAt, resolvedAt);
        }

        private Optional<Long> findId(String sql, Object... args) {
            try {
                return Optional.ofNullable(jdbcTemplate.queryForObject(sql, Long.class, args));
            } catch (EmptyResultDataAccessException exception) {
                return Optional.empty();
            }
        }
    }
}
