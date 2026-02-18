package com.diplomat.dto;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ConstitutionDto {
    private Long id;
    private String title;
    private String content;
    private boolean finalized;
}
