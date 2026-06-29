package org.example.controller;

import jakarta.validation.Valid;
import org.example.dto.DeviceResponse;
import org.example.dto.UserCreateRequest;
import org.example.dto.UserDeviceAssignRequest;
import org.example.dto.UserResponse;
import org.example.service.DeviceService;
import org.example.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final DeviceService deviceService;

    public UserController(UserService userService, DeviceService deviceService) {
        this.userService = userService;
        this.deviceService = deviceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody UserCreateRequest request) {
        return userService.createUser(request);
    }

    @GetMapping
    public List<UserResponse> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    public UserResponse getUserById(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, Authentication authentication) {
        userService.deleteUser(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignDevice(@PathVariable Long userId, @Valid @RequestBody UserDeviceAssignRequest request) {
        userService.assignDevice(userId, request.getDeviceId());
    }

    @GetMapping("/{userId}/devices")
    public List<DeviceResponse> getUserDevices(@PathVariable Long userId) {
        userService.getUserById(userId);
        return deviceService.getDevicesByUserId(userId);
    }

    @DeleteMapping("/{userId}/devices/{deviceId}")
    public ResponseEntity<Void> removeDevice(@PathVariable Long userId, @PathVariable Long deviceId) {
        userService.removeDevice(userId, deviceId);
        return ResponseEntity.noContent().build();
    }
}
