ALTER TABLE incidents ENABLE ROW LEVEL SECURITY;

-- Note: 'app.tenant_id' must be set in the DB session before queries
CREATE POLICY tenant_isolation ON incidents
    USING (tenant_id = current_setting('app.tenant_id')::uuid);
