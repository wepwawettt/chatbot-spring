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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AlarmService {
    // Request icinde severity gelmezse alarm varsayilan olarak MEDIUM kaydedilir.
    private static final String DEFAULT_SEVERITY = "MEDIUM";
    private static final ZoneId APP_ZONE = ZoneId.of("Europe/Istanbul");
    private static final Set<String> ALARM_STATUSES = Set.of("RESOLVED", "UNRESOLVED");

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
        alarm.setDevice(device);
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

        return alarmRepository.findByDevice_Id(deviceId)
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

    @Transactional
    public AlarmResponse resolveAlarm(Long id) {
        Alarm alarm = alarmRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Alarm not found: " + id));
        if (alarm.getResolvedAt() == null) {
            alarm.setResolvedAt(OffsetDateTime.now(APP_ZONE));
        }
        return toResponse(alarmRepository.save(alarm));
    }

    @Transactional(readOnly = true)
    public List<AlarmResponse> listAlarms(String username,
                                          boolean admin,
                                          String status,
                                          String dateRange,
                                          String startDate,
                                          String endDate,
                                          int limit) {
        return visibleAlarms(username, admin, status, dateRange, startDate, endDate)
                .stream()
                .limit(Math.max(0, limit))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countAlarms(String username,
                            boolean admin,
                            String status,
                            String dateRange,
                            String startDate,
                            String endDate) {
        return visibleAlarms(username, admin, status, dateRange, startDate, endDate).size();
    }

    @Transactional(readOnly = true)
    public List<GroupCount> groupAlarmsBy(String username,
                                          boolean admin,
                                          String groupBy,
                                          String status,
                                          String dateRange,
                                          String startDate,
                                          String endDate) {
        Map<String, Long> counts = visibleAlarms(username, admin, status, dateRange, startDate, endDate)
                .stream()
                .collect(Collectors.groupingBy(alarm -> groupKey(alarm, groupBy), LinkedHashMap::new, Collectors.counting()));

        return counts.entrySet()
                .stream()
                .map(entry -> new GroupCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(GroupCount::count).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GroupCount> getTopAlarmGroups(String username,
                                              boolean admin,
                                              String groupBy,
                                              String metric,
                                              String status,
                                              String dateRange,
                                              String startDate,
                                              String endDate,
                                              int limit) {
        // TODO: metric alarm_count disindaki metrikleri destekleyecek sekilde genisletilebilir.
        return groupAlarmsBy(username, admin, groupBy, status, dateRange, startDate, endDate)
                .stream()
                .limit(Math.max(0, limit))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DateRangeCount> countAlarmsForDateRanges(String username,
                                                         boolean admin,
                                                         String status,
                                                         List<String> dateRanges) {
        if (dateRanges == null || dateRanges.isEmpty()) {
            return List.of(new DateRangeCount("ALL", countAlarms(username, admin, status, null, null, null)));
        }

        return dateRanges.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> new DateRangeCount(
                        value,
                        countAlarms(username, admin, status, value, null, null)
                ))
                .toList();
    }

    // Alarm entity'sini, cihaz adini da ekleyerek AlarmResponse DTO'suna cevirir.
    private AlarmResponse toResponse(Alarm alarm) {
        return AlarmResponse.from(alarm, alarm.getDevice().getName());
    }

    // Cihazi id ile bulur; yoksa EntityNotFoundException firlatir.
    private Device findDeviceOrThrow(Long id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Device not found: " + id));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private List<Alarm> visibleAlarms(String username,
                                      boolean admin,
                                      String status,
                                      String dateRange,
                                      String startDate,
                                      String endDate) {
        String alarmStatus = allow(normalize(status), ALARM_STATUSES);
        DateWindow window = dateWindow(dateRange, startDate, endDate);

        return alarmRepository.findVisibleAlarmScope(username, admin)
                .stream()
                .filter(alarm -> alarmStatus == null || matchesAlarmStatus(alarm, alarmStatus))
                .filter(alarm -> window == null || window.contains(alarm.getOccurredAt()))
                .sorted(Comparator.comparing(Alarm::getOccurredAt).reversed())
                .toList();
    }

    private String groupKey(Alarm alarm, String groupBy) {
        String value = normalize(groupBy);
        if ("DEVICE".equals(value)) {
            return "#" + alarm.getDevice().getId() + " " + alarm.getDevice().getName();
        }
        if ("DEVICE_TYPE".equals(value)) {
            return alarm.getDevice().getDeviceType();
        }
        if ("STATUS".equals(value)) {
            return alarm.getResolvedAt() == null ? "UNRESOLVED" : "RESOLVED";
        }
        if ("DATE".equals(value) || "DAY".equals(value)) {
            return alarm.getOccurredAt().toLocalDate().toString();
        }
        return "ALL";
    }

    private boolean matchesAlarmStatus(Alarm alarm, String status) {
        if ("RESOLVED".equals(status)) {
            return alarm.getResolvedAt() != null;
        }
        if ("UNRESOLVED".equals(status)) {
            return alarm.getResolvedAt() == null;
        }
        return false;
    }

    private DateWindow dateWindow(String dateRange, String startDate, String endDate) {
        DateWindow explicitWindow = explicitDateWindow(startDate, endDate);
        if (explicitWindow != null) {
            return explicitWindow;
        }

        String value = normalize(dateRange);
        if (value == null) {
            return null;
        }

        OffsetDateTime now = OffsetDateTime.now(APP_ZONE);
        OffsetDateTime todayStart = now.toLocalDate().atStartOfDay(APP_ZONE).toOffsetDateTime();
        return switch (value) {
            case "TODAY" -> new DateWindow(todayStart, todayStart.plusDays(1));
            case "YESTERDAY" -> new DateWindow(todayStart.minusDays(1), todayStart);
            case "LAST_24_HOURS" -> new DateWindow(now.minusHours(24), now);
            case "LAST_7_DAYS" -> new DateWindow(now.minusDays(7), now);
            default -> null;
        };
    }

    private DateWindow explicitDateWindow(String startDate, String endDate) {
        try {
            OffsetDateTime start = isBlank(startDate)
                    ? null
                    : LocalDate.parse(startDate).atStartOfDay(APP_ZONE).toOffsetDateTime();
            OffsetDateTime end = isBlank(endDate)
                    ? null
                    : LocalDate.parse(endDate).plusDays(1).atStartOfDay(APP_ZONE).toOffsetDateTime();

            if (start == null && end == null) {
                return null;
            }
            return new DateWindow(
                    start == null ? OffsetDateTime.MIN : start,
                    end == null ? OffsetDateTime.MAX : end
            );
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String allow(String value, Set<String> allowedValues) {
        return value != null && allowedValues.contains(value) ? value : null;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.toUpperCase(Locale.forLanguageTag("tr-TR")).trim();
    }

    private record DateWindow(OffsetDateTime start, OffsetDateTime end) {
        private boolean contains(OffsetDateTime value) {
            return value != null && !value.isBefore(start) && value.isBefore(end);
        }
    }

    public record GroupCount(String key, long count) {
    }

    public record DateRangeCount(String dateRange, long count) {
    }
}
