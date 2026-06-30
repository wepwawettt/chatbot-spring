package org.example.service;

import org.example.dto.DeviceResponse;
import org.example.event.DeviceTelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.simulator.enabled", havingValue = "true")
public class DeviceTelemetrySimulator {
    private static final Logger log = LoggerFactory.getLogger(DeviceTelemetrySimulator.class);

    private final DeviceService deviceService;
    private final DomainEventPublisher eventPublisher;
    private final Random random = new Random();

    public DeviceTelemetrySimulator(DeviceService deviceService, DomainEventPublisher eventPublisher) {
        this.deviceService = deviceService;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${app.simulator.fixed-delay-ms:60000}")
    public void publishTelemetry() {
        List<DeviceResponse> devices = deviceService.getAllDevices();
        for (DeviceResponse device : devices) {
            DeviceTelemetryEvent event = new DeviceTelemetryEvent(
                    UUID.randomUUID().toString(),
                    device.getId(),
                    device.getName(),
                    nextTemperature(),
                    nextBattery(),
                    random.nextDouble() > 0.04,
                    OffsetDateTime.now()
            );
            eventPublisher.publishTelemetry(event);
        }
        if (!devices.isEmpty()) {
            log.info("Device telemetry simulation published. deviceCount={}", devices.size());
        }
    }

    private double nextTemperature() {
        return Math.round((25 + random.nextDouble() * 65) * 10.0) / 10.0;
    }

    private double nextBattery() {
        return Math.round((5 + random.nextDouble() * 95) * 10.0) / 10.0;
    }
}
