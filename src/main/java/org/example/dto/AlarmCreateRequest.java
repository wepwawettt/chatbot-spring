package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public class AlarmCreateRequest {
    @NotNull
    private Long deviceId;

    @NotBlank
    private String alarmType;

    private String severity;

    @NotBlank
    private String description;

    private OffsetDateTime occurredAt;

    public AlarmCreateRequest() {
    }

    public AlarmCreateRequest(Long deviceId, String alarmType, String severity, String description,
                              OffsetDateTime occurredAt) {
        this.deviceId = deviceId;
        this.alarmType = alarmType;
        this.severity = severity;
        this.description = description;
        this.occurredAt = occurredAt;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public String getAlarmType() {
        return alarmType;
    }

    public void setAlarmType(String alarmType) {
        this.alarmType = alarmType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }
}
