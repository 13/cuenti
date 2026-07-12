package com.cuenti.app.api;

import com.cuenti.app.api.dto.AuditLogDTO;
import com.cuenti.app.api.dto.DtoMapper;
import com.cuenti.app.model.AuditLog;
import com.cuenti.app.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/audit-log")
@RequiredArgsConstructor
public class AuditLogApiController {

    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAuditLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String filter) {
        Page<AuditLog> result = auditService.latest(filter, page, Math.min(size, 200));
        return ResponseEntity.ok(Map.of(
                "content", result.getContent().stream().map(DtoMapper::toAuditLogDTO).collect(Collectors.toList()),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        ));
    }
}
