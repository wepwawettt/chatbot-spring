package org.example.repository;

import org.example.entity.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {
    List<ConversationMessage> findTop12ByConversation_IdAndConversation_UsernameOrderByCreatedAtDesc(
            String conversationId,
            String username
    );

    List<ConversationMessage> findByConversation_IdAndConversation_UsernameOrderByCreatedAtAsc(
            String conversationId,
            String username
    );
}
