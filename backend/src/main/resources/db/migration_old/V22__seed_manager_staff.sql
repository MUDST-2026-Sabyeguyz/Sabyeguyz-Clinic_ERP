-- Ensure the development manager account is visible in Staff > Users.
INSERT INTO staff (id, employee_code, full_name, phone)
SELECT u.id, 'MGR-001', 'Manager Demo', '0800000000'
FROM app_users u
WHERE u.username = 'manager'
  AND NOT EXISTS (SELECT 1 FROM staff s WHERE s.id = u.id);
