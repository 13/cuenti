package com.cuenti.app.repository;

import com.cuenti.app.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    Page<AuditLog> findByUsernameContainingIgnoreCaseOrEntityTypeContainingIgnoreCaseOrderByTimestampDesc(
            String username, String entityType, Pageable pageable);
}
