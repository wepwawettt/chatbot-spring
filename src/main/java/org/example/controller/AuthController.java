package org.example.controller;

import org.example.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Giris yapan kullanicinin temel bilgilerini dondurur.
    // Authentication nesnesi Spring Security tarafindan otomatik doldurulur.
    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        // authentication.getName(), Basic Auth ile dogrulanan username bilgisidir.
        return userRepository.findByUsername(authentication.getName())
                // Kullanici veritabaninda bulunursa frontend'in ihtiyaci olan id, username ve role doner.
                .map(user -> Map.<String, Object>of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "role", user.getRole()
                ))
                // Normalde buraya dusmez; yine de kullanici bulunamazsa guvenli bir fallback cevap doner.
                .orElseGet(() -> Map.of(
                        "username", authentication.getName(),
                        "role", "UNKNOWN"
                ));
    }
}
