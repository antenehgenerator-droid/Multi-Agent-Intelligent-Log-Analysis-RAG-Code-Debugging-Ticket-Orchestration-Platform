package com.platform.persistence.repo;

import com.platform.persistence.entity.IncidentEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<IncidentEntity, UUID> {}

