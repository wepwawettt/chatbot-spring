package org.example.dto;

import jakarta.validation.constraints.NotBlank;

public class DeviceCreateRequest {
    @NotBlank
    private String name;

    @NotBlank
    private String deviceType;

    private String location;

    public DeviceCreateRequest() {
    }

    public DeviceCreateRequest(String name, String deviceType, String location) {
        this.name = name;
        this.deviceType = deviceType;
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

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
