package org.example.controller;

import jakarta.validation.Valid;
import org.example.dto.DeviceCreateRequest;
import org.example.dto.DeviceResponse;
import org.example.dto.DeviceUpdateRequest;
import org.example.service.DeviceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceResponse createDevice(@Valid @RequestBody DeviceCreateRequest request) {
        return deviceService.createDevice(request);
    }

    @GetMapping
    public List<DeviceResponse> getAllDevices(Authentication authentication) {
        return deviceService.getVisibleDevices(authentication.getName(), isAdmin(authentication));
    }

    @GetMapping("/{id}")
    public DeviceResponse getDeviceById(@PathVariable Long id, Authentication authentication) {
        return deviceService.getVisibleDeviceById(authentication.getName(), isAdmin(authentication), id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Device not found: " + id));
    }

    @PutMapping("/{id}")
    public DeviceResponse updateDevice(@PathVariable Long id, @Valid @RequestBody DeviceUpdateRequest request) {
        return deviceService.updateDevice(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDevice(@PathVariable Long id) {
        deviceService.deleteDevice(id);
        return ResponseEntity.noContent().build();
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
