-- Demo-only data: loaded only when db/seed_v2 is explicitly enabled.
-- Match the original demo credentials; never overwrite existing emails.
UPDATE app_user AS u
SET email = demo.email
    FROM (
    VALUES
        (
            'manager',
            'manager@example.com',
            '$2a$10$26pLsRLyoUN2ft4i72Scr.hEVVCFljFK6ao8aFyKHRupJMP5LilKm'
        ),
        (
            'therapist',
            'therapist@example.com',
            '$2a$10$ni3Y/yJ714sLk21b9s5wH.qWjDfBfHYeig2I6kEvxV0ud8Ch1.s9u'
        ),
        (
            'therapist2',
            'therapist2@example.com',
            '$2a$10$ni3Y/yJ714sLk21b9s5wH.qWjDfBfHYeig2I6kEvxV0ud8Ch1.s9u'
        )
) AS demo(username, email, password_hash)
WHERE u.username = demo.username
  AND u.password_hash = demo.password_hash
  AND u.email IS NULL;