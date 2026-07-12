package com.cuenti.app.api.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedViewDTO {
    private Long id;
    private String name;
    private String params;
    private LocalDateTime createdAt;
}
