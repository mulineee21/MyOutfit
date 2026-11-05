import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { config } from '../config.js';
import { pool, withTransaction } from '../db.js';
import {
  processAndSaveOutfitImage,
  safeDeleteMediaFile,
} from '../services/mediaService.js';
import { broadcastToFollowers, sendToUser } from '../services/wsHub.js';

const postMetadataSchema = z.object({
  caption: z.string().trim().max(280).optional().default(''),
  wornDate: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'Formato esperado YYYY-MM-DD')
    .optional(),
  sourceType: z
    .enum(['REAL_CAMERA', 'AI_GENERATED', 'CLOSET_COLLAGE'])
    .optional()
    .default('REAL_CAMERA'),
  visibility: z
    .enum(['FOLLOWERS', 'PUBLIC', 'PRIVATE_ONLY'])
    .optional()
    .default('FOLLOWERS'),
  isLate: z
    .union([z.boolean(), z.enum(['true', 'false']).transform((v) => v === 'true')])
    .optional()
    .default(false),
  styleTags: z.array(z.string().trim().max(40)).max(10).optional().default([]),
  dominantColors: z.array(z.string().trim().max(30)).max(8).optional().default([]),
  aiMetadata: z.record(z.string(), z.unknown()).optional().default({}),
});

const feedQuerySchema = z.object({
  limit: z.coerce.number().int().min(1).max(50).optional().default(20),
  before: z.string().datetime().optional(),
  discover: z
    .enum(['true', 'false'])
    .transform((v) => v === 'true')
    .optional()
    .default(false),
});

const reactionBodySchema = z.object({
  emoji: z.string().trim().min(1).max(16),
});

const commentBodySchema = z.object({
  content: z.string().trim().min(1).max(500),
});

const reportBodySchema = z.object({
  reason: z.enum([
    'INAPPROPRIATE',
    'HARASSMENT',
    'PRIVACY',
    'SPAM',
    'COPYRIGHT',
    'OTHER',
  ]),
  details: z.string().trim().max(500).optional().default(''),
});

function parseJsonOrCsvList(raw: unknown): string[] {
  if (typeof raw !== 'string' || !raw.trim()) return [];
  const trimmed = raw.trim();
  if (trimmed.startsWith('[')) {
    try {
      const arr = JSON.parse(trimmed);
      if (Array.isArray(arr)) return arr.map((x) => String(x).trim()).filter(Boolean);
    } catch {
      return [];
    }
  }
  return trimmed
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

export async function postRoutes(app: FastifyInstance): Promise<void> {
  /**
   * POST /api/posts
   * Subida multipart estilo BeReal:
   * 1) Comprime la imagen a WebP (full + miniatura 400px).
   * 2) Guarda permanentemente en `outfit_calendar_archive` (Calendario Privado + Yearly Recap).
   * 3) Publica en `ephemeral_posts` con caducidad exacta en 24h (`expires_at`).
   */
  app.post(
    '/api/posts',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const userId = request.user.sub;
      const parts = request.parts();

      let imageBuffer: Buffer | null = null;
      const rawFields: Record<string, string> = {};

      for await (const part of parts) {
        if (part.type === 'file' && part.fieldname === 'image') {
          if (!part.mimetype.startsWith('image/')) {
            return reply.code(400).send({
              error: 'INVALID_MEDIA_TYPE',
              message: 'El archivo subido debe ser una imagen.',
            });
          }
          imageBuffer = await part.toBuffer();
        } else if (part.type === 'field') {
          rawFields[part.fieldname] = String(part.value ?? '');
        }
      }

      if (!imageBuffer || imageBuffer.length === 0) {
        return reply.code(400).send({
          error: 'MISSING_IMAGE',
          message: 'Debes adjuntar el campo multipart "image" con la foto de tu outfit.',
        });
      }

      let parsedAiMetadata: Record<string, unknown> = {};
      if (rawFields.aiMetadata) {
        try {
          parsedAiMetadata = JSON.parse(rawFields.aiMetadata) as Record<string, unknown>;
        } catch {
          return reply.code(400).send({
            error: 'INVALID_AI_METADATA',
            message: 'aiMetadata debe ser un JSON válido.',
          });
        }
      }

      const metaParse = postMetadataSchema.safeParse({
        caption: rawFields.caption,
        wornDate: rawFields.wornDate || undefined,
        sourceType: rawFields.sourceType || undefined,
        visibility: rawFields.visibility || undefined,
        isLate: rawFields.isLate || undefined,
        styleTags: parseJsonOrCsvList(rawFields.styleTags),
        dominantColors: parseJsonOrCsvList(rawFields.dominantColors),
        aiMetadata: parsedAiMetadata,
      });

      if (!metaParse.success) {
        return reply.code(400).send({
          error: 'VALIDATION_ERROR',
          details: metaParse.error.flatten(),
        });
      }

      const meta = metaParse.data;
      const wornDate = meta.wornDate ?? new Date().toISOString().slice(0, 10);

      // 1. Procesar y comprimir a WebP en el volumen Docker
      const processedMedia = await processAndSaveOutfitImage(imageBuffer);

      try {
        const created = await withTransaction(async (client) => {
          // Si ya existía un outfit principal para este día, pasa a secundario sin perderse del archivo
          await client.query(
            `UPDATE outfit_calendar_archive
             SET is_primary_of_day = FALSE
             WHERE user_id = $1 AND worn_date = $2::DATE AND is_primary_of_day = TRUE`,
            [userId, wornDate],
          );

          // 2. Insertar en la bóveda permanente (Calendario Privado)
          const archiveRes = await client.query<{
            id: string;
            worn_date: string;
            created_at: Date;
          }>(
            `INSERT INTO outfit_calendar_archive (
               user_id, worn_date, is_primary_of_day,
               image_webp_path, thumbnail_webp_path, width_px, height_px,
               source_type, caption, style_tags, dominant_colors, ai_metadata
             )
             VALUES ($1, $2::DATE, TRUE, $3, $4, $5, $6, $7, $8, $9, $10, $11::jsonb)
             RETURNING id, worn_date::TEXT AS worn_date, created_at`,
            [
              userId,
              wornDate,
              processedMedia.imageWebpPath,
              processedMedia.thumbnailWebpPath,
              processedMedia.widthPx,
              processedMedia.heightPx,
              meta.sourceType,
              meta.caption,
              meta.styleTags,
              meta.dominantColors,
              JSON.stringify(meta.aiMetadata),
            ],
          );
          const archive = archiveRes.rows[0]!;

          // 3. Insertar en `ephemeral_posts` con ventana exacta de 24h
          const postRes = await client.query<{
            id: string;
            published_at: Date;
            expires_at: Date;
          }>(
            `INSERT INTO ephemeral_posts (
               archive_id, user_id, visibility, caption, is_late, expires_at
             )
             VALUES (
               $1, $2, $3, $4, $5,
               NOW() + ($6 || ' hours')::INTERVAL
             )
             RETURNING id, published_at, expires_at`,
            [
              archive.id,
              userId,
              meta.visibility,
              meta.caption,
              meta.isLate,
              config.EPHEMERAL_TTL_HOURS,
            ],
          );
          const post = postRes.rows[0]!;

          // 4. Actualizar racha (`current_streak`) si registró outfit ayer
          await client.query(
            `UPDATE users u
             SET current_streak = CASE
               WHEN EXISTS (
                 SELECT 1 FROM outfit_calendar_archive oca
                 WHERE oca.user_id = $1
                   AND oca.worn_date = ($2::DATE - INTERVAL '1 day')::DATE
               ) THEN GREATEST(u.current_streak + 1, 1)
               ELSE 1
             END
             WHERE u.id = $1`,
            [userId, wornDate],
          );

          return { archive, post };
        });

        if (meta.visibility !== 'PRIVATE_ONLY') {
          void broadcastToFollowers(userId, {
            type: 'NEW_POST',
            payload: {
              postId: created.post.id,
              authorId: userId,
              username: request.user.username,
              publishedAt: created.post.published_at.toISOString(),
              expiresAt: created.post.expires_at.toISOString(),
            },
          });
        }

        const remainingSeconds = Math.max(
          0,
          Math.floor((created.post.expires_at.getTime() - Date.now()) / 1000),
        );

        return reply.code(201).send({
          postId: created.post.id,
          archiveId: created.archive.id,
          wornDate: created.archive.worn_date,
          visibility: meta.visibility,
          caption: meta.caption,
          sourceType: meta.sourceType,
          styleTags: meta.styleTags,
          dominantColors: meta.dominantColors,
          imageUrl: `/api/media/outfits/${created.archive.id}/full`,
          thumbnailUrl: `/api/media/outfits/${created.archive.id}/thumb`,
          widthPx: processedMedia.widthPx,
          heightPx: processedMedia.heightPx,
          publishedAt: created.post.published_at.toISOString(),
          expiresAt: created.post.expires_at.toISOString(),
          remainingSeconds,
        });
      } catch (err) {
        // Limpieza en caso de fallo de base de datos para no dejar archivos huérfanos
        await Promise.all([
          safeDeleteMediaFile(processedMedia.imageWebpPath),
          safeDeleteMediaFile(processedMedia.thumbnailWebpPath),
        ]);
        throw err;
      }
    },
  );

  /**
   * GET /api/feed
   * Devuelve exclusivamente publicaciones con menos de 24h (`expires_at > NOW()`)
   * de los usuarios seguidos (`status = 'ACCEPTED'`) + las propias publicaciones activas.
   */
  app.get(
    '/api/feed',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const parsed = feedQuerySchema.safeParse(request.query);
      if (!parsed.success) {
        return reply.code(400).send({
          error: 'VALIDATION_ERROR',
          details: parsed.error.flatten(),
        });
      }

      const viewerId = request.user.sub;
      const { limit, before, discover } = parsed.data;

      const { rows } = await pool.query<{
        post_id: string;
        archive_id: string;
        user_id: string;
        username: string;
        display_name: string;
        avatar_webp_path: string | null;
        visibility: string;
        caption: string;
        is_late: boolean;
        reactions_count: number;
        comments_count: number;
        published_at: Date;
        expires_at: Date;
        remaining_seconds: number;
        source_type: string;
        style_tags: string[];
        dominant_colors: string[];
        width_px: number | null;
        height_px: number | null;
        is_private: boolean;
        follow_status: string | null;
        my_reaction: string | null;
      }>(
        `SELECT
           v.*,
           u.is_private,
           f.status::TEXT AS follow_status,
           pr.emoji AS my_reaction
         FROM v_active_feed_posts v
         JOIN users u ON u.id = v.user_id
         LEFT JOIN follows f
           ON f.follower_id = $1 AND f.following_id = v.user_id
         LEFT JOIN post_reactions pr
           ON pr.post_id = v.post_id AND pr.user_id = $1
         WHERE (
           CASE
             -- Modo Explorar Público: ÚNICAMENTE publicaciones de cuentas públicas (is_private = FALSE)
             WHEN $4::BOOLEAN = TRUE THEN
               u.is_private = FALSE AND v.visibility <> 'PRIVATE_ONLY'
             -- Modo Mis Seguidos: mis propios posts y los de cuentas que sigo con solicitud aceptada
             ELSE
               v.user_id = $1
               OR (f.status = 'ACCEPTED' AND v.visibility <> 'PRIVATE_ONLY')
           END
         )
         AND NOT EXISTS (
           SELECT 1 FROM post_reports rep
           WHERE rep.post_id = v.post_id AND rep.reporter_id = $1
         )
         AND NOT EXISTS (
           SELECT 1 FROM user_blocks ub
           WHERE (ub.blocker_id = $1 AND ub.blocked_id = v.user_id)
              OR (ub.blocker_id = v.user_id AND ub.blocked_id = $1)
         )
         AND ($2::TIMESTAMPTZ IS NULL OR v.published_at < $2::TIMESTAMPTZ)
         ORDER BY v.published_at DESC
         LIMIT $3`,
        [viewerId, before ?? null, limit, discover],
      );

      // Comprobar si el usuario actual ya subió su BeReal de Outfit hoy
      const todayPostedRes = await pool.query<{ has_posted_today: boolean }>(
        `SELECT EXISTS (
           SELECT 1 FROM outfit_calendar_archive
           WHERE user_id = $1 AND worn_date = CURRENT_DATE
         ) AS has_posted_today`,
        [viewerId],
      );

      const items = rows.map((r) => ({
        postId: r.post_id,
        archiveId: r.archive_id,
        author: {
          id: r.user_id,
          username: r.username,
          displayName: r.display_name,
          avatarUrl: r.avatar_webp_path ? `/api/media/avatars/${r.user_id}` : null,
          isPrivate: r.is_private,
          followStatus: r.follow_status ?? 'NONE',
        },
        imageUrl: `/api/media/outfits/${r.archive_id}/full`,
        thumbnailUrl: `/api/media/outfits/${r.archive_id}/thumb`,
        widthPx: r.width_px,
        heightPx: r.height_px,
        caption: r.caption,
        visibility: r.visibility,
        sourceType: r.source_type,
        styleTags: r.style_tags,
        dominantColors: r.dominant_colors,
        isLate: r.is_late,
        reactionsCount: r.reactions_count,
        commentsCount: r.comments_count,
        myReaction: r.my_reaction,
        publishedAt: r.published_at.toISOString(),
        expiresAt: r.expires_at.toISOString(),
        remainingSeconds: r.remaining_seconds,
      }));

      const lastItem = items[items.length - 1];
      return reply.send({
        hasPostedToday: todayPostedRes.rows[0]?.has_posted_today ?? false,
        nextCursor: items.length === limit && lastItem ? lastItem.publishedAt : null,
        items,
      });
    },
  );

  /**
   * POST /api/posts/:postId/reactions
   * Añade o actualiza reacción rápida en un outfit activo (< 24h)
   */
  app.post(
    '/api/posts/:postId/reactions',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const { postId } = request.params as { postId: string };
      const parsed = reactionBodySchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.code(400).send({ error: 'VALIDATION_ERROR' });
      }

      const viewerId = request.user.sub;

      // Verificar que el post siga activo (< 24h) y sea visible para quien reacciona
      const postCheck = await pool.query<{ user_id: string }>(
        `SELECT ep.user_id
         FROM ephemeral_posts ep
         WHERE ep.id = $1
           AND ep.expires_at > NOW()
           AND (
             ep.user_id = $2
             OR ep.visibility = 'PUBLIC'
             OR (
               ep.visibility = 'FOLLOWERS'
               AND EXISTS (
                 SELECT 1 FROM follows f
                 WHERE f.follower_id = $2 AND f.following_id = ep.user_id AND f.status = 'ACCEPTED'
               )
             )
           )`,
        [postId, viewerId],
      );

      const postOwner = postCheck.rows[0];
      if (!postOwner) {
        return reply.code(404).send({
          error: 'POST_NOT_ACTIVE',
          message: 'La publicación no existe o ya superó las 24 horas.',
        });
      }

      await pool.query(
        `INSERT INTO post_reactions (post_id, user_id, emoji)
         VALUES ($1, $2, $3)
         ON CONFLICT (post_id, user_id)
         DO UPDATE SET emoji = EXCLUDED.emoji, created_at = NOW()`,
        [postId, viewerId, parsed.data.emoji],
      );

      if (postOwner.user_id !== viewerId) {
        sendToUser(postOwner.user_id, {
          type: 'NEW_REACTION',
          payload: {
            postId,
            fromUserId: viewerId,
            fromUsername: request.user.username,
            emoji: parsed.data.emoji,
          },
        });
      }

      return reply.code(200).send({ ok: true, emoji: parsed.data.emoji });
    },
  );

  /**
   * DELETE /api/posts/:postId/reactions
   */
  app.delete(
    '/api/posts/:postId/reactions',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const { postId } = request.params as { postId: string };
      await pool.query(
        `DELETE FROM post_reactions WHERE post_id = $1 AND user_id = $2`,
        [postId, request.user.sub],
      );
      return reply.code(204).send();
    },
  );

  /**
   * GET /api/posts/:postId/comments & POST /api/posts/:postId/comments
   */
  app.get(
    '/api/posts/:postId/comments',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const { postId } = request.params as { postId: string };
      const viewerId = request.user.sub;

      const accessCheck = await pool.query(
        `SELECT 1 FROM ephemeral_posts ep
         WHERE ep.id = $1
           AND ep.expires_at > NOW()
           AND (
             ep.user_id = $2
             OR ep.visibility = 'PUBLIC'
             OR (
               ep.visibility = 'FOLLOWERS'
               AND EXISTS (
                 SELECT 1 FROM follows f
                 WHERE f.follower_id = $2 AND f.following_id = ep.user_id AND f.status = 'ACCEPTED'
               )
             )
           )`,
        [postId, viewerId],
      );

      if (accessCheck.rowCount === 0) {
        return reply.code(404).send({
          error: 'POST_NOT_ACTIVE',
          message: 'La publicación no está disponible o ya expiró.',
        });
      }

      const { rows } = await pool.query<{
        id: string;
        user_id: string;
        username: string;
        display_name: string;
        avatar_webp_path: string | null;
        content: string;
        created_at: Date;
      }>(
        `SELECT
           c.id,
           c.user_id,
           u.username,
           u.display_name,
           u.avatar_webp_path,
           c.content,
           c.created_at
         FROM post_comments c
         INNER JOIN users u ON u.id = c.user_id
         WHERE c.post_id = $1
         ORDER BY c.created_at ASC`,
        [postId],
      );

      return reply.send({
        comments: rows.map((c) => ({
          id: c.id,
          author: {
            id: c.user_id,
            username: c.username,
            displayName: c.display_name,
            avatarUrl: c.avatar_webp_path ? `/api/media/avatars/${c.user_id}` : null,
          },
          content: c.content,
          createdAt: c.created_at.toISOString(),
        })),
      });
    },
  );

  app.post(
    '/api/posts/:postId/comments',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const { postId } = request.params as { postId: string };
      const parsed = commentBodySchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.code(400).send({ error: 'VALIDATION_ERROR' });
      }

      const viewerId = request.user.sub;

      const postCheck = await pool.query<{ user_id: string }>(
        `SELECT ep.user_id
         FROM ephemeral_posts ep
         WHERE ep.id = $1
           AND ep.expires_at > NOW()
           AND (
             ep.user_id = $2
             OR ep.visibility = 'PUBLIC'
             OR (
               ep.visibility = 'FOLLOWERS'
               AND EXISTS (
                 SELECT 1 FROM follows f
                 WHERE f.follower_id = $2 AND f.following_id = ep.user_id AND f.status = 'ACCEPTED'
               )
             )
           )`,
        [postId, viewerId],
      );

      const postOwner = postCheck.rows[0];
      if (!postOwner) {
        return reply.code(404).send({
          error: 'POST_NOT_ACTIVE',
          message: 'La publicación ya superó las 24 horas o no tienes acceso.',
        });
      }

      const insertRes = await pool.query<{ id: string; created_at: Date }>(
        `INSERT INTO post_comments (post_id, user_id, content)
         VALUES ($1, $2, $3)
         RETURNING id, created_at`,
        [postId, viewerId, parsed.data.content],
      );

      const created = insertRes.rows[0]!;

      if (postOwner.user_id !== viewerId) {
        sendToUser(postOwner.user_id, {
          type: 'NEW_COMMENT',
          payload: {
            postId,
            commentId: created.id,
            fromUserId: viewerId,
            fromUsername: request.user.username,
            content: parsed.data.content,
          },
        });
      }

      return reply.code(201).send({
        id: created.id,
        content: parsed.data.content,
        createdAt: created.created_at.toISOString(),
      });
    },
  );

  /**
   * DELETE /api/posts/:postId
   * Derecho de supresión (Art. 17 RGPD - "Derecho al Olvido"):
   * Permite al autor eliminar de forma inmediata y definitiva su foto tanto
   * del feed efímero como de su almacenamiento físico en disco y archivo privado.
   */
  app.delete(
    '/api/posts/:postId',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const { postId } = request.params as { postId: string };
      const purgeArchive = (request.query as { purgeArchive?: string })?.purgeArchive !== 'false';
      const userId = request.user.sub;

      const postRes = await pool.query<{ user_id: string; archive_id: string }>(
        `SELECT user_id, archive_id FROM ephemeral_posts WHERE id = $1`,
        [postId],
      );

      const post = postRes.rows[0];
      if (!post) {
        return reply.code(404).send({
          error: 'POST_NOT_FOUND',
          message: 'La publicación no existe o ya ha sido eliminada.',
        });
      }

      if (post.user_id !== userId) {
        return reply.code(403).send({
          error: 'FORBIDDEN',
          message: 'No tienes autorización para eliminar esta publicación.',
        });
      }

      if (purgeArchive) {
        const archRes = await pool.query<{
          image_webp_path: string;
          thumbnail_webp_path: string;
        }>(
          `SELECT image_webp_path, thumbnail_webp_path FROM outfit_calendar_archive WHERE id = $1`,
          [post.archive_id],
        );

        // Al eliminar de outfit_calendar_archive, el ON DELETE CASCADE elimina de ephemeral_posts,
        // post_reactions, post_comments y post_reports
        await pool.query(`DELETE FROM outfit_calendar_archive WHERE id = $1`, [post.archive_id]);

        if (archRes.rows[0]) {
          await safeDeleteMediaFile(archRes.rows[0].image_webp_path);
          await safeDeleteMediaFile(archRes.rows[0].thumbnail_webp_path);
        }
      } else {
        await pool.query(`DELETE FROM ephemeral_posts WHERE id = $1`, [postId]);
      }

      return reply.code(200).send({
        ok: true,
        message: 'Publicación y fotografía eliminadas permanentemente conforme al Art. 17 RGPD.',
      });
    },
  );

  /**
   * POST /api/posts/:postId/report
   * Mecanismo de notificación y acción ("Notice and Action") según el Art. 16
   * del Reglamento de Servicios Digitales (DSA - Reglamento UE 2022/2065).
   */
  app.post(
    '/api/posts/:postId/report',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const { postId } = request.params as { postId: string };
      const parsed = reportBodySchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.code(400).send({
          error: 'VALIDATION_ERROR',
          message: 'Motivo de denuncia no válido.',
          details: parsed.error.flatten(),
        });
      }

      const reporterId = request.user.sub;

      const postCheck = await pool.query<{ user_id: string }>(
        `SELECT user_id FROM ephemeral_posts WHERE id = $1`,
        [postId],
      );

      const post = postCheck.rows[0];
      if (!post) {
        return reply.code(404).send({
          error: 'POST_NOT_FOUND',
          message: 'La publicación no existe o ya ha sido retirada.',
        });
      }

      if (post.user_id === reporterId) {
        return reply.code(400).send({
          error: 'CANNOT_REPORT_SELF',
          message: 'No puedes denunciar tu propia publicación. Usa la opción de supresión para retirarla.',
        });
      }

      await pool.query(
        `INSERT INTO post_reports (post_id, reporter_id, reason, details)
         VALUES ($1, $2, $3, $4)
         ON CONFLICT (post_id, reporter_id)
         DO UPDATE SET reason = EXCLUDED.reason, details = EXCLUDED.details, created_at = NOW()`,
        [postId, reporterId, parsed.data.reason, parsed.data.details],
      );

      // Si la publicación acumula >= 3 denuncias de usuarios distintos, ocultarla preventivamente de discover público
      await pool.query(
        `UPDATE ephemeral_posts
         SET visibility = 'FOLLOWERS'
         WHERE id = $1
           AND visibility = 'PUBLIC'
           AND (SELECT COUNT(*) FROM post_reports WHERE post_id = $1) >= 3`,
        [postId],
      );

      return reply.code(200).send({
        ok: true,
        message: 'Denuncia registrada conforme a la Ley de Servicios Digitales (DSA). Gracias por ayudarnos a mantener una comunidad segura.',
      });
    },
  );
}
