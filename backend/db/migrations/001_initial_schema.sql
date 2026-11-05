-- ============================================================================
-- MyOutfit Social ("BeReal de Outfits") - Esquema Inicial PostgreSQL 17
-- Archivo: 001_initial_schema.sql
-- ============================================================================

BEGIN;

-- Extensiones útiles (UUIDs criptográficos y búsquedas insensibles a mayúsculas)
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;

-- ============================================================================
-- 1. TIPOS ENUMERADOS
-- ============================================================================

-- Estado de la relación de seguimiento (soporta cuentas públicas y privadas)
DO $$ BEGIN
    CREATE TYPE follow_status AS ENUM ('PENDING', 'ACCEPTED', 'BLOCKED');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

-- Origen del outfit publicado (conecta con la IA y el Armario de la app Android)
DO $$ BEGIN
    CREATE TYPE outfit_source AS ENUM ('REAL_CAMERA', 'AI_GENERATED', 'CLOSET_COLLAGE');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

-- Alcance de visibilidad durante las 24 horas activas
DO $$ BEGIN
    CREATE TYPE post_visibility AS ENUM ('FOLLOWERS', 'PUBLIC', 'PRIVATE_ONLY');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

-- ============================================================================
-- 2. TABLA DE USUARIOS (Soporta Correo/Contraseña + Inicio de Sesión con Google)
-- ============================================================================
CREATE TABLE IF NOT EXISTS users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username            CITEXT NOT NULL UNIQUE,
    email               CITEXT NOT NULL UNIQUE,
    password_hash       TEXT,
    google_sub          TEXT UNIQUE,
    email_verified      BOOLEAN NOT NULL DEFAULT FALSE,
    display_name        VARCHAR(60) NOT NULL,
    bio                 VARCHAR(200) NOT NULL DEFAULT '',
    avatar_webp_path       TEXT,
    is_private             BOOLEAN NOT NULL DEFAULT TRUE,
    total_outfits_count    INTEGER NOT NULL DEFAULT 0 CHECK (total_outfits_count >= 0),
    current_streak         INTEGER NOT NULL DEFAULT 0 CHECK (current_streak >= 0),
    username_changed_at    TIMESTAMPTZ,
    privacy_accepted_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    privacy_policy_version VARCHAR(20) NOT NULL DEFAULT '2026.1-ES',
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_username_format CHECK (username ~ '^[a-zA-Z0-9_.]{3,30}$'),
    CONSTRAINT chk_auth_method_present CHECK (password_hash IS NOT NULL OR google_sub IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users (username);
CREATE INDEX IF NOT EXISTS idx_users_google_sub ON users (google_sub) WHERE google_sub IS NOT NULL;

-- ============================================================================
-- 3. SESIONES, REFRESH TOKENS Y RECUPERACIÓN DE CONTRASEÑA
-- ============================================================================
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      TEXT NOT NULL UNIQUE,
    device_info     VARCHAR(120),
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_active
    ON refresh_tokens (user_id, expires_at)
    WHERE revoked_at IS NULL;

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash       TEXT NOT NULL,
    attempts        INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_password_reset_active
    ON password_reset_tokens (user_id, expires_at DESC)
    WHERE used_at IS NULL;

-- ============================================================================
-- 4. RELACIONES DE SEGUIMIENTO (Seguidores / Seguidos / Solicitudes)
-- ============================================================================
CREATE TABLE IF NOT EXISTS follows (
    follower_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    following_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status          follow_status NOT NULL DEFAULT 'ACCEPTED',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    responded_at    TIMESTAMPTZ,

    PRIMARY KEY (follower_id, following_id),
    CONSTRAINT chk_no_self_follow CHECK (follower_id <> following_id)
);

-- Índices para resolver rápido "¿a quién sigo?" (Feed) y "¿quién me sigue?"
CREATE INDEX IF NOT EXISTS idx_follows_follower_accepted
    ON follows (follower_id, following_id)
    WHERE status = 'ACCEPTED';

CREATE INDEX IF NOT EXISTS idx_follows_following_status
    ON follows (following_id, status, created_at DESC);

-- ============================================================================
-- 5. BÓVEDA Y CALENDARIO PRIVADO PERMANENTE (Core Feature & Yearly Recap)
-- Persiste indefinidamente aunque el post público caduque a las 24h.
-- Las rutas de imagen NUNCA se sirven como archivos estáticos públicos;
-- el backend verifica propiedad o ventana activa <24h antes de hacer stream.
-- ============================================================================
CREATE TABLE IF NOT EXISTS outfit_calendar_archive (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    worn_date           DATE NOT NULL DEFAULT CURRENT_DATE,
    is_primary_of_day   BOOLEAN NOT NULL DEFAULT TRUE,

    -- Rutas relativas dentro del volumen Docker `/var/lib/myoutfit/media`
    image_webp_path     TEXT NOT NULL,
    thumbnail_webp_path TEXT NOT NULL,
    width_px            INTEGER,
    height_px           INTEGER,

    -- Metadatos enriquecidos para el Calendario y futuro "Yearly Recap"
    source_type         outfit_source NOT NULL DEFAULT 'REAL_CAMERA',
    caption             VARCHAR(280) NOT NULL DEFAULT '',
    style_tags          TEXT[] NOT NULL DEFAULT '{}',
    dominant_colors     TEXT[] NOT NULL DEFAULT '{}',
    ai_metadata         JSONB NOT NULL DEFAULT '{}'::jsonb,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_positive_dimensions CHECK (
        (width_px IS NULL OR width_px > 0) AND
        (height_px IS NULL OR height_px > 0)
    )
);

-- Garantiza un único outfit principal ("BeReal del día") por fecha para la celda del calendario,
-- permitiendo aún guardar outfits secundarios si `is_primary_of_day = FALSE`.
CREATE UNIQUE INDEX IF NOT EXISTS uq_calendar_primary_outfit_per_day
    ON outfit_calendar_archive (user_id, worn_date)
    WHERE is_primary_of_day = TRUE;

-- Índice principal para consultas mensuales `GET /api/calendar/{userId}?year=2026&month=11`
-- y resúmenes anuales ("Yearly Recap")
CREATE INDEX IF NOT EXISTS idx_calendar_user_date
    ON outfit_calendar_archive (user_id, worn_date DESC);

-- Índice GIN para analíticas anuales por estilos/colores (ej: "Tu estilo #1 fue Streetwear")
CREATE INDEX IF NOT EXISTS idx_calendar_style_tags_gin
    ON outfit_calendar_archive USING GIN (style_tags);

-- ============================================================================
-- 6. PUBLICACIONES EFÍMERAS DEL FEED SOCIAL (Ventana estricta de 24 Horas)
-- ============================================================================
CREATE TABLE IF NOT EXISTS ephemeral_posts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    archive_id          UUID NOT NULL UNIQUE REFERENCES outfit_calendar_archive(id) ON DELETE CASCADE,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    visibility          post_visibility NOT NULL DEFAULT 'FOLLOWERS',
    caption             VARCHAR(280) NOT NULL DEFAULT '',
    is_late             BOOLEAN NOT NULL DEFAULT FALSE,
    reactions_count     INTEGER NOT NULL DEFAULT 0 CHECK (reactions_count >= 0),
    comments_count      INTEGER NOT NULL DEFAULT 0 CHECK (comments_count >= 0),
    published_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '24 hours'),

    CONSTRAINT chk_valid_expiration CHECK (expires_at > published_at)
);

-- Índice compuesto crítico para `GET /api/feed`: filtra por autor seguido y ventana `expires_at > NOW()`
CREATE INDEX IF NOT EXISTS idx_ephemeral_posts_feed_lookup
    ON ephemeral_posts (user_id, expires_at DESC, published_at DESC)
    WHERE visibility <> 'PRIVATE_ONLY';

CREATE INDEX IF NOT EXISTS idx_ephemeral_posts_expires_at
    ON ephemeral_posts (expires_at DESC);

-- ============================================================================
-- 7. INTERACCIONES SOCIALES: REACCIONES RÁPIDAS Y COMENTARIOS
-- ============================================================================
CREATE TABLE IF NOT EXISTS post_reactions (
    post_id         UUID NOT NULL REFERENCES ephemeral_posts(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    emoji           VARCHAR(16) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (post_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_post_reactions_post
    ON post_reactions (post_id, created_at DESC);

CREATE TABLE IF NOT EXISTS post_comments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID NOT NULL REFERENCES ephemeral_posts(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content         VARCHAR(500) NOT NULL CHECK (char_length(btrim(content)) > 0),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_post_comments_post
    ON post_comments (post_id, created_at ASC);

-- ============================================================================
-- 8. TRIGGERS AUTOMÁTICOS DE INTEGRIDAD Y CONTADORES
-- ============================================================================

-- 8.1 Actualizar `updated_at` automáticamente en `users`
CREATE OR REPLACE FUNCTION fn_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_users_updated_at ON users;
CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION fn_set_updated_at();

-- 8.2 Mantener sincronizado `users.total_outfits_count` al añadir/borrar del archivo
CREATE OR REPLACE FUNCTION fn_sync_user_outfits_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE users SET total_outfits_count = total_outfits_count + 1 WHERE id = NEW.user_id;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE users SET total_outfits_count = GREATEST(0, total_outfits_count - 1) WHERE id = OLD.user_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_user_outfits_count ON outfit_calendar_archive;
CREATE TRIGGER trg_sync_user_outfits_count
    AFTER INSERT OR DELETE ON outfit_calendar_archive
    FOR EACH ROW
    EXECUTE FUNCTION fn_sync_user_outfits_count();

-- 8.3 Mantener sincronizado `ephemeral_posts.reactions_count`
CREATE OR REPLACE FUNCTION fn_sync_post_reactions_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE ephemeral_posts SET reactions_count = reactions_count + 1 WHERE id = NEW.post_id;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE ephemeral_posts SET reactions_count = GREATEST(0, reactions_count - 1) WHERE id = OLD.post_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_post_reactions_count ON post_reactions;
CREATE TRIGGER trg_sync_post_reactions_count
    AFTER INSERT OR DELETE ON post_reactions
    FOR EACH ROW
    EXECUTE FUNCTION fn_sync_post_reactions_count();

-- 8.4 Mantener sincronizado `ephemeral_posts.comments_count`
CREATE OR REPLACE FUNCTION fn_sync_post_comments_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE ephemeral_posts SET comments_count = comments_count + 1 WHERE id = NEW.post_id;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE ephemeral_posts SET comments_count = GREATEST(0, comments_count - 1) WHERE id = OLD.post_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_post_comments_count ON post_comments;
CREATE TRIGGER trg_sync_post_comments_count
    AFTER INSERT OR DELETE ON post_comments
    FOR EACH ROW
    EXECUTE FUNCTION fn_sync_post_comments_count();

-- ============================================================================
-- 9. VISTA SEGURA DE PUBLICACIONES ACTIVAS (< 24 HORAS)
-- Garantiza a nivel SQL que ningún post caducado se cuele en el feed público
-- ============================================================================
CREATE OR REPLACE VIEW v_active_feed_posts AS
SELECT
    ep.id AS post_id,
    ep.archive_id,
    ep.user_id,
    u.username,
    u.display_name,
    u.avatar_webp_path,
    ep.visibility,
    ep.caption,
    ep.is_late,
    ep.reactions_count,
    ep.comments_count,
    ep.published_at,
    ep.expires_at,
    GREATEST(0, EXTRACT(EPOCH FROM (ep.expires_at - NOW()))::INTEGER) AS remaining_seconds,
    oca.source_type,
    oca.style_tags,
    oca.dominant_colors,
    oca.width_px,
    oca.height_px
FROM ephemeral_posts ep
INNER JOIN users u ON u.id = ep.user_id
INNER JOIN outfit_calendar_archive oca ON oca.id = ep.archive_id
WHERE ep.expires_at > NOW()
  AND ep.visibility <> 'PRIVATE_ONLY';

COMMIT;
