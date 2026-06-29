package org.example.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    // Swagger/OpenAPI dokumani icin uygulama bilgilerini tanimlar.
    // Bu bilgiler /swagger-ui.html ekraninda baslik, versiyon ve aciklama olarak gorunur.
    @Bean
    public OpenAPI deviceAlarmOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        // API dokumaninda gorunecek ana baslik.
                        .title("Device Alarm API")
                        // API dokumaninda gorunecek versiyon bilgisi.
                        .version("1.0")
                        // Projenin Swagger ekraninda gorunecek kisa aciklamasi.
                        .description("Device, user, alarm and chat API"));
    }
}
