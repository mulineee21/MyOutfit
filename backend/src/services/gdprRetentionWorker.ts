import type { FastifyBaseLogger } from 'fastify';
import { pool } from '../db.js';
import { sendToUser } from './wsHub.js';

const RETENTION_SWEEP_INTERVAL_MS = 60 * 1000; // Cada 1 minuto

/**
 * Anonimiza direcciones IPv4/IPv6 para los registros técnicos conforme al criterio del
 * TJUE (Asunto C-582/14 Breyer) y la AEPD (Art. 5.1.c y Art. 32 RGPD - Seudonimización/Minimización).
 */
export function anonymizeIpAddress(rawIp: string | undefined | null): string {
  if (!rawIp) return '0.0.0.0';
  const ip = rawIp.trim();

  // IPv4 mapeada en IPv6 (::ffff:192.168.1.10 -> ::ffff:192.168.1.0)
  if (ip.startsWith('::ffff:')) {
    const v4 = ip.slice(7);
    const parts = v4.split('.');
    if (parts.length === 4) {
      return `::ffff:${parts[0]}.${parts[1]}.${parts[2]}.0`;
    }
  }

  // IPv4 estándar (85.190.123.116 -> 85.190.123.0)
  const v4Parts = ip.split('.');
  if (v4Parts.length === 4) {
    return `${v4Parts[0]}.${v4Parts[1]}.${v4Parts[2]}.0`;
  }

  // IPv6 (conserva únicamente los primeros 48 bits de red, truncando los 80 bits de interfaz)
  if (ip.includes(':')) {
    const groups = ip.split(':').slice(0, 3);
    return `${groups.join(':')}::`;
  }

  return '0.0.0.0';
}

/**
 * Ejecuta la purga periódica de datos efímeros y credenciales caducadas
 * en cumplimiento del Principio de Limitación del Plazo de Conservación (Art. 5.1.e RGPD).
 */
export async function runGdprRetentionSweep(logger?: FastifyBaseLogger): Promise<void> {
  try {
    // 1. Eliminar publicaciones efímeras de >24h y notificar al usuario
    const expiredPosts = await pool.query<{
      id: string;
      user_id: string;
      caption: string;
    }>(
      `DELETE FROM ephemeral_posts
       WHERE expires_at <= NOW()
       RETURNING id, user_id, caption`,
    );

    if ((expiredPosts.rowCount ?? 0) > 0) {
      for (const row of expiredPosts.rows) {
        await pool.query(
          `INSERT INTO user_notifications (user_id, type, title, message)
           VALUES ($1, 'POST_EXPIRED', '⏳ Tu outfit de 24h ha expirado',
                   'Tu outfit diario ha cumplido sus 24 horas y se ha guardado en tu Calendario Privado. ¡Es momento de compartir tu look de hoy!')`,
          [row.user_id],
        );

        sendToUser(row.user_id, {
          type: 'POST_EXPIRED',
          payload: {
            postId: row.id,
            title: '⏳ Tu outfit de 24h ha expirado',
            message:
              'Tu outfit diario ha cumplido sus 24 horas y se ha guardado en tu Calendario Privado. ¡Es momento de compartir tu look de hoy!',
          },
        });
      }
    }

    // 2. Eliminar códigos OTP de recuperación caducados o ya consumidos (>1h)
    const expiredResetCodes = await pool.query(
      `DELETE FROM password_reset_tokens
       WHERE expires_at <= NOW()
          OR (used_at IS NOT NULL AND used_at <= NOW() - INTERVAL '1 hour')`,
    );

    // 3. Eliminar refresh tokens expirados o revocados hace más de 7 días
    const expiredRefreshTokens = await pool.query(
      `DELETE FROM refresh_tokens
       WHERE expires_at <= NOW()
          OR (revoked_at IS NOT NULL AND revoked_at <= NOW() - INTERVAL '7 days')`,
    );

    const totalPurged =
      (expiredPosts.rowCount ?? 0) +
      (expiredResetCodes.rowCount ?? 0) +
      (expiredRefreshTokens.rowCount ?? 0);

    if (totalPurged > 0) {
      logger?.info(
        {
          purgedEphemeralPosts: expiredPosts.rowCount ?? 0,
          purgedResetCodes: expiredResetCodes.rowCount ?? 0,
          purgedRefreshTokens: expiredRefreshTokens.rowCount ?? 0,
        },
        'Purga automática RGPD completada',
      );
    }
  } catch (err) {
    logger?.error({ err }, 'Error en tarea periódica de retención RGPD');
  }
}

export function startGdprRetentionWorker(logger: FastifyBaseLogger): NodeJS.Timeout {
  void runGdprRetentionSweep(logger);
  const timer = setInterval(() => {
    void runGdprRetentionSweep(logger);
  }, RETENTION_SWEEP_INTERVAL_MS);
  timer.unref();
  return timer;
}
