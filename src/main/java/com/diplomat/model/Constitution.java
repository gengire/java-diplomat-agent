package com.diplomat.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "constitutions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Constitution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 8000)
    private String content; // The full constitution text (Markdown)

    @Column(nullable = false)
    private String createdBy; // "TEMPLATE" or participant names

    @Column(nullable = false)
    private boolean finalized; // Both parties agreed

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
