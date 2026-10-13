-- Reference data every environment needs: roles, permissions, payment methods and master data.
-- Every row needs an author (the *_create_by columns are NOT NULL), so a login-disabled
-- 'system' user is created first.
INSERT INTO app_user (username, password_hash, user_status, last_login_at)
VALUES ('system', '!login-disabled', FALSE, CURRENT_TIMESTAMP);

INSERT INTO role (role_name, role_create_by)
SELECT r.role_name::role_name_enum, u.user_id
FROM app_user u
CROSS JOIN (VALUES ('Manager'), ('Therapist')) AS r(role_name)
WHERE u.username = 'system';

INSERT INTO permission (permission_name, module_name, permission_create_by)
SELECT p.permission_name, p.module_name, u.user_id
FROM app_user u
CROSS JOIN (VALUES
    ('COMMISSION_SCHEME_MANAGE', 'Commission'),
    ('COMMISSION_CLOSING_MANAGE', 'Commission'),
    ('COMMISSION_RATE_OVERRIDE', 'Commission'),
    ('SALE_CANCEL', 'Sale'),
    ('COURSE_REVERSE', 'Course')
) AS p(permission_name, module_name)
WHERE u.username = 'system';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM role r
CROSS JOIN permission p
WHERE r.role_name = 'Manager';

INSERT INTO payment_method (method_name, payment_method_create_by)
SELECT m.method_name::method_name_enum, u.user_id
FROM app_user u
CROSS JOIN (VALUES ('Cash'), ('BankTransfer'), ('PromptPay'), ('CreditCard')) AS m(method_name)
WHERE u.username = 'system';

INSERT INTO master_data_type (type_name, type_create_by)
SELECT t.type_name, u.user_id
FROM app_user u
CROSS JOIN (VALUES ('Customer Group'), ('Referral Channel'), ('Insurance Type'), ('Insurance Company')) AS t(type_name)
WHERE u.username = 'system';

INSERT INTO master_data_value (type_id, value_name, sort_order, value_create_by)
SELECT t.type_id, v.value_name, v.sort_order, u.user_id
FROM master_data_type t
JOIN (VALUES
    ('Customer Group', 'General', 1),
    ('Customer Group', 'VIP', 2),
    ('Referral Channel', 'Walk-in', 1),
    ('Referral Channel', 'Facebook', 2),
    ('Referral Channel', 'Friend', 3),
    ('Insurance Type', 'None', 1)
) AS v(type_name, value_name, sort_order) ON v.type_name = t.type_name
CROSS JOIN app_user u
WHERE u.username = 'system';
