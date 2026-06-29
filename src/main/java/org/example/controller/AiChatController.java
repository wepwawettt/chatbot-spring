package org.example.controller;

import jakarta.validation.Valid;
import org.example.dto.ChatRequest;
import org.example.dto.ChatResponse;
import org.example.service.AiAgentService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiChatController {
    private final AiAgentService aiAgentService;

    public AiChatController(AiAgentService aiAgentService) {
        this.aiAgentService = aiAgentService;
    }

    @PostMapping("/api/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request, Authentication authentication) {
        return aiAgentService.answer(authentication.getName(), isAdmin(authentication), request.conversationId(), request.message());
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
