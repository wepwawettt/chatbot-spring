package org.example.repository;

import org.example.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, String> {
    Optional<Conversation> findByIdAndUsername(String id, String username);

    List<Conversation> findByUsernameOrderByUpdatedAtDesc(String username);
}
