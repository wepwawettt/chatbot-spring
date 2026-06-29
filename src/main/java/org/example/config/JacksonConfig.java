package org.example.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {
    // Spring container icinde ortak kullanilacak JSON mapper bean'ini olusturur.
    // GeminiClient gibi servisler JSON request/response cevirmek icin bunu kullanir.
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        // OffsetDateTime, LocalDateTime gibi Java time tiplerinin JSON'a dogru cevrilmesini saglar.
        objectMapper.registerModule(new JavaTimeModule());

        return objectMapper;
    }
}
