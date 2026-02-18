package com.diplomat.repository;

import com.diplomat.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByConversationIdOrderByTimestampAsc(Long conversationId);
    List<Message> findByConversationSessionCodeOrderByTimestampAsc(String sessionCode);
    long countByConversationIdAndSender(Long conversationId, String sender);
}
