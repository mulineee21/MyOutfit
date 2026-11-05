-- ============================================================================
-- Migración 002: Control de cambio mensual de nombre de perfil (@username)
-- ============================================================================
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS username_changed_at TIMESTAMPTZ;
