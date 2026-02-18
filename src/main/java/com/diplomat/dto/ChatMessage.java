package com.diplomat.dto;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatMessage {
    private String sessionCode;
    private String sender;
    private String content;
    private String type; // CHAT, JOIN, LEAVE, REWIND
}
