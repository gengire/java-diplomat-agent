package com.diplomat.repository;

import com.diplomat.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findBySessionCode(String sessionCode);
    List<Conversation> findByParticipantAAndParticipantBOrderByCreatedAtDesc(String a, String b);
    List<Conversation> findByStatus(String status);
}
