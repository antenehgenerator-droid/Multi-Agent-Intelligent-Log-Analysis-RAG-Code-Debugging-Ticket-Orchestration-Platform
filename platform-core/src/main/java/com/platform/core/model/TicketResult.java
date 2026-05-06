package com.platform.core.model;

import java.util.UUID;

public record TicketResult(
    Status status,
    UUID linkedIncidentId,
    String externalTicketId
) {
    public enum Status {
        DISCARDED, DEDUPLICATED, TICKETED, DEGRADED
    }

    public static TicketResult discarded() {
        return new TicketResult(Status.DISCARDED, null, null);
    }

    public static TicketResult duplicate(UUID incidentId) {
        return new TicketResult(Status.DEDUPLICATED, incidentId, null);
    }
}
