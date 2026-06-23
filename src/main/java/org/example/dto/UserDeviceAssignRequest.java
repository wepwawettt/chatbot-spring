package org.example.dto;

import jakarta.validation.constraints.NotNull;

public class UserDeviceAssignRequest {
    @NotNull
    private Long deviceId;

    public UserDeviceAssignRequest() {
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }
}
