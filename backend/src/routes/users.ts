import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { pool } from '../db.js';
import {
  processAndSaveAvatar,
  safeDeleteMediaFile,
} from '../services/mediaService.js';
import { sendToUser } from '../services/wsHub.js';
import {
  computeUsernameCooldown,
  formatSpanishDate,
} from '../utils/usernameCooldown.js';

const followBodySchema = z.object({
  targetUserId: z.string().uuid(),
  action: z.enum(['FOLLOW', 'UNFOLLOW']).optional().default('FOLLOW'),
});

const followDecisionSchema = z.object({
  decision: z.enum(['ACCEPT', 'REJECT']),
});

const blockBodySchema = z.object({
  targetUserId: z.string().uuid(),
  action: z.enum(['BLOCK', 'UNBLOCK']).optional().default('BLOCK'),
});

const updateProfileSchema = z.object({
  displayName: z.string().trim().min(1).max(60).optional(),
  bio: z.string().trim().max(200).optional(),
  isPrivate: z.boolean().optional(),
});

const updateUsernameSchema = z.object({
  username: z
    .string()
    .trim()
    .transform((s) => s.replace(/^@+/, ''))
    .pipe(
      z
        .string()
        .min(3, 'El nombre de perfil debe tener entre 3 y 30 caracteres.')
        .max(30, 'El nombre de perfil debe tener entre 3 y 30 caracteres.')
        .regex(
          /^[a-zA-Z0-9_.]+$/,
          'Solo se permiten letras, números, puntos (.) y guiones bajos (_).',
        ),
    ),
});

export async function userRoutes(app: FastifyInstance): Promise<void> {
  /**
   * POST /api/users/follow
   * Seguir / Dejar de seguir (soporta solicitud `PENDING` automática si la cuenta destino es privada)
   */
  app.post(
    '/api/users/follow',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const parsed = followBodySchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.code(400).send({
          error: 'VALIDATION_ERROR',
          details: parsed.error.flatten(),
        });
      }

      const followerId = request.user.sub;
      const { targetUserId, action } = parsed.data;

      if (followerId === targetUserId) {
        return reply.code(400).send({
          error: 'SELF_FOLLOW_NOT_ALLOWED',
          message: 'No puedes seguirte a ti mismo.',
        });
      }

      if (action === 'UNFOLLOW') {
        await pool.query(
          `DELETE FROM follows WHERE follower_id = $1 AND following_id = $2`,
          [followerId, targetUserId],
        );
        return reply.send({
          targetUserId,
          status: 'NONE',
        });
      }

      const targetRes = await pool.query<{ id: string; is_private: boolean }>(
        `SELECT id, is_private FROM users WHERE id = $1`,
        [targetUserId],
      );

      const targetUser = targetRes.rows[0];
      if (!targetUser) {
        return reply.code(404).send({
          error: 'USER_NOT_FOUND',
          message: 'El usuario indicado no existe.',
        });
      }

      // Verificar si el destino bloqueó al solicitante
      const blockCheck = await pool.query(
        `SELECT 1 FROM follows
         WHERE follower_id = $1 AND following_id = $2 AND status = 'BLOCKED'`,
        [targetUserId, followerId],
      );
      if ((blockCheck.rowCount ?? 0) > 0) {
        return reply.code(403).send({ error: 'FORBIDDEN' });
      }

      const desiredStatus = targetUser.is_private ? 'PENDING' : 'ACCEPTED';
      const respondedAt = desiredStatus === 'ACCEPTED' ? new Date() : null;

      const upsertRes = await pool.query<{ status: string }>(
        `INSERT INTO follows (follower_id, following_id, status, responded_at)
         VALUES ($1, $2, $3::follow_status, $4)
         ON CONFLICT (follower_id, following_id)
         DO UPDATE SET
           status = CASE
             WHEN follows.status = 'ACCEPTED' THEN 'ACCEPTED'::follow_status
             ELSE EXCLUDED.status
           END,
           responded_at = CASE
             WHEN EXCLUDED.status = 'ACCEPTED' THEN NOW()
             ELSE follows.responded_at
           END
         RETURNING status::TEXT AS status`,
        [followerId, targetUserId, desiredStatus, respondedAt],
      );

      const finalStatus = upsertRes.rows[0]!.status;

      sendToUser(targetUserId, {
        type: 'FOLLOW_EVENT',
        payload: {
          followerId,
          followerUsername: request.user.username,
          status: finalStatus,
        },
      });

      return reply.send({
        targetUserId,
        status: finalStatus,
      });
    },
  );

  /**
   * POST /api/users/block
   * Bloqueo / Desbloqueo de usuarios (UGC Policy Google Play / Salvaguarda frente a Acoso DSA)
   */
  app.post(
    '/api/users/block',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const parsed = blockBodySchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.code(400).send({
          error: 'VALIDATION_ERROR',
          details: parsed.error.flatten(),
        });
      }

      const blockerId = request.user.sub;
      const { targetUserId, action } = parsed.data;

      if (blockerId === targetUserId) {
        return reply.code(400).send({
          error: 'CANNOT_BLOCK_SELF',
          message: 'No puedes bloquearte a ti mismo.',
        });
      }

      if (action === 'UNBLOCK') {
        await pool.query(
          `DELETE FROM user_blocks WHERE blocker_id = $1 AND blocked_id = $2`,
          [blockerId, targetUserId],
        );
        return reply.send({
          targetUserId,
          isBlocked: false,
          message: 'Usuario desbloqueado.',
        });
      }

      // Bloquear: insertar en user_blocks y eliminar cualquier relación de seguimiento mutuo
      await pool.query(
        `INSERT INTO user_blocks (blocker_id, blocked_id)
         VALUES ($1, $2)
         ON CONFLICT (blocker_id, blocked_id) DO NOTHING`,
        [blockerId, targetUserId],
      );

      await pool.query(
        `DELETE FROM follows
         WHERE (follower_id = $1 AND following_id = $2)
            OR (follower_id = $2 AND following_id = $1)`,
        [blockerId, targetUserId],
      );

      return reply.send({
        targetUserId,
        isBlocked: true,
        message: 'Usuario bloqueado correctamente. No volverá a aparecer en tu feed ni en tus búsquedas.',
      });
    },
  );

  /**
   * GET /api/users/blocked
   * Lista de usuarios bloqueados por el usuario autenticado
   */
  app.get(
    '/api/users/blocked',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const userId = request.user.sub;
      const { rows } = await pool.query<{
        id: string;
        username: string;
        display_name: string;
        avatar_webp_path: string | null;
        created_at: Date;
      }>(
        `SELECT
           u.id,
           u.username,
           u.display_name,
           u.avatar_webp_path,
           ub.created_at
         FROM user_blocks ub
         JOIN users u ON u.id = ub.blocked_id
         WHERE ub.blocker_id = $1
         ORDER BY ub.created_at DESC`,
        [userId],
      );

      return reply.send({
        blockedUsers: rows.map((r) => ({
          id: r.id,
          username: r.username,
          displayName: r.display_name,
          avatarUrl: r.avatar_webp_path ? `/api/media/avatars/${r.id}` : null,
          blockedAt: r.created_at.toISOString(),
        })),
      });
    },
  );

  /**
   * GET /api/users/follow-requests
   * Lista las solicitudes pendientes de aprobación (para cuentas privadas)
   */
  app.get(
    '/api/users/follow-requests',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const userId = request.user.sub;
      const { rows } = await pool.query<{
        follower_id: string;
        username: string;
        display_name: string;
        avatar_webp_path: string | null;
        created_at: Date;
      }>(
        `SELECT
           f.follower_id,
           u.username,
           u.display_name,
           u.avatar_webp_path,
           f.created_at
         FROM follows f
         INNER JOIN users u ON u.id = f.follower_id
         WHERE f.following_id = $1 AND f.status = 'PENDING'
         ORDER BY f.created_at DESC`,
        [userId],
      );

      return reply.send({
        requests: rows.map((r) => ({
          userId: r.follower_id,
          username: r.username,
          displayName: r.display_name,
          avatarUrl: r.avatar_webp_path ? `/api/media/avatars/${r.follower_id}` : null,
          requestedAt: r.created_at.toISOString(),
        })),
      });
    },
  );

  /**
   * GET /api/users/notifications
   * Historial de notificaciones del sistema (caducidad de outfits 24h, avisos de cuenta)
   */
  app.get(
    '/api/users/notifications',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const userId = request.user.sub;
      const { rows } = await pool.query<{
        id: string;
        type: string;
        title: string;
        message: string;
        created_at: Date;
        read_at: Date | null;
      }>(
        `SELECT id, type, title, message, created_at, read_at
         FROM user_notifications
         WHERE user_id = $1
         ORDER BY created_at DESC
         LIMIT 30`,
        [userId],
      );

      return reply.send({
        notifications: rows.map((n) => ({
          id: n.id,
          type: n.type,
          title: n.title,
          message: n.message,
          createdAt: n.created_at.toISOString(),
          readAt: n.read_at ? n.read_at.toISOString() : null,
        })),
      });
    },
  );

  /**
   * POST /api/users/follow-requests/:followerId
   * Aceptar o rechazar una solicitud pendiente
   */
  app.post(
    '/api/users/follow-requests/:followerId',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const { followerId } = request.params as { followerId: string };
      const parsed = followDecisionSchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.code(400).send({ error: 'VALIDATION_ERROR' });
      }

      const currentUserId = request.user.sub;

      if (parsed.data.decision === 'REJECT') {
        await pool.query(
          `DELETE FROM follows
           WHERE follower_id = $1 AND following_id = $2 AND status = 'PENDING'`,
          [followerId, currentUserId],
        );
        sendToUser(followerId, {
          type: 'FOLLOW_EVENT',
          payload: {
            targetUserId: currentUserId,
            targetUsername: request.user.username,
            status: 'NONE',
          },
        });
        return reply.send({ followerId, status: 'REJECTED' });
      }

      const updated = await pool.query(
        `UPDATE follows
         SET status = 'ACCEPTED', responded_at = NOW()
         WHERE follower_id = $1 AND following_id = $2 AND status = 'PENDING'`,
        [followerId, currentUserId],
      );

      if (updated.rowCount === 0) {
        return reply.code(404).send({ error: 'REQUEST_NOT_FOUND' });
      }

      sendToUser(followerId, {
        type: 'FOLLOW_EVENT',
        payload: {
          targetUserId: currentUserId,
          targetUsername: request.user.username,
          status: 'ACCEPTED',
        },
      });

      return reply.send({ followerId, status: 'ACCEPTED' });
    },
  );

  /**
   * GET /api/users/search?q=...
   * Buscador de amigos por username o nombre visible
   */
  app.get(
    '/api/users/search',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const q = String((request.query as { q?: string }).q ?? '').trim();
      if (q.length < 2) {
        return reply.send({ users: [] });
      }

      const viewerId = request.user.sub;
      const pattern = `%${q}%`;

      const { rows } = await pool.query<{
        id: string;
        username: string;
        display_name: string;
        bio: string;
        avatar_webp_path: string | null;
        is_private: boolean;
        total_outfits_count: number;
        follow_status: string | null;
      }>(
        `SELECT
           u.id,
           u.username,
           u.display_name,
           u.bio,
           u.avatar_webp_path,
           u.is_private,
           u.total_outfits_count,
           f.status::TEXT AS follow_status
         FROM users u
         LEFT JOIN follows f
           ON f.follower_id = $1 AND f.following_id = u.id
         WHERE u.id <> $1
           AND (u.username ILIKE $2 OR u.display_name ILIKE $2)
           AND NOT EXISTS (
             SELECT 1 FROM user_blocks ub
             WHERE (ub.blocker_id = $1 AND ub.blocked_id = u.id)
                OR (ub.blocker_id = u.id AND ub.blocked_id = $1)
           )
         ORDER BY u.username ASC
         LIMIT 25`,
        [viewerId, pattern],
      );

      return reply.send({
        users: rows.map((u) => ({
          id: u.id,
          username: u.username,
          displayName: u.display_name,
          bio: u.bio,
          avatarUrl: u.avatar_webp_path ? `/api/media/avatars/${u.id}` : null,
          isPrivate: u.is_private,
          totalOutfitsCount: u.total_outfits_count,
          followStatus: u.follow_status ?? 'NONE',
        })),
      });
    },
  );

  /**
   * GET /api/users/:userId/profile
   * Perfil detallado de cualquier usuario con métricas, estado de seguimiento y outfit activo (si procede).
   */
  app.get(
    '/api/users/:userId/profile',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const viewerId = request.user.sub;
      const { userId: targetUserId } = request.params as { userId: string };

      const userRes = await pool.query<{
        id: string;
        username: string;
        display_name: string;
        bio: string;
        avatar_webp_path: string | null;
        is_private: boolean;
        total_outfits_count: number;
        current_streak: number;
        followers_count: number;
        following_count: number;
        follow_status: string | null;
      }>(
        `SELECT
           u.id,
           u.username,
           u.display_name,
           u.bio,
           u.avatar_webp_path,
           u.is_private,
           u.total_outfits_count,
           u.current_streak,
           (SELECT COUNT(*) FROM follows WHERE following_id = u.id AND status = 'ACCEPTED')::INT AS followers_count,
           (SELECT COUNT(*) FROM follows WHERE follower_id = u.id AND status = 'ACCEPTED')::INT AS following_count,
           f.status::TEXT AS follow_status
         FROM users u
         LEFT JOIN follows f
           ON f.follower_id = $1 AND f.following_id = u.id
         WHERE u.id = $2`,
        [viewerId, targetUserId],
      );

      const targetUser = userRes.rows[0];
      if (!targetUser) {
        return reply.code(404).send({
          error: 'USER_NOT_FOUND',
          message: 'Usuario no encontrado.',
        });
      }

      const blockRes = await pool.query<{ is_blocked_by_me: boolean; is_blocked_by_them: boolean }>(
        `SELECT
           EXISTS (SELECT 1 FROM user_blocks WHERE blocker_id = $1 AND blocked_id = $2) AS is_blocked_by_me,
           EXISTS (SELECT 1 FROM user_blocks WHERE blocker_id = $2 AND blocked_id = $1) AS is_blocked_by_them`,
        [viewerId, targetUserId],
      );
      if (blockRes.rows[0]?.is_blocked_by_them) {
        return reply.code(403).send({
          error: 'USER_BLOCKED',
          message: 'No puedes ver el perfil de este usuario.',
        });
      }
      const isBlockedByMe = blockRes.rows[0]?.is_blocked_by_me ?? false;

      const isSelf = viewerId === targetUserId;
      const followStatus = isSelf
        ? 'SELF'
        : isBlockedByMe
          ? 'BLOCKED'
          : (targetUser.follow_status ?? 'NONE');
      const canViewPosts = !isBlockedByMe && (isSelf || !targetUser.is_private || followStatus === 'ACCEPTED');

      let activePost: any = null;
      if (canViewPosts) {
        const postRes = await pool.query<{
          post_id: string;
          archive_id: string;
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
          my_reaction: string | null;
        }>(
          `SELECT
             v.*,
             pr.emoji AS my_reaction
           FROM v_active_feed_posts v
           LEFT JOIN post_reactions pr
             ON pr.post_id = v.post_id AND pr.user_id = $1
           WHERE v.user_id = $2
           ORDER BY v.published_at DESC
           LIMIT 1`,
          [viewerId, targetUserId],
        );

        const p = postRes.rows[0];
        if (p) {
          activePost = {
            postId: p.post_id,
            archiveId: p.archive_id,
            author: {
              id: targetUser.id,
              username: targetUser.username,
              displayName: targetUser.display_name,
              avatarUrl: targetUser.avatar_webp_path
                ? `/api/media/avatars/${targetUser.id}`
                : null,
              isPrivate: targetUser.is_private,
              followStatus,
            },
            imageUrl: `/api/media/outfits/${p.archive_id}/full`,
            thumbnailUrl: `/api/media/outfits/${p.archive_id}/thumb`,
            widthPx: p.width_px,
            heightPx: p.height_px,
            caption: p.caption,
            visibility: p.visibility,
            sourceType: p.source_type,
            styleTags: p.style_tags,
            dominantColors: p.dominant_colors,
            isLate: p.is_late,
            reactionsCount: p.reactions_count,
            commentsCount: p.comments_count,
            myReaction: p.my_reaction,
            publishedAt: p.published_at.toISOString(),
            expiresAt: p.expires_at.toISOString(),
            remainingSeconds: p.remaining_seconds,
          };
        }
      }

      return reply.send({
        id: targetUser.id,
        username: targetUser.username,
        displayName: targetUser.display_name,
        bio: targetUser.bio,
        avatarUrl: targetUser.avatar_webp_path
          ? `/api/media/avatars/${targetUser.id}`
          : null,
        isPrivate: targetUser.is_private,
        totalOutfitsCount: targetUser.total_outfits_count,
        currentStreak: targetUser.current_streak,
        followersCount: targetUser.followers_count,
        followingCount: targetUser.following_count,
        followStatus,
        isBlockedByMe,
        canViewPosts,
        activePost,
      });
    },
  );

  /**
   * GET /api/users/me & PATCH /api/users/me & PATCH /api/users/me/username & POST /api/users/me/avatar
   */
  app.get(
    '/api/users/me',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const userId = request.user.sub;
      const { rows } = await pool.query<{
        id: string;
        username: string;
        email: string;
        display_name: string;
        bio: string;
        avatar_webp_path: string | null;
        is_private: boolean;
        total_outfits_count: number;
        current_streak: number;
        username_changed_at: Date | null;
        followers_count: string;
        following_count: string;
        pending_requests_count: string;
      }>(
        `SELECT
           u.*,
           (SELECT COUNT(*) FROM follows WHERE following_id = u.id AND status = 'ACCEPTED')::TEXT AS followers_count,
           (SELECT COUNT(*) FROM follows WHERE follower_id = u.id AND status = 'ACCEPTED')::TEXT AS following_count,
           (SELECT COUNT(*) FROM follows WHERE following_id = u.id AND status = 'PENDING')::TEXT AS pending_requests_count
         FROM users u
         WHERE u.id = $1`,
        [userId],
      );

      const u = rows[0];
      if (!u) return reply.code(404).send({ error: 'USER_NOT_FOUND' });

      const cooldown = computeUsernameCooldown(u.username_changed_at);

      return reply.send({
        id: u.id,
        username: u.username,
        email: u.email,
        displayName: u.display_name,
        bio: u.bio,
        avatarUrl: u.avatar_webp_path ? `/api/media/avatars/${u.id}` : null,
        isPrivate: u.is_private,
        totalOutfitsCount: u.total_outfits_count,
        currentStreak: u.current_streak,
        canChangeUsername: cooldown.canChangeUsername,
        nextUsernameChangeAt: cooldown.nextUsernameChangeAt,
        usernameCooldownDaysRemaining: cooldown.usernameCooldownDaysRemaining,
        followersCount: Number(u.followers_count),
        followingCount: Number(u.following_count),
        pendingRequestsCount: Number(u.pending_requests_count),
      });
    },
  );

  app.patch(
    '/api/users/me',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const parsed = updateProfileSchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.code(400).send({
          error: 'VALIDATION_ERROR',
          message: 'Datos de perfil inválidos.',
          details: parsed.error.flatten(),
        });
      }

      const userId = request.user.sub;
      const { displayName, bio, isPrivate } = parsed.data;

      const { rows } = await pool.query<{
        id: string;
        username: string;
        display_name: string;
        bio: string;
        is_private: boolean;
      }>(
        `UPDATE users
         SET
           display_name = COALESCE($2, display_name),
           bio = COALESCE($3, bio),
           is_private = COALESCE($4, is_private)
         WHERE id = $1
         RETURNING id, username, display_name, bio, is_private`,
        [userId, displayName ?? null, bio ?? null, isPrivate ?? null],
      );

      const updated = rows[0]!;
      return reply.send({
        id: updated.id,
        username: updated.username,
        displayName: updated.display_name,
        bio: updated.bio,
        isPrivate: updated.is_private,
      });
    },
  );

  /**
   * PATCH /api/users/me/username
   * Cambia el nombre de perfil de la cuenta (@username) con bloqueo estricto de 1 cambio cada 30 días (1 mes).
   */
  app.patch(
    '/api/users/me/username',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const parsed = updateUsernameSchema.safeParse(request.body);
      if (!parsed.success) {
        const firstIssue = parsed.error.issues[0]?.message;
        return reply.code(400).send({
          error: 'VALIDATION_ERROR',
          message:
            firstIssue ??
            'El nombre de perfil debe tener entre 3 y 30 caracteres (letras, números, . y _).',
        });
      }

      const userId = request.user.sub;
      const newUsername = parsed.data.username;

      const currentRes = await pool.query<{
        id: string;
        username: string;
        username_changed_at: Date | null;
      }>(
        `SELECT id, username, username_changed_at FROM users WHERE id = $1`,
        [userId],
      );

      const currentUser = currentRes.rows[0];
      if (!currentUser) {
        return reply.code(404).send({
          error: 'USER_NOT_FOUND',
          message: 'Usuario no encontrado.',
        });
      }

      if (currentUser.username === newUsername) {
        return reply.code(400).send({
          error: 'SAME_USERNAME',
          message: 'Ese ya es tu nombre de perfil actual.',
        });
      }

      const cooldown = computeUsernameCooldown(currentUser.username_changed_at);
      if (!cooldown.canChangeUsername && cooldown.nextUsernameChangeAt) {
        const formattedDate = formatSpanishDate(cooldown.nextUsernameChangeAt);
        return reply.code(429).send({
          error: 'USERNAME_COOLDOWN_ACTIVE',
          message: `Solo puedes cambiar tu nombre de perfil 1 vez al mes. Podrás volver a cambiarlo el ${formattedDate} (en ${cooldown.usernameCooldownDaysRemaining} días).`,
          nextUsernameChangeAt: cooldown.nextUsernameChangeAt,
          usernameCooldownDaysRemaining: cooldown.usernameCooldownDaysRemaining,
        });
      }

      const conflictRes = await pool.query(
        `SELECT 1 FROM users WHERE username = $1 AND id <> $2 LIMIT 1`,
        [newUsername, userId],
      );
      if ((conflictRes.rowCount ?? 0) > 0) {
        return reply.code(409).send({
          error: 'USERNAME_TAKEN',
          message: 'Ese nombre de perfil ya está ocupado. Prueba con otro distinto.',
        });
      }

      try {
        const updateRes = await pool.query<{
          id: string;
          username: string;
          display_name: string;
          username_changed_at: Date;
        }>(
          `UPDATE users
           SET
             username = $2,
             display_name = $2,
             username_changed_at = NOW()
           WHERE id = $1
           RETURNING id, username, display_name, username_changed_at`,
          [userId, newUsername],
        );

        const updated = updateRes.rows[0]!;
        const nextCooldown = computeUsernameCooldown(updated.username_changed_at);
        const freshAccessToken = app.jwt.sign({
          sub: updated.id,
          username: updated.username,
        });

        return reply.send({
          username: updated.username,
          displayName: updated.display_name,
          canChangeUsername: nextCooldown.canChangeUsername,
          nextUsernameChangeAt: nextCooldown.nextUsernameChangeAt,
          usernameCooldownDaysRemaining: nextCooldown.usernameCooldownDaysRemaining,
          accessToken: freshAccessToken,
        });
      } catch (err: unknown) {
        const pgErr = err as { code?: string };
        if (pgErr.code === '23505') {
          return reply.code(409).send({
            error: 'USERNAME_TAKEN',
            message: 'Ese nombre de perfil ya está ocupado. Prueba con otro distinto.',
          });
        }
        throw err;
      }
    },
  );

  app.post(
    '/api/users/me/avatar',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const file = await request.file();
      if (!file || !file.mimetype.startsWith('image/')) {
        return reply.code(400).send({
          error: 'INVALID_AVATAR',
          message: 'Adjunta una imagen válida en la petición multipart.',
        });
      }

      const userId = request.user.sub;
      const buffer = await file.toBuffer();
      const newAvatarRelPath = await processAndSaveAvatar(buffer, userId);

      const prevRes = await pool.query<{ avatar_webp_path: string | null }>(
        `SELECT avatar_webp_path FROM users WHERE id = $1`,
        [userId],
      );
      const oldPath = prevRes.rows[0]?.avatar_webp_path;

      await pool.query(`UPDATE users SET avatar_webp_path = $2 WHERE id = $1`, [
        userId,
        newAvatarRelPath,
      ]);

      if (oldPath && oldPath !== newAvatarRelPath) {
        await safeDeleteMediaFile(oldPath);
      }

      return reply.send({
        avatarUrl: `/api/media/avatars/${userId}?t=${Date.now()}`,
      });
    },
  );

  /**
   * GET /api/users/me/privacy-export
   * Art. 15 (Derecho de Acceso) y Art. 20 (Derecho a la Portabilidad de los Datos) del RGPD (UE 2016/679).
   * Devuelve en formato JSON estructurado y legible por máquina la totalidad de datos personales
   * asociados al usuario en el servidor.
   */
  app.get(
    '/api/users/me/privacy-export',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const userId = request.user.sub;

      const userRes = await pool.query<{
        id: string;
        username: string;
        email: string;
        email_verified: boolean;
        google_sub: string | null;
        display_name: string;
        bio: string;
        is_private: boolean;
        total_outfits_count: number;
        current_streak: number;
        username_changed_at: Date | null;
        privacy_accepted_at: Date | null;
        privacy_policy_version: string | null;
        created_at: Date;
        updated_at: Date;
      }>(
        `SELECT
           id, username, email, email_verified, google_sub, display_name, bio,
           is_private, total_outfits_count, current_streak, username_changed_at,
           privacy_accepted_at, privacy_policy_version, created_at, updated_at
         FROM users
         WHERE id = $1`,
        [userId],
      );

      const u = userRes.rows[0];
      if (!u) {
        return reply.code(404).send({
          error: 'USER_NOT_FOUND',
          message: 'Usuario no encontrado.',
        });
      }

      const [calendarRes, postsRes, followingRes, followersRes, commentsRes, reactionsRes] =
        await Promise.all([
          pool.query(
            `SELECT id, worn_date::TEXT AS worn_date, is_primary_of_day, source_type::TEXT AS source_type,
                    caption, style_tags, dominant_colors, created_at
             FROM outfit_calendar_archive
             WHERE user_id = $1
             ORDER BY worn_date DESC`,
            [userId],
          ),
          pool.query(
            `SELECT id, archive_id, visibility::TEXT AS visibility, caption, is_late,
                    reactions_count, comments_count, published_at, expires_at
             FROM ephemeral_posts
             WHERE user_id = $1
             ORDER BY published_at DESC`,
            [userId],
          ),
          pool.query(
            `SELECT u.username, f.status::TEXT AS status, f.created_at
             FROM follows f
             INNER JOIN users u ON u.id = f.following_id
             WHERE f.follower_id = $1
             ORDER BY f.created_at DESC`,
            [userId],
          ),
          pool.query(
            `SELECT u.username, f.status::TEXT AS status, f.created_at
             FROM follows f
             INNER JOIN users u ON u.id = f.follower_id
             WHERE f.following_id = $1
             ORDER BY f.created_at DESC`,
            [userId],
          ),
          pool.query(
            `SELECT id, post_id, content, created_at
             FROM post_comments
             WHERE user_id = $1
             ORDER BY created_at DESC`,
            [userId],
          ),
          pool.query(
            `SELECT post_id, emoji, created_at
             FROM post_reactions
             WHERE user_id = $1
             ORDER BY created_at DESC`,
            [userId],
          ),
        ]);

      return reply.send({
        gdprExportVersion: '1.0-RGPD-LOPDGDD',
        exportedAt: new Date().toISOString(),
        legalBasis: 'Art. 15 y Art. 20 Reglamento (UE) 2016/679 (RGPD) y Ley Orgánica 3/2018 (LOPDGDD)',
        account: {
          id: u.id,
          username: u.username,
          email: u.email,
          emailVerified: u.email_verified,
          linkedWithGoogle: Boolean(u.google_sub),
          displayName: u.display_name,
          bio: u.bio,
          isPrivate: u.is_private,
          totalOutfitsCount: u.total_outfits_count,
          currentStreak: u.current_streak,
          usernameChangedAt: u.username_changed_at?.toISOString() ?? null,
          privacyAcceptedAt: u.privacy_accepted_at?.toISOString() ?? null,
          privacyPolicyVersion: u.privacy_policy_version ?? '2026.1-ES',
          createdAt: u.created_at.toISOString(),
          updatedAt: u.updated_at.toISOString(),
        },
        calendarArchive: calendarRes.rows,
        activeEphemeralPosts: postsRes.rows,
        following: followingRes.rows,
        followers: followersRes.rows,
        commentsAuthored: commentsRes.rows,
        reactionsGiven: reactionsRes.rows,
      });
    },
  );

  /**
   * DELETE /api/users/me
   * Art. 17 RGPD — Derecho de Supresión ("Derecho al Olvido").
   * Elimina de forma inmediata e irreversible la cuenta del usuario, todas sus relaciones,
   * publicaciones, comentarios, tokens y TODOS los archivos fotográficos WebP del disco.
   */
  app.delete(
    '/api/users/me',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const userId = request.user.sub;

      // 1. Recopilar todas las rutas físicas de imágenes antes de borrar en cascada
      const [userMediaRes, archiveMediaRes] = await Promise.all([
        pool.query<{ avatar_webp_path: string | null }>(
          `SELECT avatar_webp_path FROM users WHERE id = $1`,
          [userId],
        ),
        pool.query<{ image_webp_path: string; thumbnail_webp_path: string }>(
          `SELECT image_webp_path, thumbnail_webp_path
           FROM outfit_calendar_archive
           WHERE user_id = $1`,
          [userId],
        ),
      ]);

      if (userMediaRes.rowCount === 0) {
        return reply.code(404).send({
          error: 'USER_NOT_FOUND',
          message: 'La cuenta ya no existe.',
        });
      }

      const filesToPurge: string[] = [];
      const avatarPath = userMediaRes.rows[0]?.avatar_webp_path;
      if (avatarPath) filesToPurge.push(avatarPath);
      for (const row of archiveMediaRes.rows) {
        if (row.image_webp_path) filesToPurge.push(row.image_webp_path);
        if (row.thumbnail_webp_path) filesToPurge.push(row.thumbnail_webp_path);
      }

      // 2. Borrado íntegro en PostgreSQL (ON DELETE CASCADE limpia todas las tablas hijas)
      await pool.query(`DELETE FROM users WHERE id = $1`, [userId]);

      // 3. Borrado físico inmediato de todos los archivos WebP en el servidor Debian
      await Promise.all(filesToPurge.map((relPath) => safeDeleteMediaFile(relPath)));

      return reply.send({
        ok: true,
        deletedMediaFilesCount: filesToPurge.length,
        message:
          'Tu cuenta y todos tus datos personales y fotografías del servidor han sido eliminados definitivamente (Art. 17 RGPD).',
      });
    },
  );
}
