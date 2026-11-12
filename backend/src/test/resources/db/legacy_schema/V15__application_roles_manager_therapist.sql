-- Migrate legacy persisted role values without changing an applied migration.
UPDATE app_users SET role = 'MANAGER' WHERE role = 'ADMIN';
UPDATE app_users SET role = 'THERAPIST' WHERE role = 'STAFF';
ALTER TABLE app_users ADD CONSTRAINT chk_app_users_role CHECK (role IN ('MANAGER', 'THERAPIST'));
