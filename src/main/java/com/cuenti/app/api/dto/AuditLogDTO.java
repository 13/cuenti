package com.cuenti.app.api.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogDTO {
    private Long id;
    private Long userId;
    private String username;
    private LocalDateTime timestamp;
    private String entityType;
    private Long entityId;
    private String action;
    private String details;
}
