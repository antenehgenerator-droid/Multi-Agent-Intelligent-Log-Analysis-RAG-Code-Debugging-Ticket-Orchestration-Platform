package com.platform.persistence.repo;

import com.platform.persistence.entity.AgentExecutionEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentExecutionRepository extends JpaRepository<AgentExecutionEntity, UUID> {}
