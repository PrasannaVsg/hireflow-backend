-- Fix placeholder BCrypt hash from V4 with a real BCrypt(12) hash.
-- Hash is for password: ChangeMe!2026
UPDATE users
SET password_hash = '$2a$12$ZqGcjL0SfyoIr9.LcIYZmOR8a52NuwaoaqIcuR0hIzDnrY88dZ32C'
WHERE email = 'admin@hireflow.ai';
