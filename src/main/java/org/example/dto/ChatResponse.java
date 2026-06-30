package org.example.dto;

public record ChatResponse(
        String answer,
        boolean success,
        String conversationId
) {
    public ChatResponse(String answer, boolean success) {
        this(answer, success, null);
    }

    public ChatResponse withConversationId(String conversationId) {
        return new ChatResponse(answer, success, conversationId);
    }
}
