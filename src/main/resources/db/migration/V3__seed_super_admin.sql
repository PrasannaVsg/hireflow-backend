-- Bootstrap platform organisation and the first SUPER_ADMIN.
-- Password is BCrypt(12) for: ChangeMe!2026  — change on first login.
INSERT INTO organisations (id, name, slug, plan_tier, monthly_ai_quota)
VALUES ('00000000-0000-0000-0000-000000000001', 'HireFlow Platform', 'platform', 'ENTERPRISE', 1000000);

INSERT INTO users (id, organisation_id, email, password_hash, full_name, role, must_change_password)
VALUES (
    '00000000-0000-0000-0000-0000000000a1',
    '00000000-0000-0000-0000-000000000001',
    'admin@hireflow.ai',
    '$2a$12$ZqGcjL0SfyoIr9.LcIYZmOR8a52NuwaoaqIcuR0hIzDnrY88dZ32C',
    'Platform Admin',
    'SUPER_ADMIN',
    TRUE
);
