-- Whether a physical card reader is plugged in at the counter, as last reported by its agent (NULL: an older agent that does not say).
ALTER TABLE card_reader_workstation ADD COLUMN reader_present BOOLEAN;
