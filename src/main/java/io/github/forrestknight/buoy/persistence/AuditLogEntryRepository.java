package io.github.forrestknight.buoy.persistence;

import io.github.forrestknight.buoy.domain.AuditLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogEntryRepository
        extends JpaRepository<AuditLogEntry, Long>, JpaSpecificationExecutor<AuditLogEntry> {

    Page<AuditLogEntry> findByProjectId(Long projectId, Pageable pageable);
}
