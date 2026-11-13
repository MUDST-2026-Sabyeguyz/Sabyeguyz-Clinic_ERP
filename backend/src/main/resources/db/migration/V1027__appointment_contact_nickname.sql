-- Optional nickname of an unregistered (NEW) appointment contact.
ALTER TABLE appointment
    ADD COLUMN contact_nickname VARCHAR(50);
