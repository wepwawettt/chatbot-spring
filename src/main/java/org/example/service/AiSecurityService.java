package org.example.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AiSecurityService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECRET_ASSIGNMENT_PATTERN = Pattern.compile(
            "\\b(password|sifre|şifre|secret|token|api[_ -]?key)\\s*[:=]\\s*\\S+",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern API_KEY_PATTERN = Pattern.compile("\\b(AIza[0-9A-Za-z_-]{20,}|sk-[0-9A-Za-z_-]{20,}|AQ\\.[0-9A-Za-z._-]{20,})\\b");
    private static final Pattern JDBC_URL_PATTERN = Pattern.compile("\\bjdbc:[^\\s]+", Pattern.CASE_INSENSITIVE);

    private static final List<UnsafeRule> UNSAFE_RULES = List.of(
            new UnsafeRule("SQL execution or data exfiltration request",
                    Pattern.compile("\\b(drop|truncate|delete\\s+from|insert\\s+into|update\\s+\\w+\\s+set|select\\s+.+\\s+from|union\\s+select)\\b")),
            new UnsafeRule("Credential disclosure request",
                    Pattern.compile("(veritabani|database|db|api|token|secret|sifre|password).*(goster|ver|listele|getir|paylas|oku)")),
            new UnsafeRule("Prompt injection attempt",
                    Pattern.compile("(prompt|system prompt|talimat|instruction).*(ignore|yok say|bypass|atla|unut)")),
            new UnsafeRule("Broad sensitive user-data request",
                    Pattern.compile("(tum|butun|her).*(kullanici|user).*(email|sifre|password|token|secret|credential)"))
    );

    public AiSecurityResult inspect(String message) {
        String sanitizedMessage = sanitize(message);
        String normalized = normalizeForSecurity(message);

        return UNSAFE_RULES.stream()
                .filter(rule -> rule.pattern().matcher(normalized).find())
                .findFirst()
                .map(rule -> new AiSecurityResult(false, sanitizedMessage, rule.reason()))
                .orElseGet(() -> new AiSecurityResult(true, sanitizedMessage, null));
    }

    public String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String sanitized = EMAIL_PATTERN.matcher(message).replaceAll("[EMAIL]");
        sanitized = SECRET_ASSIGNMENT_PATTERN.matcher(sanitized).replaceAll("$1=[MASKED_SECRET]");
        sanitized = API_KEY_PATTERN.matcher(sanitized).replaceAll("[API_KEY]");
        sanitized = JDBC_URL_PATTERN.matcher(sanitized).replaceAll("[JDBC_URL]");
        return sanitized;
    }

    private String normalizeForSecurity(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String lower = message.toLowerCase(Locale.forLanguageTag("tr-TR")).replace('\u0131', 'i');
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record UnsafeRule(String reason, Pattern pattern) {
    }

    public record AiSecurityResult(
            boolean allowed,
            String sanitizedMessage,
            String reason
    ) {
    }
}
