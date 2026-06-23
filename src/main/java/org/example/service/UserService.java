package org.example.service;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import org.example.dto.UserCreateRequest;
import org.example.dto.UserResponse;
import org.example.entity.User;
import org.example.entity.UserDevice;
import org.example.repository.DeviceRepository;
import org.example.repository.UserDeviceRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class UserService {
    private static final String DEFAULT_ROLE = "USER";

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final UserDeviceRepository userDeviceRepository;

    public UserService(UserRepository userRepository, DeviceRepository deviceRepository,
                       UserDeviceRepository userDeviceRepository) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.userDeviceRepository = userDeviceRepository;
    }

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        validateUniqueUser(request);

        OffsetDateTime now = OffsetDateTime.now();

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setRole(isBlank(request.getRole()) ? DEFAULT_ROLE : request.getRole());
        user.setEnabled(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        return UserResponse.from(findUserOrThrow(id));
    }

    @Transactional
    public void assignDevice(Long userId, Long deviceId) {
        findUserOrThrow(userId);
        if (!deviceRepository.existsById(deviceId)) {
            throw new EntityNotFoundException("Device not found: " + deviceId);
        }
        if (userDeviceRepository.existsByUserIdAndDeviceId(userId, deviceId)) {
            return;
        }

        UserDevice userDevice = new UserDevice();
        userDevice.setUserId(userId);
        userDevice.setDeviceId(deviceId);
        userDevice.setCreatedAt(OffsetDateTime.now());

        userDeviceRepository.save(userDevice);
    }

    @Transactional
    public void removeDevice(Long userId, Long deviceId) {
        findUserOrThrow(userId);
        if (!userDeviceRepository.existsByUserIdAndDeviceId(userId, deviceId)) {
            throw new EntityNotFoundException("User device relation not found");
        }

        userDeviceRepository.deleteByUserIdAndDeviceId(userId, deviceId);
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
    }

    private void validateUniqueUser(UserCreateRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new EntityExistsException("Username already exists: " + request.getUsername());
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new EntityExistsException("Email already exists: " + request.getEmail());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
