package org.example.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.dto.DeviceCreateRequest;
import org.example.dto.DeviceResponse;
import org.example.dto.DeviceUpdateRequest;
import org.example.entity.Device;
import org.example.repository.DeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class DeviceService {
    private static final String DEFAULT_STATUS = "ACTIVE";

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Transactional
    public DeviceResponse createDevice(DeviceCreateRequest request) {
        OffsetDateTime now = OffsetDateTime.now();

        Device device = new Device();
        device.setName(request.getName());
        device.setDeviceType(request.getDeviceType());
        device.setStatus(DEFAULT_STATUS);
        device.setLocation(request.getLocation());
        device.setCreatedAt(now);
        device.setUpdatedAt(now);

        return DeviceResponse.from(deviceRepository.save(device));
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> getAllDevices() {
        return deviceRepository.findAll()
                .stream()
                .map(DeviceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> getDevicesByUserId(Long userId) {
        return deviceRepository.findByUserId(userId)
                .stream()
                .map(DeviceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DeviceResponse getDeviceById(Long id) {
        return DeviceResponse.from(findDeviceOrThrow(id));
    }

    @Transactional
    public DeviceResponse updateDevice(Long id, DeviceUpdateRequest request) {
        Device device = findDeviceOrThrow(id);
        device.setName(request.getName());
        device.setDeviceType(request.getDeviceType());
        device.setStatus(request.getStatus());
        device.setLocation(request.getLocation());
        device.setUpdatedAt(OffsetDateTime.now());

        return DeviceResponse.from(deviceRepository.save(device));
    }

    @Transactional
    public void deleteDevice(Long id) {
        if (!deviceRepository.existsById(id)) {
            throw new EntityNotFoundException("Device not found: " + id);
        }
        deviceRepository.deleteById(id);
    }

    private Device findDeviceOrThrow(Long id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Device not found: " + id));
    }
}
