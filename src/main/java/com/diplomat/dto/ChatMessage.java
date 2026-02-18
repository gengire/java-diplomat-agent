package com.diplomat.dto;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatMessage {
    private String sessionCode;
    private String sender;
    private String content;
    private String type; // CHAT, JOIN, LEAVE, REWIND, TRANSLATE, PARKING_LOT, PRIVATE
    private Integer interactionLevel; // 1-10, sent with slider changes
    private String recipient; // null = public, participant name = private coaching message
}
