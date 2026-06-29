package org.example.config;

import org.example.entity.User;
import org.example.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;

@Configuration
public class DefaultAdminInitializer {
    @Bean
    public ApplicationRunner createDefaultAdmin(UserRepository userRepository,
                                                PasswordEncoder passwordEncoder,
                                                @Value("${app.security.default-admin.username}") String username,
                                                @Value("${app.security.default-admin.email}") String email,
                                                @Value("${app.security.default-admin.password}") String password) {
        return args -> {
            if (userRepository.findByUsername(username).isPresent()) {
                return;
            }

            OffsetDateTime now = OffsetDateTime.now();

            User admin = new User();
            admin.setUsername(username);
            admin.setEmail(email);
            admin.setPasswordHash(passwordEncoder.encode(password));
            admin.setRole("ADMIN");
            admin.setEnabled(true);
            admin.setCreatedAt(now);
            admin.setUpdatedAt(now);

            userRepository.save(admin);
        };
    }
}
