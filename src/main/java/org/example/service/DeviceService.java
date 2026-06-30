package org.example.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.dto.DeviceCreateRequest;
import org.example.dto.DeviceResponse;
import org.example.dto.DeviceUpdateRequest;
import org.example.entity.Device;
import org.example.repository.DeviceRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class DeviceService {
    private static final String DEFAULT_STATUS = "ACTIVE";
    private static final Set<String> DEVICE_STATUSES = Set.of("ACTIVE", "PASSIVE", "MAINTENANCE");

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Transactional
    @CacheEvict(cacheNames = {"devices", "userDevices", "deviceDetails"}, allEntries = true)
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
    @Cacheable(cacheNames = "devices", key = "'all'")
    public List<DeviceResponse> getAllDevices() {
        return deviceRepository.findAll()
                .stream()
                .map(DeviceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "devices", key = "'visible-all:' + #username + ':' + #admin")
    public List<DeviceResponse> getVisibleDevices(String username, boolean admin) {
        return deviceRepository.findVisibleDeviceScope(username, admin)
                .stream()
                .sorted(Comparator.comparing(Device::getId))
                .map(DeviceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "userDevices", key = "#userId")
    public List<DeviceResponse> getDevicesByUserId(Long userId) {
        return deviceRepository.findByUserId(userId)
                .stream()
                .sorted(Comparator.comparing(Device::getId))
                .map(DeviceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DeviceResponse getDeviceById(Long id) {
        return DeviceResponse.from(findDeviceOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Optional<DeviceResponse> getVisibleDeviceById(String username, boolean admin, Long id) {
        return getDeviceDetail(username, admin, String.valueOf(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "devices", key = "#username + ':' + #admin + ':' + #status + ':' + #deviceType + ':' + #nameContains + ':' + #limit")
    public List<DeviceResponse> listDevices(String username,
                                            boolean admin,
                                            String status,
                                            String deviceType,
                                            String nameContains,
                                            int limit) {
        return visibleDevices(username, admin, status, deviceType, nameContains)
                .stream()
                .limit(Math.max(0, limit))
                .map(DeviceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "devices", key = "'count:' + #username + ':' + #admin + ':' + #status + ':' + #deviceType + ':' + #nameContains")
    public long countDevices(String username, boolean admin, String status, String deviceType, String nameContains) {
        return visibleDevices(username, admin, status, deviceType, nameContains).size();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "deviceDetails", key = "#username + ':' + #admin + ':' + #identifier")
    public Optional<DeviceResponse> getDeviceDetail(String username, boolean admin, String identifier) {
        Long deviceId = parseLong(identifier);
        if (deviceId != null) {
            return deviceRepository.findVisibleDeviceScope(username, admin)
                    .stream()
                    .filter(device -> device.getId().equals(deviceId))
                    .findFirst()
                    .map(DeviceResponse::from);
        }

        return visibleDevices(username, admin, null, null, identifier)
                .stream()
                .findFirst()
                .map(DeviceResponse::from);
    }

    @Transactional
    @CacheEvict(cacheNames = {"devices", "userDevices", "deviceDetails"}, allEntries = true)
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
    @CacheEvict(cacheNames = {"devices", "userDevices", "deviceDetails"}, allEntries = true)
    public DeviceResponse updateDeviceStatus(Long id, String status) {
        String normalizedStatus = allow(normalize(status), DEVICE_STATUSES);
        if (normalizedStatus == null) {
            throw new IllegalArgumentException("Unsupported device status: " + status);
        }

        Device device = findDeviceOrThrow(id);
        device.setStatus(normalizedStatus);
        device.setUpdatedAt(OffsetDateTime.now());
        return DeviceResponse.from(deviceRepository.save(device));
    }

    @Transactional
    @CacheEvict(cacheNames = {"devices", "userDevices", "deviceDetails"}, allEntries = true)
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

    private List<Device> visibleDevices(String username,
                                        boolean admin,
                                        String status,
                                        String deviceType,
                                        String nameContains) {
        String normalizedStatus = allow(normalize(status), DEVICE_STATUSES);
        String normalizedDeviceType = normalize(deviceType);
        String normalizedName = normalize(nameContains);

        return deviceRepository.findVisibleDeviceScope(username, admin)
                .stream()
                .filter(device -> normalizedStatus == null || normalizedStatus.equals(normalize(device.getStatus())))
                .filter(device -> normalizedDeviceType == null || containsNormalized(device.getDeviceType(), normalizedDeviceType))
                .filter(device -> normalizedName == null || containsNormalized(device.getName(), normalizedName))
                .sorted(Comparator.comparing(Device::getId))
                .toList();
    }

    private String allow(String value, Set<String> allowedValues) {
        return value != null && allowedValues.contains(value) ? value : null;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.toUpperCase(Locale.ROOT).trim();
    }

    private boolean containsNormalized(String source, String needle) {
        String normalizedSource = normalize(source);
        return normalizedSource != null && normalizedSource.contains(needle);
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
