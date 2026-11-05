import { randomInt } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { config } from '../config.js';
import { pool, withTransaction } from '../db.js';
import { sendPasswordResetEmail } from '../services/emailService.js';
import {
  downloadAndSaveGoogleAvatar,
  verifyGoogleIdToken,
} from '../services/googleAuthService.js';
import {
  generateOpaqueToken,
  hashPassword,
  sha256Hex,
  verifyPassword,
} from '../utils/password.js';
import { computeUsernameCooldown } from '../utils/usernameCooldown.js';
import { CURRENT_PRIVACY_POLICY_VERSION } from './legal.js';

const registerBodySchema = z.object({
  username: z
    .string()
    .trim()
    .min(3)
    .max(30)
    .regex(/^[a-zA-Z0-9_.]+$/, 'Solo letras, números, puntos y guiones bajos'),
  email: z.string().trim().email().max(120),
  password: z.string().min(8, 'La contraseña debe tener mínimo 8 caracteres').max(128),
  displayName: z.string().trim().min(1).max(60),
  bio: z.string().trim().max(200).optional().default(''),
  isPrivate: z.boolean().optional().default(true),
  acceptedPrivacyPolicy: z.boolean().optional().default(false),
  confirmedAge14Plus: z.boolean().optional().default(false),
  deviceInfo: z.string().trim().max(120).optional(),
});

const loginBodySchema = z.object({
  identifier: z.string().trim().min(1, 'Introduce tu correo o usuario'),
  password: z.string().min(1),
  deviceInfo: z.string().trim().max(120).optional(),
});

const googleAuthSchema = z.object({
  idToken: z.string().min(20),
  deviceInfo: z.string().trim().max(120).optional(),
});

const forgotPasswordSchema = z.object({
  email: z.string().trim().email().max(120),
});

const resetPasswordSchema = z.object({
  email: z.string().trim().email().max(120),
  code: z.string().trim().regex(/^\d{6}$/, 'El código debe tener 6 dígitos'),
  newPassword: z.string().min(8, 'La nueva contraseña debe tener mínimo 8 caracteres').max(128),
});

const refreshBodySchema = z.object({
  refreshToken: z.string().min(20),
  deviceInfo: z.string().trim().max(120).optional(),
});

interface DbUserRow {
  id: string;
  username: string;
  email: string;
  password_hash: string | null;
  google_sub: string | null;
  email_verified: boolean;
  display_name: string;
  bio: string;
  avatar_webp_path: string | null;
  is_private: boolean;
  total_outfits_count: number;
  current_streak: number;
  username_changed_at: Date | null;
  created_at: Date;
}

function mapUserDto(u: DbUserRow) {
  const cooldown = computeUsernameCooldown(u.username_changed_at);
  return {
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
    createdAt: u.created_at.toISOString(),
  };
}

function sanitizeBaseUsername(emailOrName: string): string {
  const localPart = emailOrName.split('@')[0] ?? 'user';
  const cleaned = localPart
    .toLowerCase()
    .replace(/[^a-z0-9_.]/g, '_')
    .replace(/_+/g, '_')
    .replace(/^[._]+|[._]+$/g, '')
    .slice(0, 22);
  return cleaned.length >= 3 ? cleaned : `user_${cleaned}`;
}

export async function authRoutes(app: FastifyInstance): Promise<void> {
  /**
   * POST /api/auth/register
   * Registro tradicional con Correo Electrónico + Contraseña
   */
  app.post('/api/auth/register', async (request, reply) => {
    const parsed = registerBodySchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({
        error: 'VALIDATION_ERROR',
        message: 'Revisa los campos del formulario.',
        details: parsed.error.flatten(),
      });
    }

    const {
      username,
      email,
      password,
      displayName,
      bio,
      isPrivate,
      acceptedPrivacyPolicy,
      confirmedAge14Plus,
      deviceInfo,
    } = parsed.data;

    if (!acceptedPrivacyPolicy || !confirmedAge14Plus) {
      return reply.code(400).send({
        error: 'GDPR_CONSENT_REQUIRED',
        message:
          'Debes aceptar la Política de Privacidad y confirmar que tienes al menos 14 años (Art. 7 LOPDGDD).',
      });
    }

    const passwordHash = await hashPassword(password);
    const rawRefreshToken = generateOpaqueToken();
    const refreshTokenHash = sha256Hex(rawRefreshToken);

    try {
      const user = await withTransaction(async (client) => {
        const insertRes = await client.query<DbUserRow>(
          `INSERT INTO users (
             username, email, password_hash, display_name, bio, is_private,
             privacy_accepted_at, privacy_policy_version
           )
           VALUES ($1, $2, $3, $4, $5, $6, NOW(), $7)
           RETURNING *`,
          [
            username,
            email.toLowerCase(),
            passwordHash,
            displayName,
            bio,
            isPrivate,
            CURRENT_PRIVACY_POLICY_VERSION,
          ],
        );
        const createdUser = insertRes.rows[0]!;

        await client.query(
          `INSERT INTO refresh_tokens (user_id, token_hash, device_info, expires_at)
           VALUES ($1, $2, $3, NOW() + ($4 || ' days')::INTERVAL)`,
          [createdUser.id, refreshTokenHash, deviceInfo ?? null, config.JWT_REFRESH_EXPIRES_IN_DAYS],
        );

        return createdUser;
      });

      const accessToken = app.jwt.sign({
        sub: user.id,
        username: user.username,
      });

      return reply.code(201).send({
        accessToken,
        refreshToken: rawRefreshToken,
        user: mapUserDto(user),
      });
    } catch (err: unknown) {
      const pgErr = err as { code?: string };
      if (pgErr.code === '23505') {
        return reply.code(409).send({
          error: 'CONFLICT',
          message: 'El nombre de usuario o el correo electrónico ya están registrados.',
        });
      }
      throw err;
    }
  });

  /**
   * POST /api/auth/login
   * Inicio de sesión con Correo o @usuario + Contraseña
   */
  app.post('/api/auth/login', async (request, reply) => {
    const parsed = loginBodySchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({
        error: 'VALIDATION_ERROR',
        details: parsed.error.flatten(),
      });
    }

    const { identifier, password, deviceInfo } = parsed.data;
    const { rows } = await pool.query<DbUserRow>(
      `SELECT * FROM users WHERE username = $1 OR email = $1 LIMIT 1`,
      [identifier],
    );

    const user = rows[0];
    if (!user) {
      return reply.code(401).send({
        error: 'INVALID_CREDENTIALS',
        message: 'Correo, usuario o contraseña incorrectos.',
      });
    }

    if (!user.password_hash) {
      return reply.code(401).send({
        error: 'GOOGLE_ACCOUNT_ONLY',
        message: 'Esta cuenta se creó con Google. Usa "Continuar con Google" o pulsa "¿Olvidaste tu contraseña?" para crear una clave.',
      });
    }

    if (!(await verifyPassword(password, user.password_hash))) {
      return reply.code(401).send({
        error: 'INVALID_CREDENTIALS',
        message: 'Correo, usuario o contraseña incorrectos.',
      });
    }

    const rawRefreshToken = generateOpaqueToken();
    const refreshTokenHash = sha256Hex(rawRefreshToken);

    await pool.query(
      `INSERT INTO refresh_tokens (user_id, token_hash, device_info, expires_at)
       VALUES ($1, $2, $3, NOW() + ($4 || ' days')::INTERVAL)`,
      [user.id, refreshTokenHash, deviceInfo ?? null, config.JWT_REFRESH_EXPIRES_IN_DAYS],
    );

    const accessToken = app.jwt.sign({
      sub: user.id,
      username: user.username,
    });

    return reply.send({
      accessToken,
      refreshToken: rawRefreshToken,
      user: mapUserDto(user),
    });
  });

  /**
   * POST /api/auth/google
   * Inicio de sesión y registro en 1 toque con Google (Credential Manager Android)
   */
  app.post('/api/auth/google', async (request, reply) => {
    const parsed = googleAuthSchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({ error: 'VALIDATION_ERROR' });
    }

    let googleProfile;
    try {
      googleProfile = await verifyGoogleIdToken(parsed.data.idToken);
    } catch (err) {
      return reply.code(401).send({
        error: 'INVALID_GOOGLE_TOKEN',
        message: err instanceof Error ? err.message : 'No se pudo verificar la cuenta de Google.',
      });
    }

    const rawRefreshToken = generateOpaqueToken();
    const refreshTokenHash = sha256Hex(rawRefreshToken);

    const { user, isNewUser } = await withTransaction(async (client) => {
      // 1. Buscar por google_sub o por correo electrónico existente (vinculación automática segura)
      const existingRes = await client.query<DbUserRow>(
        `SELECT * FROM users WHERE google_sub = $1 OR email = $2 LIMIT 1`,
        [googleProfile.googleSub, googleProfile.email],
      );

      let targetUser = existingRes.rows[0];
      let createdNew = false;

      if (targetUser) {
        if (!targetUser.google_sub) {
          const updatedRes = await client.query<DbUserRow>(
            `UPDATE users
             SET google_sub = $2,
                 email_verified = TRUE
             WHERE id = $1
             RETURNING *`,
            [targetUser.id, googleProfile.googleSub],
          );
          targetUser = updatedRes.rows[0]!;
        }
      } else {
        // 2. Generar un @username único automáticamente a partir de su correo/nombre
        const baseUsername = sanitizeBaseUsername(googleProfile.email);
        let candidate = baseUsername;

        for (let attempt = 0; attempt < 10; attempt++) {
          const check = await client.query(
            `SELECT 1 FROM users WHERE username = $1`,
            [candidate],
          );
          if (check.rowCount === 0) break;
          candidate = `${baseUsername}${randomInt(100, 9999)}`;
        }

        const insertRes = await client.query<DbUserRow>(
          `INSERT INTO users (
             username, email, google_sub, email_verified, display_name,
             is_private, privacy_accepted_at, privacy_policy_version
           )
           VALUES ($1, $2, $3, $4, $5, TRUE, NOW(), $6)
           RETURNING *`,
          [
            candidate,
            googleProfile.email,
            googleProfile.googleSub,
            googleProfile.emailVerified,
            googleProfile.displayName,
            CURRENT_PRIVACY_POLICY_VERSION,
          ],
        );
        targetUser = insertRes.rows[0]!;
        createdNew = true;
      }

      await client.query(
        `INSERT INTO refresh_tokens (user_id, token_hash, device_info, expires_at)
         VALUES ($1, $2, $3, NOW() + ($4 || ' days')::INTERVAL)`,
        [
          targetUser.id,
          refreshTokenHash,
          parsed.data.deviceInfo ?? 'Android-Google',
          config.JWT_REFRESH_EXPIRES_IN_DAYS,
        ],
      );

      return { user: targetUser, isNewUser: createdNew };
    });

    // Si aún no tiene foto de perfil, importar avatar de Google comprimido en WebP
    let finalUser = user;
    if (!user.avatar_webp_path && googleProfile.pictureUrl) {
      const savedAvatar = await downloadAndSaveGoogleAvatar(googleProfile.pictureUrl, user.id);
      if (savedAvatar) {
        const avatarRes = await pool.query<DbUserRow>(
          `UPDATE users SET avatar_webp_path = $2 WHERE id = $1 RETURNING *`,
          [user.id, savedAvatar],
        );
        if (avatarRes.rows[0]) finalUser = avatarRes.rows[0];
      }
    }

    const accessToken = app.jwt.sign({
      sub: finalUser.id,
      username: finalUser.username,
    });

    return reply.code(isNewUser ? 201 : 200).send({
      accessToken,
      refreshToken: rawRefreshToken,
      isNewUser,
      user: mapUserDto(finalUser),
    });
  });

  /**
   * POST /api/auth/forgot-password
   * Envía un código numérico de 6 dígitos al correo electrónico para recuperar la contraseña
   */
  app.post(
    '/api/auth/forgot-password',
    {
      config: {
        rateLimit: {
          max: 5,
          timeWindow: '15 minutes',
        },
      },
    },
    async (request, reply) => {
      const parsed = forgotPasswordSchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.code(400).send({ error: 'VALIDATION_ERROR' });
      }

      const email = parsed.data.email.toLowerCase();
      const { rows } = await pool.query<DbUserRow>(
        `SELECT * FROM users WHERE email = $1 LIMIT 1`,
        [email],
      );

      const user = rows[0];
      if (user) {
        const sixDigitCode = String(randomInt(100_000, 1_000_000));
        const codeHash = sha256Hex(sixDigitCode);

        await withTransaction(async (client) => {
          // Invalidar códigos previos pendientes de este usuario
          await client.query(
            `UPDATE password_reset_tokens
             SET used_at = NOW()
             WHERE user_id = $1 AND used_at IS NULL`,
            [user.id],
          );

          await client.query(
            `INSERT INTO password_reset_tokens (user_id, code_hash, expires_at)
             VALUES ($1, $2, NOW() + INTERVAL '15 minutes')`,
            [user.id, codeHash],
          );
        });

        await sendPasswordResetEmail(user.email, user.display_name, sixDigitCode);
      }

      // Respuesta uniforme para proteger la privacidad de qué emails están registrados
      return reply.send({
        ok: true,
        message: 'Si el correo está registrado, recibirás un código de 6 dígitos en unos segundos.',
      });
    },
  );

  /**
   * POST /api/auth/reset-password
   * Valida el código de 6 dígitos recibido por correo y establece la nueva contraseña
   */
  app.post(
    '/api/auth/reset-password',
    {
      config: {
        rateLimit: {
          max: 10,
          timeWindow: '15 minutes',
        },
      },
    },
    async (request, reply) => {
      const parsed = resetPasswordSchema.safeParse(request.body);
      if (!parsed.success) {
        return reply.code(400).send({
          error: 'VALIDATION_ERROR',
          details: parsed.error.flatten(),
        });
      }

      const { email, code, newPassword } = parsed.data;
      const submittedHash = sha256Hex(code);

      const userRes = await pool.query<{ id: string }>(
        `SELECT id FROM users WHERE email = $1 LIMIT 1`,
        [email.toLowerCase()],
      );
      const user = userRes.rows[0];
      if (!user) {
        return reply.code(400).send({
          error: 'INVALID_RESET_CODE',
          message: 'El código es inválido o ha expirado.',
        });
      }

      const tokenRes = await pool.query<{
        id: string;
        code_hash: string;
        attempts: number;
      }>(
        `SELECT id, code_hash, attempts
         FROM password_reset_tokens
         WHERE user_id = $1
           AND used_at IS NULL
           AND expires_at > NOW()
         ORDER BY created_at DESC
         LIMIT 1`,
        [user.id],
      );

      const resetRow = tokenRes.rows[0];
      if (!resetRow || resetRow.attempts >= 5) {
        return reply.code(400).send({
          error: 'INVALID_RESET_CODE',
          message: 'El código ha expirado o superó el número máximo de intentos. Solicita uno nuevo.',
        });
      }

      if (resetRow.code_hash !== submittedHash) {
        await pool.query(
          `UPDATE password_reset_tokens SET attempts = attempts + 1 WHERE id = $1`,
          [resetRow.id],
        );
        return reply.code(400).send({
          error: 'INVALID_RESET_CODE',
          message: 'El código introducido no es correcto.',
        });
      }

      const newPasswordHash = await hashPassword(newPassword);

      await withTransaction(async (client) => {
        await client.query(
          `UPDATE users SET password_hash = $2, email_verified = TRUE WHERE id = $1`,
          [user.id, newPasswordHash],
        );
        await client.query(
          `UPDATE password_reset_tokens SET used_at = NOW() WHERE id = $1`,
          [resetRow.id],
        );
        // Por seguridad, cerrar sesiones antiguas abiertas en otros dispositivos
        await client.query(
          `UPDATE refresh_tokens SET revoked_at = NOW() WHERE user_id = $1 AND revoked_at IS NULL`,
          [user.id],
        );
      });

      return reply.send({
        ok: true,
        message: 'Contraseña actualizada correctamente. Ya puedes iniciar sesión.',
      });
    },
  );

  /**
   * POST /api/auth/refresh
   */
  app.post('/api/auth/refresh', async (request, reply) => {
    const parsed = refreshBodySchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({ error: 'VALIDATION_ERROR' });
    }

    const oldHash = sha256Hex(parsed.data.refreshToken);
    const newRawRefreshToken = generateOpaqueToken();
    const newHash = sha256Hex(newRawRefreshToken);

    const result = await withTransaction(async (client) => {
      const revoked = await client.query<{ user_id: string }>(
        `UPDATE refresh_tokens
         SET revoked_at = NOW()
         WHERE token_hash = $1
           AND revoked_at IS NULL
           AND expires_at > NOW()
         RETURNING user_id`,
        [oldHash],
      );

      const tokenRow = revoked.rows[0];
      if (!tokenRow) return null;

      const userRes = await client.query<{ id: string; username: string }>(
        `SELECT id, username FROM users WHERE id = $1`,
        [tokenRow.user_id],
      );
      const user = userRes.rows[0];
      if (!user) return null;

      await client.query(
        `INSERT INTO refresh_tokens (user_id, token_hash, device_info, expires_at)
         VALUES ($1, $2, $3, NOW() + ($4 || ' days')::INTERVAL)`,
        [
          user.id,
          newHash,
          parsed.data.deviceInfo ?? null,
          config.JWT_REFRESH_EXPIRES_IN_DAYS,
        ],
      );

      return user;
    });

    if (!result) {
      return reply.code(401).send({
        error: 'INVALID_REFRESH_TOKEN',
        message: 'La sesión ha expirado, inicia sesión de nuevo.',
      });
    }

    const accessToken = app.jwt.sign({
      sub: result.id,
      username: result.username,
    });

    return reply.send({
      accessToken,
      refreshToken: newRawRefreshToken,
    });
  });

  /**
   * POST /api/auth/logout
   */
  app.post('/api/auth/logout', async (request, reply) => {
    const body = request.body as { refreshToken?: string } | undefined;
    if (body?.refreshToken) {
      await pool.query(
        `UPDATE refresh_tokens SET revoked_at = NOW() WHERE token_hash = $1 AND revoked_at IS NULL`,
        [sha256Hex(body.refreshToken)],
      );
    }
    return reply.code(204).send();
  });
}
