package com.diplomat.repository;

import com.diplomat.model.PatternNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PatternNoteRepository extends JpaRepository<PatternNote, Long> {
    List<PatternNote> findByParticipantAAndParticipantBOrderByLastObservedDesc(String a, String b);
    List<PatternNote> findByCategoryOrderByOccurrenceCountDesc(String category);
}
