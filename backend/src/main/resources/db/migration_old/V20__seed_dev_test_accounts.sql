-- Development/test accounts only. Passwords are BCrypt hashes.
INSERT INTO app_users (username, email, password_hash, role, active)
SELECT 'manager', 'manager@clinic.local', '$2a$10$26pLsRLyoUN2ft4i72Scr.hEVVCFljFK6ao8aFyKHRupJMP5LilKm', 'MANAGER', TRUE
WHERE NOT EXISTS (SELECT 1 FROM app_users WHERE username = 'manager');

INSERT INTO app_users (username, email, password_hash, role, active)
SELECT 'therapist', 'therapist@clinic.local', '$2a$10$ni3Y/yJ714sLk21b9s5wH.qWjDfBfHYeig2I6kEvxV0ud8Ch1.s9u', 'THERAPIST', TRUE
WHERE NOT EXISTS (SELECT 1 FROM app_users WHERE username = 'therapist');

INSERT INTO staff (id, employee_code, full_name, phone)
SELECT u.id, 'THP-001', 'Therapist Demo', '0800000001'
FROM app_users u
WHERE u.username = 'therapist'
  AND NOT EXISTS (SELECT 1 FROM staff s WHERE s.id = u.id);
