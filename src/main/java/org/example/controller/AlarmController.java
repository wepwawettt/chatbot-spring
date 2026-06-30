package org.example.controller;

import jakarta.validation.Valid;
import org.example.dto.AlarmCreateRequest;
import org.example.dto.AlarmResponse;
import org.example.service.AlarmService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AlarmController {
    private final AlarmService alarmService;

    public AlarmController(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    @PostMapping("/api/alarms")
    @ResponseStatus(HttpStatus.CREATED)
    public AlarmResponse createAlarm(@Valid @RequestBody AlarmCreateRequest request, Authentication authentication) {
        return alarmService.createAlarm(authentication.getName(), isAdmin(authentication), request);
    }

    @GetMapping("/api/alarms/{id}")
    public AlarmResponse getAlarmById(@PathVariable Long id, Authentication authentication) {
        return alarmService.getVisibleAlarmById(authentication.getName(), isAdmin(authentication), id);
    }

    @GetMapping("/api/devices/{deviceId}/alarms")
    public List<AlarmResponse> getAlarmsByDeviceId(@PathVariable Long deviceId, Authentication authentication) {
        return alarmService.getVisibleAlarmsByDeviceId(authentication.getName(), isAdmin(authentication), deviceId);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
