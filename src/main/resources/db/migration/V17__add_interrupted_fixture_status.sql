ALTER TABLE fixture DROP CHECK chk_fixture_status;
ALTER TABLE fixture ADD CONSTRAINT chk_fixture_status
    CHECK (status IN ('DRAFT', 'SCHEDULED', 'LIVE', 'FINISHED', 'CANCELLED', 'INTERRUPTED'));
