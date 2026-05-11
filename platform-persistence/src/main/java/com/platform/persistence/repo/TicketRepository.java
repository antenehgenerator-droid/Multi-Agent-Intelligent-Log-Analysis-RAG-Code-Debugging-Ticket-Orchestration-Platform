package com.platform.persistence.repo;

import com.platform.persistence.entity.TicketEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<TicketEntity, UUID> {
  boolean existsByIncidentId(UUID incidentId);
}

