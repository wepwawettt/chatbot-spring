package org.example.service;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import org.example.dto.UserCreateRequest;
import org.example.dto.UserResponse;
import org.example.entity.Device;
import org.example.entity.User;
import org.example.repository.DeviceRepository;
import org.example.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    private static final String DEFAULT_ROLE = "USER";
    private static final List<String> ALLOWED_ROLES = List.of("USER", "ADMIN");

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       DeviceRepository deviceRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        validateUniqueUser(request);

        OffsetDateTime now = OffsetDateTime.now();

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(normalizeRole(request.getRole()));
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

    @Transactional(readOnly = true)
    public UserResponse getVisibleUserById(String currentUsername, boolean admin, Long id) {
        User user = findUserOrThrow(id);
        if (!admin && !user.getUsername().equals(currentUsername)) {
            throw new AccessDeniedException("You can only access your own user");
        }
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public boolean canAccessUser(String currentUsername, boolean admin, Long userId) {
        if (admin) {
            return true;
        }
        User user = findUserOrThrow(userId);
        return user.getUsername().equals(currentUsername);
    }

    @Transactional(readOnly = true)
    public Optional<UserResponse> findUserByUsername(String username) {
        if (isBlank(username)) {
            return Optional.empty();
        }
        return userRepository.findByUsername(username.trim())
                .map(UserResponse::from);
    }

    @Transactional
    public void deleteUser(Long id, String currentUsername) {
        User user = findUserOrThrow(id);
        if (user.getUsername().equals(currentUsername)) {
            throw new IllegalArgumentException("You cannot delete your own user");
        }
        if ("ADMIN".equals(user.getRole())) {
            throw new IllegalArgumentException("Admin users cannot be deleted");
        }

        userRepository.delete(user);
    }

    @Transactional
    public void assignDevice(Long userId, Long deviceId) {
        User user = findUserOrThrow(userId);
        Device device = findDeviceOrThrow(deviceId);
        if (hasDevice(user, deviceId)) {
            return;
        }

        user.getDevices().add(device);
    }

    @Transactional
    public void removeDevice(Long userId, Long deviceId) {
        User user = findUserOrThrow(userId);
        boolean removed = user.getDevices().removeIf(device -> device.getId().equals(deviceId));
        if (!removed) {
            throw new EntityNotFoundException("User device relation not found");
        }
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
    }

    private Device findDeviceOrThrow(Long id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Device not found: " + id));
    }

    private boolean hasDevice(User user, Long deviceId) {
        return user.getDevices()
                .stream()
                .anyMatch(device -> device.getId().equals(deviceId));
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

    private String normalizeRole(String role) {
        String normalizedRole = isBlank(role) ? DEFAULT_ROLE : role.trim().toUpperCase();
        if (!ALLOWED_ROLES.contains(normalizedRole)) {
            throw new IllegalArgumentException("Role must be USER or ADMIN");
        }
        return normalizedRole;
    }
}
