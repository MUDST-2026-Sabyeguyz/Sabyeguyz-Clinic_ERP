-- Keep one demo visit in the current reporting period so the dashboard has
-- a meaningful attended-patient example after a fresh or existing startup.
UPDATE appointments
SET start_at = (CURRENT_DATE - INTERVAL '1 day' + INTERVAL '10 hours') AT TIME ZONE 'Asia/Bangkok',
    end_at = (CURRENT_DATE - INTERVAL '1 day' + INTERVAL '10 hours 30 minutes') AT TIME ZONE 'Asia/Bangkok',
    status = 'COMPLETED'
WHERE note = 'ข้อมูลนัดหมายจำลอง 2';
