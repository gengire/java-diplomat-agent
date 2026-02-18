package com.diplomat.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pattern_notes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PatternNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String participantA;

    @Column(nullable = false)
    private String participantB;

    @Column(nullable = false, length = 4000)
    private String pattern; // Description of observed pattern

    @Column(nullable = false)
    private String category; // FALLACY, ESCALATION, TOPIC, POSITIVE, COMMUNICATION_STYLE

    @Column(nullable = false)
    private int occurrenceCount;

    @Column(nullable = false)
    private LocalDateTime firstObserved;

    @Column(nullable = false)
    private LocalDateTime lastObserved;

    @PrePersist
    protected void onCreate() {
        if (firstObserved == null) firstObserved = LocalDateTime.now();
        if (lastObserved == null) lastObserved = LocalDateTime.now();
    }
}
