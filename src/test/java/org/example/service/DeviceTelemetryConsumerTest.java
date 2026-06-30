package org.example.service;

import org.example.dto.AlarmCreateRequest;
import org.example.dto.AlarmResponse;
import org.example.event.DeviceTelemetryEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeviceTelemetryConsumerTest {
    @Test
    void createsMediumTemperatureAlarm() {
        AlarmService alarmService = alarmService();
        DeviceTelemetryConsumer consumer = new DeviceTelemetryConsumer(alarmService);

        consumer.handleTelemetry(event(79.0, 75.0, true));

        AlarmCreateRequest request = capturedRequest(alarmService);
        assertThat(request.getAlarmType()).isEqualTo("TEMPERATURE");
        assertThat(request.getSeverity()).isEqualTo("MEDIUM");
    }

    @Test
    void createsLowBatteryAlarm() {
        AlarmService alarmService = alarmService();
        DeviceTelemetryConsumer consumer = new DeviceTelemetryConsumer(alarmService);

        consumer.handleTelemetry(event(45.0, 25.0, true));

        AlarmCreateRequest request = capturedRequest(alarmService);
        assertThat(request.getAlarmType()).isEqualTo("CONNECTION");
        assertThat(request.getSeverity()).isEqualTo("LOW");
    }

    @Test
    void ignoresNormalTelemetry() {
        AlarmService alarmService = mock(AlarmService.class);
        DeviceTelemetryConsumer consumer = new DeviceTelemetryConsumer(alarmService);

        consumer.handleTelemetry(event(45.0, 75.0, true));

        verifyNoInteractions(alarmService);
    }

    private AlarmService alarmService() {
        AlarmService alarmService = mock(AlarmService.class);
        when(alarmService.createAlarm(any())).thenAnswer(invocation -> {
            AlarmCreateRequest request = invocation.getArgument(0);
            return new AlarmResponse(
                    1L,
                    request.getDeviceId(),
                    "Demo Device",
                    request.getAlarmType(),
                    request.getSeverity(),
                    request.getDescription(),
                    request.getOccurredAt(),
                    null
            );
        });
        return alarmService;
    }

    private AlarmCreateRequest capturedRequest(AlarmService alarmService) {
        ArgumentCaptor<AlarmCreateRequest> captor = ArgumentCaptor.forClass(AlarmCreateRequest.class);
        verify(alarmService).createAlarm(captor.capture());
        return captor.getValue();
    }

    private DeviceTelemetryEvent event(double temperature, double battery, boolean online) {
        return new DeviceTelemetryEvent(
                "event-1",
                10L,
                "UPS",
                temperature,
                battery,
                online,
                OffsetDateTime.now()
        );
    }
}
