-- Technical address for the login-disabled system audit account only.
UPDATE app_user
SET email = 'system@clinic.invalid'
WHERE username = 'system'
  AND user_status = FALSE
  AND password_hash = '!login-disabled'
  AND email IS NULL;

-- Existing accounts must receive reviewed email addresses before upgrade.
-- Never generate placeholder emails for real users.
DO $$
    BEGIN
        IF EXISTS (
            SELECT 1
            FROM app_user
            WHERE email IS NULL
        ) THEN
            RAISE EXCEPTION
                'Cannot require app_user email: existing accounts without email require review';
        END IF;
    END
$$;

ALTER TABLE app_user
    ALTER COLUMN email SET NOT NULL;