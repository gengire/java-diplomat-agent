package com.diplomat.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(nullable = false)
    private String sender; // participant name or "DIPLOMAT"

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(nullable = false)
    private String messageType; // CHAT, DIPLOMAT_OBSERVATION, DIPLOMAT_REFRAME, FALLACY_ALERT, TEMPERATURE_CHECK, SYSTEM

    @Column
    private String fallacyType; // null unless messageType is FALLACY_ALERT

    @Column
    private String recipient; // null = public message, participant name = private to that person

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) timestamp = LocalDateTime.now();
    }
}
