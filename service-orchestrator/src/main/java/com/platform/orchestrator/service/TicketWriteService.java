package com.platform.orchestrator.service;

import com.platform.persistence.entity.IncidentEntity;
import com.platform.persistence.entity.TicketEntity;
import com.platform.persistence.repo.IncidentRepository;
import com.platform.persistence.repo.TicketRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketWriteService {

  private final IncidentRepository incidentRepository;
  private final TicketRepository ticketRepository;

  public TicketWriteService(
      IncidentRepository incidentRepository, TicketRepository ticketRepository) {
    this.incidentRepository = incidentRepository;
    this.ticketRepository = ticketRepository;
  }

  @Transactional
  public void saveIncidentAndTicket(IncidentEntity incident, TicketEntity ticket, UUID incidentId) {
    if (!incidentRepository.existsById(incidentId)) {
      incidentRepository.save(incident);
    }
    ticketRepository.save(ticket);
  }
}
