package org.example.dto;

import org.example.entity.Alarm;

import java.time.OffsetDateTime;

public class AlarmResponse {
    private Long id;
    private Long deviceId;
    private String deviceName;
    private String alarmType;
    private String severity;
    private String description;
    private OffsetDateTime occurredAt;
    private OffsetDateTime resolvedAt;

    public AlarmResponse() {
    }

    public AlarmResponse(Long id, Long deviceId, String deviceName, String alarmType, String severity,
                         String description, OffsetDateTime occurredAt, OffsetDateTime resolvedAt) {
        this.id = id;
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.alarmType = alarmType;
        this.severity = severity;
        this.description = description;
        this.occurredAt = occurredAt;
        this.resolvedAt = resolvedAt;
    }

    public static AlarmResponse from(Alarm alarm, String deviceName) {
        return new AlarmResponse(
                alarm.getId(),
                alarm.getDeviceId(),
                deviceName,
                alarm.getAlarmType(),
                alarm.getSeverity(),
                alarm.getDescription(),
                alarm.getOccurredAt(),
                alarm.getResolvedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
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

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(OffsetDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
