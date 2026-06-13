-- Bootstrap a platform organisation and the first SUPER_ADMIN.
-- Password hash below is BCrypt for 'ChangeMe!2026' — rotate immediately after first login.
INSERT INTO organisations (id, name, slug, plan_tier, monthly_ai_quota)
VALUES ('00000000-0000-0000-0000-000000000001', 'HireFlow Platform', 'platform', 'ENTERPRISE', 1000000);

INSERT INTO users (id, organisation_id, email, password_hash, full_name, role)
VALUES (
    '00000000-0000-0000-0000-0000000000a1',
    '00000000-0000-0000-0000-000000000001',
    'admin@hireflow.ai',
    '$2a$12$Q8m1Yx3o0t6Zr0E0a8H1uO2xq6kQ7m6m8s9N4qF1iV2bJ0p4nC7e',
    'Platform Admin',
    'SUPER_ADMIN'
);
