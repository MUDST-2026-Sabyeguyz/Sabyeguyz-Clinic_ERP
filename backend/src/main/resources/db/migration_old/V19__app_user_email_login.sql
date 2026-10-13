ALTER TABLE app_users ADD COLUMN email VARCHAR(255);
CREATE UNIQUE INDEX ux_app_users_email ON app_users(email);
