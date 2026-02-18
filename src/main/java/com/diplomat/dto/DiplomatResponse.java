package com.diplomat.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DiplomatResponse {
    private String sender;       // "DIPLOMAT"
    private String content;
    private String responseType; // OBSERVATION, REFRAME, FALLACY_ALERT, TEMPERATURE_CHECK, CONSTITUTION_REMINDER, SUMMARY
    private String fallacyType;  // null unless FALLACY_ALERT
    private LocalDateTime timestamp;
}
