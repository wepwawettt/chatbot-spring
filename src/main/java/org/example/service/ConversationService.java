package org.example.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.example.dto.ConversationMessageResponse;
import org.example.dto.ConversationSummaryResponse;
import org.example.entity.Conversation;
import org.example.entity.ConversationMessage;
import org.example.repository.ConversationMessageRepository;
import org.example.repository.ConversationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Service
public class ConversationService {
    private static final String ROLE_USER = "USER";
    private static final String ROLE_ASSISTANT = "ASSISTANT";
    private static final int CONTEXT_MESSAGE_LIMIT = 6;
    private static final int CONTEXT_CHARS_PER_MESSAGE = 240;
    private static final int CACHE_MESSAGE_LIMIT = 24;
    private static final Pattern SAFE_CONVERSATION_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final boolean persistenceEnabled;
    private final Cache<String, CachedConversation> conversationCache;
    private final AtomicLong cacheMessageIdSequence = new AtomicLong();

    public ConversationService(ConversationRepository conversationRepository,
                               ConversationMessageRepository messageRepository,
                               @Value("${app.conversation.persistence-enabled:false}") boolean persistenceEnabled,
                               @Value("${app.conversation.cache.maximum-size:1000}") long cacheMaximumSize,
                               @Value("${app.conversation.cache.expire-after-minutes:60}") long cacheExpireMinutes) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.persistenceEnabled = persistenceEnabled;
        this.conversationCache = Caffeine.newBuilder()
                .maximumSize(cacheMaximumSize)
                .expireAfterAccess(Duration.ofMinutes(cacheExpireMinutes))
                .build();
    }

    @Transactional
    public ConversationContext start(String username, String requestedConversationId, String userMessage) {
        if (!persistenceEnabled) {
            return startCached(username, requestedConversationId, userMessage);
        }
        return startPersistent(username, requestedConversationId, userMessage);
    }

    @Transactional
    public void saveAssistantMessage(String username, String conversationId, String answer) {
        if (!persistenceEnabled) {
            saveAssistantMessageCached(username, conversationId, answer);
            return;
        }
        saveAssistantMessagePersistent(username, conversationId, answer);
    }

    @Transactional(readOnly = true)
    public List<ConversationSummaryResponse> listConversations(String username) {
        if (!persistenceEnabled) {
            return conversationCache.asMap()
                    .values()
                    .stream()
                    .filter(conversation -> username.equals(conversation.username))
                    .map(this::cachedSummary)
                    .sorted(Comparator.comparing(ConversationSummaryResponse::updatedAt).reversed())
                    .toList();
        }

        return conversationRepository.findByUsernameOrderByUpdatedAtDesc(username)
                .stream()
                .map(ConversationSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ConversationMessageResponse> listMessages(String username, String conversationId) {
        if (!persistenceEnabled) {
            CachedConversation conversation = conversationCache.getIfPresent(cacheKey(username, conversationId));
            if (conversation == null) {
                return List.of();
            }
            synchronized (conversation) {
                return conversation.messages
                        .stream()
                        .map(message -> new ConversationMessageResponse(
                                message.id(),
                                message.role(),
                                message.content(),
                                message.createdAt()
                        ))
                        .toList();
            }
        }

        return messageRepository.findByConversation_IdAndConversation_UsernameOrderByCreatedAtAsc(conversationId, username)
                .stream()
                .map(ConversationMessageResponse::from)
                .toList();
    }

    @Transactional
    public int deleteUserConversations(String username) {
        List<Conversation> conversations = conversationRepository.findByUsernameOrderByUpdatedAtDesc(username);
        conversationRepository.deleteAll(conversations);
        return conversations.size() + deleteCachedUserConversations(username);
    }

    private ConversationContext startPersistent(String username, String requestedConversationId, String userMessage) {
        Conversation conversation = resolveConversation(username, requestedConversationId, userMessage);
        List<MessageSnapshot> previousMessages = recentMessages(conversation.getId(), username);

        saveMessage(conversation, ROLE_USER, userMessage);
        conversation.setUpdatedAt(OffsetDateTime.now());
        conversationRepository.save(conversation);

        String previousUserContext = previousUserContext(previousMessages);
        return new ConversationContext(
                conversation.getId(),
                contextualAiMessage(previousUserContext, userMessage),
                backendContextMessage(previousUserContext, userMessage),
                lastAssistantListMessage(previousMessages)
        );
    }

    private ConversationContext startCached(String username, String requestedConversationId, String userMessage) {
        CachedConversation conversation = resolveCachedConversation(username, requestedConversationId, userMessage);
        synchronized (conversation) {
            List<MessageSnapshot> previousMessages = recentCachedMessages(conversation);
            saveCachedMessage(conversation, ROLE_USER, userMessage);
            conversation.updatedAt = OffsetDateTime.now();

            String previousUserContext = previousUserContext(previousMessages);
            return new ConversationContext(
                    conversation.id,
                    contextualAiMessage(previousUserContext, userMessage),
                    backendContextMessage(previousUserContext, userMessage),
                    lastAssistantListMessage(previousMessages)
            );
        }
    }

    private void saveAssistantMessagePersistent(String username, String conversationId, String answer) {
        conversationRepository.findByIdAndUsername(conversationId, username)
                .ifPresent(conversation -> {
                    saveMessage(conversation, ROLE_ASSISTANT, answer);
                    conversation.setUpdatedAt(OffsetDateTime.now());
                    conversationRepository.save(conversation);
                });
    }

    private void saveAssistantMessageCached(String username, String conversationId, String answer) {
        CachedConversation conversation = conversationCache.getIfPresent(cacheKey(username, conversationId));
        if (conversation == null) {
            return;
        }
        synchronized (conversation) {
            saveCachedMessage(conversation, ROLE_ASSISTANT, answer);
            conversation.updatedAt = OffsetDateTime.now();
        }
    }

    private Conversation resolveConversation(String username, String requestedConversationId, String userMessage) {
        String safeConversationId = safeConversationId(requestedConversationId);
        if (safeConversationId != null) {
            return conversationRepository.findByIdAndUsername(safeConversationId, username)
                    .orElseGet(() -> conversationRepository.existsById(safeConversationId)
                            ? createConversation(username, null, userMessage)
                            : createConversation(username, safeConversationId, userMessage));
        }
        return createConversation(username, null, userMessage);
    }

    private Conversation createConversation(String username, String requestedId, String userMessage) {
        OffsetDateTime now = OffsetDateTime.now();
        Conversation conversation = new Conversation();
        conversation.setId(requestedId == null ? UUID.randomUUID().toString() : requestedId);
        conversation.setUsername(username);
        conversation.setTitle(preview(userMessage, 80));
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        return conversationRepository.save(conversation);
    }

    private CachedConversation resolveCachedConversation(String username, String requestedConversationId, String userMessage) {
        String safeConversationId = safeConversationId(requestedConversationId);
        String conversationId = safeConversationId == null ? UUID.randomUUID().toString() : safeConversationId;
        return conversationCache.get(cacheKey(username, conversationId), ignored -> {
            OffsetDateTime now = OffsetDateTime.now();
            return new CachedConversation(
                    conversationId,
                    username,
                    preview(userMessage, 80),
                    now,
                    now,
                    new ArrayList<>()
            );
        });
    }

    private List<MessageSnapshot> recentMessages(String conversationId, String username) {
        List<ConversationMessage> messages = new ArrayList<>(
                messageRepository.findTop12ByConversation_IdAndConversation_UsernameOrderByCreatedAtDesc(conversationId, username)
        );
        Collections.reverse(messages);
        return messages.stream()
                .map(message -> new MessageSnapshot(message.getRole(), message.getContent()))
                .toList();
    }

    private List<MessageSnapshot> recentCachedMessages(CachedConversation conversation) {
        int fromIndex = Math.max(0, conversation.messages.size() - 12);
        return conversation.messages
                .subList(fromIndex, conversation.messages.size())
                .stream()
                .map(message -> new MessageSnapshot(message.role(), message.content()))
                .toList();
    }

    private void saveMessage(Conversation conversation, String role, String content) {
        ConversationMessage message = new ConversationMessage();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        message.setCreatedAt(OffsetDateTime.now());
        messageRepository.save(message);
    }

    private void saveCachedMessage(CachedConversation conversation, String role, String content) {
        conversation.messages.add(new CachedMessage(
                cacheMessageIdSequence.incrementAndGet(),
                role,
                content == null ? "" : content,
                OffsetDateTime.now()
        ));
        while (conversation.messages.size() > CACHE_MESSAGE_LIMIT) {
            conversation.messages.remove(0);
        }
    }

    private ConversationSummaryResponse cachedSummary(CachedConversation conversation) {
        synchronized (conversation) {
            return new ConversationSummaryResponse(
                    conversation.id,
                    conversation.title,
                    conversation.createdAt,
                    conversation.updatedAt
            );
        }
    }

    private int deleteCachedUserConversations(String username) {
        List<String> keys = conversationCache.asMap()
                .entrySet()
                .stream()
                .filter(entry -> username.equals(entry.getValue().username))
                .map(Map.Entry::getKey)
                .toList();
        conversationCache.invalidateAll(keys);
        return keys.size();
    }

    private String previousUserContext(List<MessageSnapshot> previousMessages) {
        List<String> userMessages = previousMessages.stream()
                .filter(message -> ROLE_USER.equals(message.role()))
                .map(message -> preview(message.content(), CONTEXT_CHARS_PER_MESSAGE))
                .filter(value -> value != null && !value.isBlank())
                .toList();

        if (userMessages.isEmpty()) {
            return "";
        }

        List<String> limited = userMessages.size() <= CONTEXT_MESSAGE_LIMIT
                ? userMessages
                : userMessages.subList(userMessages.size() - CONTEXT_MESSAGE_LIMIT, userMessages.size());
        return String.join("\n", limited.stream().map(value -> "- " + value).toList());
    }

    private String lastAssistantListMessage(List<MessageSnapshot> previousMessages) {
        for (int index = previousMessages.size() - 1; index >= 0; index--) {
            MessageSnapshot message = previousMessages.get(index);
            if (ROLE_ASSISTANT.equals(message.role()) && containsListItem(message.content())) {
                return message.content();
            }
        }
        return "";
    }

    private boolean containsListItem(String value) {
        return value != null && value.lines().anyMatch(line -> line.trim().matches("-?\\s*#\\d+\\s+.*"));
    }

    private String contextualAiMessage(String previousUserContext, String currentMessage) {
        if (previousUserContext == null || previousUserContext.isBlank()) {
            return currentMessage;
        }
        return """
                Use these previous user messages only to resolve references. Do not answer them again.
                Previous user messages:
                %s

                Current user message:
                %s
                """.formatted(previousUserContext, currentMessage);
    }

    private String backendContextMessage(String previousUserContext, String currentMessage) {
        if (previousUserContext == null || previousUserContext.isBlank()) {
            return currentMessage;
        }
        return previousUserContext + "\n" + currentMessage;
    }

    private String safeConversationId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return SAFE_CONVERSATION_ID.matcher(trimmed).matches() ? trimmed : null;
    }

    private String preview(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String cacheKey(String username, String conversationId) {
        return username + ":" + conversationId;
    }

    public record ConversationContext(
            String conversationId,
            String aiMessage,
            String backendContextMessage,
            String lastAssistantMessage
    ) {
    }

    private record MessageSnapshot(String role, String content) {
    }

    private record CachedMessage(Long id, String role, String content, OffsetDateTime createdAt) {
    }

    private static final class CachedConversation {
        private final String id;
        private final String username;
        private final String title;
        private final OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;
        private final List<CachedMessage> messages;

        private CachedConversation(String id,
                                   String username,
                                   String title,
                                   OffsetDateTime createdAt,
                                   OffsetDateTime updatedAt,
                                   List<CachedMessage> messages) {
            this.id = id;
            this.username = username;
            this.title = title;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.messages = messages;
        }
    }
}
