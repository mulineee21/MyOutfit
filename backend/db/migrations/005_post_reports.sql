-- ============================================================================
-- Migración 005: Denuncias y moderación de contenido (DSA - Reglamento UE 2022/2065)
-- Mecanismo de notificación y acción ("Notice and Action") según Art. 16 DSA
-- ============================================================================

CREATE TABLE IF NOT EXISTS post_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES ephemeral_posts(id) ON DELETE CASCADE,
    reporter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason VARCHAR(50) NOT NULL,
    details VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (post_id, reporter_id)
);

CREATE INDEX IF NOT EXISTS idx_post_reports_status ON post_reports(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_post_reports_post ON post_reports(post_id);
