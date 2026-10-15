
-- username can't be xx@x
ALTER TABLE app_user
    ADD CONSTRAINT chk_app_user_username_no_at
        CHECK (POSITION('@' IN username) = 0);

-- ADD Column : email
ALTER TABLE app_user
    ADD COLUMN email VARCHAR(254);

ALTER TABLE app_user
    ADD CONSTRAINT chk_app_user_email_format
        CHECK (
            email IS NULL
                OR (
                email = BTRIM(email)    -- format such as: sbz@example.com, sbz.clinic@example.co.th
                    AND email ~ '^[^@.[:space:]]+([.][^@.[:space:]]+)*@[^@.[:space:]]+([.][^@.[:space:]]+)+$'
                )
            );

CREATE UNIQUE INDEX uq_app_user_email_ci
    ON app_user (LOWER(email));


-- Future migration: after assigning emails to all existing users,
-- enforce NOT NULL with ALTER TABLE app_user ALTER COLUMN email SET NOT NULL.

-- ALTER TABLE app_user
--     ALTER COLUMN email SET NOT NULL;