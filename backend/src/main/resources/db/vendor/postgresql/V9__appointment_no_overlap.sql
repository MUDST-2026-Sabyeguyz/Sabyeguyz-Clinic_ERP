CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE appointments
    ADD CONSTRAINT excl_appointments_therapist_overlap
    EXCLUDE USING gist (
        therapist_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    ) WHERE (status <> 'CANCELLED');
