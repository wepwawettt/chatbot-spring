package org.example.dto;

import jakarta.validation.constraints.NotBlank;

public class DeviceUpdateRequest {
    @NotBlank
    private String name;

    @NotBlank
    private String deviceType;

    @NotBlank
    private String status;

    private String location;

    public DeviceUpdateRequest() {
    }

    public DeviceUpdateRequest(String name, String deviceType, String status, String location) {
        this.name = name;
        this.deviceType = deviceType;
        this.status = status;
        this.location = location;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
