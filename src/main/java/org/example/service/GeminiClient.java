package org.example.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.GeminiProperties;
import org.example.dto.AiActionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class GeminiClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 500;

    private static final String SYSTEM_PROMPT = """
            You are an intent extraction engine for a Spring Boot backend.
            The user speaks Turkish.
            Do not generate SQL.
            Do not explain.
            Do not use markdown.
            Return only valid JSON.
            Allowed actions: DEVICE_QUERY, ALARM_QUERY, DEVICE_COMMAND, ALARM_COMMAND, CALCULATION, GENERAL_QUESTION, UNKNOWN.
            Allowed operations: LIST, COUNT, DETAIL, GROUP_BY, TOP_N, AVERAGE, SUM, MIN, MAX, COMPARE, UPDATE_STATUS, CREATE, RESOLVE, UNKNOWN.
            Extract filters if present.
            Return DEVICE_QUERY or ALARM_QUERY only when the user clearly asks for backend data.
            Queries read data only. Examples:
            - "aktif cihazlari getir" => DEVICE_QUERY + LIST, filter status ACTIVE.
            - "id si 7 olan kullanicinin cihazlarini goruntule" => DEVICE_QUERY + LIST, target "user_devices", entityId 7.
            - "buragin cihazlarini getir" => DEVICE_QUERY + LIST, target "user_devices", entityName "burak".
            - "son 10 alarmi listele" => ALARM_QUERY + LIST, limit 10.
            - "hangi cihazlarda alarm var" => ALARM_QUERY + GROUP_BY, groupBy "device".
            Commands request a write operation. Examples:
            - "Depo Kamerasi cihazini pasif yap" => DEVICE_COMMAND + UPDATE_STATUS, entityName "Depo Kamerasi", status "PASSIVE".
            - "4 numarali cihazi aktif yap" => DEVICE_COMMAND + UPDATE_STATUS, entityId 4, status "ACTIVE".
            - "Bahce Kamerasi adli CAMERA cihaz ekle" => DEVICE_COMMAND + CREATE, entityName "Bahce Kamerasi", deviceType "CAMERA".
            - "371 numarali alarmi cozuldu yap" => ALARM_COMMAND + RESOLVE, entityId 371.
            - "Depo Kamerasi icin sicaklik alarmi olustur" => ALARM_COMMAND + CREATE, entityName "Depo Kamerasi", alarmType "TEMPERATURE".
            Never execute commands. Only describe the intended command in JSON; the backend will ask for confirmation.
            Greetings, help questions, and definition questions must be GENERAL_QUESTION with operation UNKNOWN.
            Bare domain keywords like "alarm" or "cihaz" are not list requests; return GENERAL_QUESTION.
            Questions like "hangi cihazlarda alarm var" must be ALARM_QUERY with operation GROUP_BY and groupBy "device".
            If the request is unclear, unsafe, or unrelated to the backend domain, return UNKNOWN.
            Never include database credentials.
            Never include SQL.
            The JSON must match this schema:
            {
              "action": "...",
              "operation": "...",
              "target": "devices | user_devices | alarms | alarm_count | ...",
              "groupBy": "...",
              "metric": "...",
              "filters": {
                "status": "...",
                "deviceType": "...",
                "nameContains": "...",
                "dateRange": "...",
                "dateRanges": ["..."],
                "startDate": "...",
                "endDate": "..."
              },
              "limit": 20,
              "entityId": 123,
              "entityName": "...",
              "status": "...",
              "deviceType": "...",
              "alarmType": "...",
              "severity": "...",
              "description": "..."
            }
            """;

    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GeminiClient(GeminiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                .build();
    }

    public Optional<AiActionResponse> extractAction(String userMessage) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("GEMINI_API_KEY is not configured.");
            return Optional.empty();
        }

        log.info("Gemini request starting. model={}, baseUrl={}, messagePreview=\"{}\"",
                properties.getModel(), trimTrailingSlash(properties.getBaseUrl()), preview(userMessage));

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String responseBody = generateContent(userMessage);
                log.info("Gemini response received. model={}, attempt={}, responseChars={}",
                        properties.getModel(), attempt, responseBody == null ? 0 : responseBody.length());

                String text = extractText(responseBody);
                if (text == null || text.isBlank()) {
                    log.info("Gemini response text is empty. Returning UNKNOWN.");
                    return Optional.of(AiActionResponse.unknown());
                }

                AiActionResponse action = parseAction(text);
                log.info("Gemini action parsed. action={}, operation={}, target={}, entityId={}, entityName={}, limit={}",
                        action.action(), action.operation(), action.target(), action.entityId(), action.entityName(), action.limit());
                return Optional.of(action);
            } catch (HttpStatusCodeException exception) {
                if (exception.getStatusCode().value() == 503 && attempt < MAX_ATTEMPTS) {
                    log.warn("Gemini API temporarily unavailable for model {}. Retrying {}/{}.",
                            properties.getModel(), attempt + 1, MAX_ATTEMPTS);
                    if (!sleepBeforeRetry(attempt)) {
                        return Optional.empty();
                    }
                    continue;
                }
                log.warn("Gemini API call failed with status {}: {}", exception.getStatusCode(), exception.getResponseBodyAsString());
                return Optional.empty();
            } catch (RuntimeException exception) {
                log.warn("Gemini API call failed.", exception);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private String preview(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 120) + "...";
    }

    private String generateContent(String userMessage) {
        return restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1beta/models/{model}:generateContent")
                        .queryParam("key", properties.getApiKey())
                        .build(properties.getModel()))
                .body(buildRequestBody(userMessage))
                .retrieve()
                .body(String.class);
    }

    private boolean sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(RETRY_DELAY_MS * attempt);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Map<String, Object> buildRequestBody(String userMessage) {
        return Map.of(
                "systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", SYSTEM_PROMPT))
                ),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", userMessage))
                )),
                "generationConfig", Map.of(
                        "temperature", 0,
                        "responseMimeType", "application/json"
                )
        );
    }

    private String extractText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
            StringBuilder text = new StringBuilder();
            if (parts.isArray()) {
                parts.forEach(part -> text.append(part.path("text").asText()));
            }
            return text.toString();
        } catch (Exception exception) {
            log.warn("Gemini response could not be read.", exception);
            return null;
        }
    }

    private AiActionResponse parseAction(String text) {
        try {
            return objectMapper.readerFor(AiActionResponse.class)
                    .with(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                    .readValue(extractJsonObject(text));
        } catch (Exception exception) {
            log.warn("Gemini action JSON could not be parsed. Raw response: {}", text);
            return AiActionResponse.unknown();
        }
    }

    private String extractJsonObject(String text) {
        String trimmed = stripMarkdownFence(text.trim());
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String stripMarkdownFence(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }

        String withoutOpeningFence = trimmed.replaceFirst("(?is)^```(?:json)?\\s*", "");
        return withoutOpeningFence.replaceFirst("(?is)\\s*```$", "").trim();
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://generativelanguage.googleapis.com";
        }
        return value.replaceAll("/+$", "");
    }
}
