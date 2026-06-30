package org.example.dto;

import org.example.entity.Conversation;

import java.time.OffsetDateTime;

public record ConversationSummaryResponse(
        String id,
        String title,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ConversationSummaryResponse from(Conversation conversation) {
        return new ConversationSummaryResponse(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }
}
