import { createReadStream } from 'node:fs';
import fs from 'node:fs/promises';
import type { FastifyInstance } from 'fastify';
import { pool } from '../db.js';
import { resolveSafeMediaPath } from '../services/mediaService.js';

export async function mediaRoutes(app: FastifyInstance): Promise<void> {
  /**
   * GET /api/media/outfits/:archiveId/:variant
   * Guardián criptográfico de imágenes:
   * - Si eres el propietario -> Acceso PERMANENTE (para tu Calendario Privado / Yearly Recap).
   * - Si eres otro usuario -> Solo si `expires_at > NOW()` (< 24h) y tienes permiso de visibilidad/seguimiento.
   * - Si pasaron 24h para terceros -> Devuelve `410 Gone`.
   */
  app.get(
    '/api/media/outfits/:archiveId/:variant',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const { archiveId, variant } = request.params as {
        archiveId: string;
        variant: string;
      };

      if (variant !== 'full' && variant !== 'thumb') {
        return reply.code(400).send({ error: 'INVALID_VARIANT' });
      }

      const requesterId = request.user.sub;

      const { rows } = await pool.query<{
        owner_id: string;
        owner_is_private: boolean;
        image_webp_path: string;
        thumbnail_webp_path: string;
        visibility: string | null;
        expires_at: Date | null;
        is_active: boolean;
        remaining_seconds: number;
      }>(
        `SELECT
           oca.user_id AS owner_id,
           u.is_private AS owner_is_private,
           oca.image_webp_path,
           oca.thumbnail_webp_path,
           ep.visibility::TEXT AS visibility,
           ep.expires_at,
           COALESCE(ep.expires_at > NOW(), FALSE) AS is_active,
           GREATEST(0, COALESCE(EXTRACT(EPOCH FROM (ep.expires_at - NOW()))::INT, 0)) AS remaining_seconds
         FROM outfit_calendar_archive oca
         JOIN users u ON u.id = oca.user_id
         LEFT JOIN ephemeral_posts ep ON ep.archive_id = oca.id
         WHERE oca.id = $1`,
        [archiveId],
      );

      const record = rows[0];
      if (!record) {
        return reply.code(404).send({ error: 'MEDIA_NOT_FOUND' });
      }

      const isOwner = record.owner_id === requesterId;

      if (!isOwner) {
        if (!record.is_active) {
          return reply.code(410).send({
            error: 'EPHEMERAL_POST_EXPIRED',
            message: 'Este outfit efímero ha superado su ventana de visibilidad de 24 horas.',
          });
        }

        if (record.visibility === 'PRIVATE_ONLY') {
          return reply.code(403).send({ error: 'FORBIDDEN' });
        }

        // Si la cuenta propietaria es privada y no es pública, se requiere seguimiento aceptado
        if (record.owner_is_private && record.visibility !== 'PUBLIC') {
          const followCheck = await pool.query(
            `SELECT 1 FROM follows
             WHERE follower_id = $1 AND following_id = $2 AND status = 'ACCEPTED'`,
            [requesterId, record.owner_id],
          );
          if (followCheck.rowCount === 0) {
            return reply.code(403).send({
              error: 'NOT_FOLLOWING',
              message: 'Debes seguir a este usuario privado para ver su outfit diario.',
            });
          }
        }
      }

      const targetRelPath =
        variant === 'thumb' ? record.thumbnail_webp_path : record.image_webp_path;
      const absPath = resolveSafeMediaPath(targetRelPath);

      try {
        const stat = await fs.stat(absPath);
        const cacheControl = isOwner
          ? 'private, max-age=31536000, immutable'
          : `private, max-age=${Math.min(record.remaining_seconds, 3600)}`;

        reply
          .header('Content-Type', 'image/webp')
          .header('Content-Length', stat.size)
          .header('Cache-Control', cacheControl);

        return reply.send(createReadStream(absPath));
      } catch {
        return reply.code(404).send({ error: 'FILE_MISSING_ON_VOLUME' });
      }
    },
  );

  /**
   * GET /api/media/avatars/:userId
   * Sirve el avatar WebP comprimido del usuario
   */
  app.get('/api/media/avatars/:userId', async (request, reply) => {
    const { userId } = request.params as { userId: string };
    const { rows } = await pool.query<{ avatar_webp_path: string | null }>(
      `SELECT avatar_webp_path FROM users WHERE id = $1`,
      [userId],
    );

    const relPath = rows[0]?.avatar_webp_path;
    if (!relPath) {
      return reply.code(404).send({ error: 'AVATAR_NOT_SET' });
    }

    try {
      const absPath = resolveSafeMediaPath(relPath);
      const stat = await fs.stat(absPath);
      reply
        .header('Content-Type', 'image/webp')
        .header('Content-Length', stat.size)
        .header('Cache-Control', 'public, max-age=86400');
      return reply.send(createReadStream(absPath));
    } catch {
      return reply.code(404).send({ error: 'AVATAR_FILE_MISSING' });
    }
  });
}
