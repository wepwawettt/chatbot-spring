package org.example.event;

import java.time.OffsetDateTime;

public record DeviceTelemetryEvent(
        String eventId,
        Long deviceId,
        String deviceName,
        double temperatureCelsius,
        double batteryPercent,
        boolean online,
        OffsetDateTime occurredAt
) {
}
