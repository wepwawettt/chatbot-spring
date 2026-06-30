package org.example.event;

import java.time.OffsetDateTime;

public record AlarmCreatedEvent(
        String eventId,
        Long alarmId,
        Long deviceId,
        String deviceName,
        String alarmType,
        String severity,
        String description,
        OffsetDateTime occurredAt
) {
}
