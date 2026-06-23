package org.example.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.dto.AlarmCreateRequest;
import org.example.dto.AlarmResponse;
import org.example.entity.Alarm;
import org.example.entity.Device;
import org.example.repository.AlarmRepository;
import org.example.repository.DeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AlarmService {
    // Request icinde severity gelmezse alarm varsayilan olarak MEDIUM kaydedilir.
    private static final String DEFAULT_SEVERITY = "MEDIUM";

    private final AlarmRepository alarmRepository;
    private final DeviceRepository deviceRepository;

    // Spring bu constructor ile gerekli repository nesnelerini service icine verir.
    public AlarmService(AlarmRepository alarmRepository, DeviceRepository deviceRepository) {
        this.alarmRepository = alarmRepository;
        this.deviceRepository = deviceRepository;
    }

    // Yeni alarm olusturur: once cihazi kontrol eder, sonra Alarm entity'sini kaydeder.
    @Transactional
    public AlarmResponse createAlarm(AlarmCreateRequest request) {
        // Alarm kaydetmeden once request icindeki deviceId gercekten var mi kontrol edilir.
        Device device = findDeviceOrThrow(request.getDeviceId());
        OffsetDateTime now = OffsetDateTime.now();

        // DTO'dan gelen bilgiler veritabanina kaydedilecek Alarm entity'sine aktarilir.
        Alarm alarm = new Alarm();
        alarm.setDeviceId(request.getDeviceId());
        alarm.setAlarmType(request.getAlarmType());
        alarm.setSeverity(isBlank(request.getSeverity()) ? DEFAULT_SEVERITY : request.getSeverity());
        alarm.setDescription(request.getDescription());
        alarm.setOccurredAt(request.getOccurredAt() == null ? now : request.getOccurredAt());
        alarm.setCreatedAt(now);

        // Kaydedilen Alarm entity'si, kullaniciya donecek AlarmResponse DTO'suna cevrilir.
        return AlarmResponse.from(alarmRepository.save(alarm), device.getName());
    }

    // Verilen cihaza ait alarm listesini getirir.
    @Transactional(readOnly = true)
    public List<AlarmResponse> getAlarmsByDeviceId(Long deviceId) {
        // Cihaz yoksa alarm aramak yerine hata firlatilir; varsa cihaz adi response'a eklenir.
        Device device = findDeviceOrThrow(deviceId);

        return alarmRepository.findByDeviceId(deviceId)
                .stream()
                // Her Alarm entity'si AlarmResponse DTO'suna cevrilir.
                .map(alarm -> AlarmResponse.from(alarm, device.getName()))
                .toList();
    }

    // Id ile tek bir alarm getirir; alarm yoksa hata firlatir.
    @Transactional(readOnly = true)
    public AlarmResponse getAlarmById(Long id) {
        return toResponse(alarmRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Alarm not found: " + id)));
    }

    // Alarm entity'sini, cihaz adini da ekleyerek AlarmResponse DTO'suna cevirir.
    private AlarmResponse toResponse(Alarm alarm) {
        String deviceName = deviceRepository.findById(alarm.getDeviceId())
                .map(Device::getName)
                // Alarm kaydi kalmis ama cihazi bulunamazsa response tamamen patlamasin diye kullanilir.
                .orElse("Unknown device");

        return AlarmResponse.from(alarm, deviceName);
    }

    // Cihazi id ile bulur; yoksa EntityNotFoundException firlatir.
    private Device findDeviceOrThrow(Long id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Device not found: " + id));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
