-- V1027 added contact_nickname as VARCHAR(50); the entity and API allow 80.
ALTER TABLE appointment
    ALTER COLUMN contact_nickname TYPE VARCHAR(80);
