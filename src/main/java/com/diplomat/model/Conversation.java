package com.diplomat.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "conversations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sessionCode;

    @Column(nullable = false)
    private String participantA;

    @Column(nullable = false)
    private String participantB;

    @Column(nullable = false)
    private String status; // ACTIVE, PAUSED, ENDED

    @Column(nullable = false)
    private String mode; // FREE_TALK, GUIDED, DEBRIEF

    @Column(nullable = false)
    @Builder.Default
    @org.hibernate.annotations.ColumnDefault("5")
    private int interactionLevelA = 5; // 1=silent, 10=very active (Person A's preference)

    @Column(nullable = false)
    @Builder.Default
    @org.hibernate.annotations.ColumnDefault("5")
    private int interactionLevelB = 5; // 1=silent, 10=very active (Person B's preference)

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("timestamp ASC")
    @Builder.Default
    private List<Message> messages = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "constitution_id")
    private Constitution constitution;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime endedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = "ACTIVE";
        if (mode == null) mode = "FREE_TALK";
    }
}
