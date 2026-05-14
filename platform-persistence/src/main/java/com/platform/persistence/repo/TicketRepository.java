package com.platform.persistence.repo;

import com.platform.persistence.entity.TicketEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Tickets keyed by {@code incident_id}. Stub orchestration uses a deterministic {@link UUID}
 * derived from {@code sha256(fingerprint + ":" + anomalyType)} as {@code incident_id} so the
 * same incident hashes idempotently to one ticket row.
 */
public interface TicketRepository extends JpaRepository<TicketEntity, UUID> {
  boolean existsByIncidentId(UUID incidentId);
}

