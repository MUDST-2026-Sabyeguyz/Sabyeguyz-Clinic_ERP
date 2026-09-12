-- นัดที่ผู้ป่วยไม่มาตามนัดต้องปล่อยช่วงเวลาคืนเหมือนนัดที่ถูกยกเลิก
-- เงื่อนไข WHERE ต้องตรงกับ AppointmentStatus.slotReleasing()
ALTER TABLE appointments
    DROP CONSTRAINT excl_appointments_therapist_overlap;

ALTER TABLE appointments
    ADD CONSTRAINT excl_appointments_therapist_overlap
    EXCLUDE USING gist (
        therapist_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    ) WHERE (status NOT IN ('CANCELLED', 'NO_SHOW'));
