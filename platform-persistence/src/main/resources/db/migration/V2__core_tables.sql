CREATE TABLE log_events (
                            id UUID DEFAULT gen_random_uuid(),
                            service TEXT NOT NULL,
                            level TEXT NOT NULL,
                            message TEXT NOT NULL,
                            stack_trace TEXT,
                            trace_id TEXT,
                            span_id TEXT,
                            metadata JSONB NOT NULL DEFAULT '{}',
                            fingerprint TEXT NOT NULL,
                            occurred_at TIMESTAMPTZ NOT NULL,
                            ingested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                            PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

CREATE TABLE incidents (
                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           tenant_id UUID NOT NULL,
                           title TEXT NOT NULL,
                           summary TEXT NOT NULL,
                           root_cause TEXT,
                           status TEXT NOT NULL,
                           severity TEXT NOT NULL,
                           service TEXT NOT NULL,
                           first_seen_at TIMESTAMPTZ NOT NULL,
                           last_seen_at TIMESTAMPTZ NOT NULL,
                           created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tickets (
                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         incident_id UUID REFERENCES incidents(id),
                         title TEXT NOT NULL,
                         description TEXT NOT NULL,
                         severity TEXT NOT NULL,
                         service TEXT NOT NULL,
                         suspected_files JSONB NOT NULL DEFAULT '[]',
                         fix_suggestion TEXT,
                         external_id TEXT,
                         status TEXT NOT NULL,
                         created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
