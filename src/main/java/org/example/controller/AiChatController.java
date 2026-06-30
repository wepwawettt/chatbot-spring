package org.example.controller;

import jakarta.validation.Valid;
import org.example.dto.ChatRequest;
import org.example.dto.ChatResponse;
import org.example.dto.ConversationMessageResponse;
import org.example.dto.ConversationSummaryResponse;
import org.example.service.AiAgentService;
import org.example.service.ConversationService;
import org.example.service.RateLimitService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
public class AiChatController {
    private final AiAgentService aiAgentService;
    private final ConversationService conversationService;
    private final RateLimitService rateLimitService;

    public AiChatController(AiAgentService aiAgentService,
                            ConversationService conversationService,
                            RateLimitService rateLimitService) {
        this.aiAgentService = aiAgentService;
        this.conversationService = conversationService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/api/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request, Authentication authentication) {
        RateLimitService.RateLimitResult rateLimit = rateLimitService.consumeChat(authentication.getName());
        if (!rateLimit.allowed()) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Chat rate limit exceeded. Retry after " + rateLimit.retryAfterSeconds() + " seconds."
            );
        }
        return aiAgentService.answer(authentication.getName(), isAdmin(authentication), request.conversationId(), request.message());
    }

    @GetMapping("/api/chat/conversations")
    public List<ConversationSummaryResponse> conversations(Authentication authentication) {
        return conversationService.listConversations(authentication.getName());
    }

    @GetMapping("/api/chat/conversations/{conversationId}/messages")
    public List<ConversationMessageResponse> messages(@PathVariable String conversationId, Authentication authentication) {
        return conversationService.listMessages(authentication.getName(), conversationId);
    }

    @DeleteMapping("/api/chat/conversations")
    public Map<String, Integer> deleteConversations(Authentication authentication) {
        return Map.of("deleted", conversationService.deleteUserConversations(authentication.getName()));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
