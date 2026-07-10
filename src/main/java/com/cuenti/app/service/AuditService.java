package com.cuenti.app.service;

import com.cuenti.app.model.AuditLog;
import com.cuenti.app.model.User;
import com.cuenti.app.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes audit-log entries inside the calling transaction, so an entry
 * exists exactly when the audited change was committed.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    @Transactional
    public void log(User actor, String action, String entityType, Long entityId, String details) {
        try {
            repository.save(AuditLog.builder()
                    .userId(actor != null ? actor.getId() : null)
                    .username(actor != null ? actor.getUsername() : "system")
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .details(details != null && details.length() > 500 ? details.substring(0, 500) : details)
                    .build());
        } catch (Exception e) {
            // Auditing must never break the business operation
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> latest(String filter, int page, int size) {
        PageRequest pr = PageRequest.of(page, size);
        if (filter == null || filter.isBlank()) {
            return repository.findAllByOrderByTimestampDesc(pr);
        }
        return repository.findByUsernameContainingIgnoreCaseOrEntityTypeContainingIgnoreCaseOrderByTimestampDesc(
                filter, filter, pr);
    }
}
