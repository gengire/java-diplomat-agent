package com.diplomat.service;

import com.diplomat.model.Conversation;
import com.diplomat.model.Message;
import com.diplomat.repository.ConversationRepository;
import com.diplomat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    /**
     * Create a new conversation session. Returns the session code.
     */
    @Transactional
    public Conversation createSession(String participantA) {
        String code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Conversation conv = Conversation.builder()
                .sessionCode(code)
                .participantA(participantA)
                .participantB("") // filled when second person joins
                .status("WAITING")
                .mode("FREE_TALK")
                .createdAt(LocalDateTime.now())
                .build();
        return conversationRepository.save(conv);
    }

    /**
     * Join an existing session as participant B.
     */
    @Transactional
    public Conversation joinSession(String sessionCode, String participantB) {
        Conversation conv = conversationRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionCode));

        if ("ACTIVE".equals(conv.getStatus())) {
            throw new RuntimeException("Session already has two participants");
        }

        conv.setParticipantB(participantB);
        conv.setStatus("ACTIVE");
        return conversationRepository.save(conv);
    }

    public Optional<Conversation> findBySessionCode(String sessionCode) {
        return conversationRepository.findBySessionCode(sessionCode);
    }

    /**
     * Save a message to the conversation.
     */
    @Transactional
    public Message saveMessage(String sessionCode, String sender, String content, String messageType) {
        return saveMessage(sessionCode, sender, content, messageType, null);
    }

    /**
     * Save a message to the conversation with optional recipient for private messages.
     */
    @Transactional
    public Message saveMessage(String sessionCode, String sender, String content, String messageType, String recipient) {
        Conversation conv = conversationRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionCode));

        Message message = Message.builder()
                .conversation(conv)
                .sender(sender)
                .content(content)
                .messageType(messageType)
                .recipient(recipient)
                .timestamp(LocalDateTime.now())
                .build();
        return messageRepository.save(message);
    }

    /**
     * Save a Diplomat message (with optional fallacy type).
     */
    @Transactional
    public Message saveDiplomatMessage(String sessionCode, String content, String messageType, String fallacyType) {
        return saveDiplomatMessage(sessionCode, content, messageType, fallacyType, null);
    }

    /**
     * Save a Diplomat message with optional recipient for private coaching.
     */
    @Transactional
    public Message saveDiplomatMessage(String sessionCode, String content, String messageType, String fallacyType, String recipient) {
        Conversation conv = conversationRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionCode));

        Message message = Message.builder()
                .conversation(conv)
                .sender("DIPLOMAT")
                .content(content)
                .messageType(messageType)
                .fallacyType(fallacyType)
                .recipient(recipient)
                .timestamp(LocalDateTime.now())
                .build();
        return messageRepository.save(message);
    }

    /**
     * Get recent messages for context window (last N messages).
     */
    public List<Message> getRecentMessages(String sessionCode, int limit) {
        List<Message> all = messageRepository.findByConversationSessionCodeOrderByTimestampAsc(sessionCode);
        if (all.size() <= limit) return all;
        return all.subList(all.size() - limit, all.size());
    }

    public List<Message> getAllMessages(String sessionCode) {
        return messageRepository.findByConversationSessionCodeOrderByTimestampAsc(sessionCode);
    }

    /**
     * Get private coaching messages between The Diplomat and a specific participant.
     */
    public List<Message> getPrivateMessages(String sessionCode, String participant) {
        return messageRepository.findByConversationSessionCodeOrderByTimestampAsc(sessionCode)
                .stream()
                .filter(m -> participant.equals(m.getRecipient()) || 
                        (participant.equals(m.getSender()) && m.getRecipient() != null))
                .toList();
    }

    /**
     * Get recent messages for context, including only public messages and private messages
     * visible to the specified participant (for private coaching context).
     */
    public List<Message> getRecentMessagesForParticipant(String sessionCode, String participant, int limit) {
        List<Message> all = messageRepository.findByConversationSessionCodeOrderByTimestampAsc(sessionCode)
                .stream()
                .filter(m -> m.getRecipient() == null || 
                        participant.equals(m.getRecipient()) || 
                        participant.equals(m.getSender()))
                .toList();
        if (all.size() <= limit) return all;
        return all.subList(all.size() - limit, all.size());
    }

    @Transactional
    public void endSession(String sessionCode) {
        Conversation conv = conversationRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionCode));
        conv.setStatus("ENDED");
        conv.setEndedAt(LocalDateTime.now());
        conversationRepository.save(conv);
    }

    @Transactional
    public void setMode(String sessionCode, String mode) {
        Conversation conv = conversationRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionCode));
        conv.setMode(mode);
        conversationRepository.save(conv);
    }

    /**
     * Set the interaction level (1-10) for a specific participant.
     */
    @Transactional
    public Conversation setInteractionLevel(String sessionCode, String participant, int level) {
        Conversation conv = conversationRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionCode));

        int clamped = Math.max(1, Math.min(10, level));

        if (participant.equals(conv.getParticipantA())) {
            conv.setInteractionLevelA(clamped);
        } else if (participant.equals(conv.getParticipantB())) {
            conv.setInteractionLevelB(clamped);
        } else {
            throw new RuntimeException("Participant not found in session: " + participant);
        }

        return conversationRepository.save(conv);
    }
}
