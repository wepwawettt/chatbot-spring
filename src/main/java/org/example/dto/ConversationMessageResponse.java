package org.example.dto;

import org.example.entity.ConversationMessage;

import java.time.OffsetDateTime;

public record ConversationMessageResponse(
        Long id,
        String role,
        String content,
        OffsetDateTime createdAt
) {
    public static ConversationMessageResponse from(ConversationMessage message) {
        return new ConversationMessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
