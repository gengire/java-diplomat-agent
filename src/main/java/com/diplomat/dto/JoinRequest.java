package com.diplomat.dto;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class JoinRequest {
    private String sessionCode;
    private String participantName;
}
