-- ============================================================================
-- Migración 003: Cumplimiento estricto RGPD (UE 2016/679) y LOPDGDD (España)
-- - Registro auditable de aceptación de Política de Privacidad y mayoría de 14 años
-- - Privacidad por defecto (Art. 25.2 RGPD): cuentas nuevas privadas por defecto
-- ============================================================================
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS privacy_accepted_at TIMESTAMPTZ DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS privacy_policy_version VARCHAR(20) NOT NULL DEFAULT '2026.1-ES',
    ALTER COLUMN is_private SET DEFAULT TRUE;

UPDATE users
SET privacy_accepted_at = COALESCE(privacy_accepted_at, created_at)
WHERE privacy_accepted_at IS NULL;
