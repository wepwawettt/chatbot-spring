package org.example.service;

import org.example.dto.AlarmCreateRequest;
import org.example.dto.AlarmResponse;
import org.example.event.DeviceTelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true")
public class DeviceTelemetryConsumer {
    private static final Logger log = LoggerFactory.getLogger(DeviceTelemetryConsumer.class);
    private static final double HIGH_TEMPERATURE_CELSIUS = 85.0;
    private static final double MEDIUM_TEMPERATURE_CELSIUS = 78.0;
    private static final double LOW_TEMPERATURE_CELSIUS = 70.0;
    private static final double HIGH_BATTERY_PERCENT = 10.0;
    private static final double MEDIUM_BATTERY_PERCENT = 20.0;
    private static final double LOW_BATTERY_PERCENT = 30.0;

    private final AlarmService alarmService;

    public DeviceTelemetryConsumer(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    @RabbitListener(queues = "${app.rabbitmq.telemetry-queue}")
    public void handleTelemetry(DeviceTelemetryEvent event) {
        if (event == null || event.deviceId() == null) {
            return;
        }

        AlarmCreateRequest request = alarmRequest(event);
        if (request == null) {
            log.info("Telemetry consumed without alarm. deviceId={}, temperature={}, battery={}, online={}",
                    event.deviceId(), event.temperatureCelsius(), event.batteryPercent(), event.online());
            return;
        }

        AlarmResponse alarm = alarmService.createAlarm(request);
        log.info("Telemetry alarm created. alarmId={}, deviceId={}, type={}, severity={}",
                alarm.getId(), event.deviceId(), alarm.getAlarmType(), alarm.getSeverity());
    }

    private AlarmCreateRequest alarmRequest(DeviceTelemetryEvent event) {
        if (!event.online()) {
            return new AlarmCreateRequest(
                    event.deviceId(),
                    "CONNECTION",
                    "HIGH",
                    "Simulasyon: cihaz cevrimdisi gorundu.",
                    event.occurredAt()
            );
        }

        String temperatureSeverity = temperatureSeverity(event.temperatureCelsius());
        if (temperatureSeverity != null) {
            return new AlarmCreateRequest(
                    event.deviceId(),
                    "TEMPERATURE",
                    temperatureSeverity,
                    "Simulasyon: sicaklik " + temperatureSeverity.toLowerCase()
                            + " esigine ulasti (" + event.temperatureCelsius() + " C).",
                    event.occurredAt()
            );
        }

        String batterySeverity = batterySeverity(event.batteryPercent());
        if (batterySeverity != null) {
            return new AlarmCreateRequest(
                    event.deviceId(),
                    "CONNECTION",
                    batterySeverity,
                    "Simulasyon: batarya " + batterySeverity.toLowerCase()
                            + " esigine dustu (" + event.batteryPercent() + "%).",
                    event.occurredAt()
            );
        }
        return null;
    }

    private String temperatureSeverity(double temperatureCelsius) {
        if (temperatureCelsius >= HIGH_TEMPERATURE_CELSIUS) {
            return "HIGH";
        }
        if (temperatureCelsius >= MEDIUM_TEMPERATURE_CELSIUS) {
            return "MEDIUM";
        }
        if (temperatureCelsius >= LOW_TEMPERATURE_CELSIUS) {
            return "LOW";
        }
        return null;
    }

    private String batterySeverity(double batteryPercent) {
        if (batteryPercent <= HIGH_BATTERY_PERCENT) {
            return "HIGH";
        }
        if (batteryPercent <= MEDIUM_BATTERY_PERCENT) {
            return "MEDIUM";
        }
        if (batteryPercent <= LOW_BATTERY_PERCENT) {
            return "LOW";
        }
        return null;
    }
}
