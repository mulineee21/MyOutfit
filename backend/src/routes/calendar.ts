import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { pool } from '../db.js';

const calendarQuerySchema = z.object({
  year: z.coerce.number().int().min(2020).max(2100).optional(),
  month: z.coerce.number().int().min(1).max(12).optional(),
});

export async function calendarRoutes(app: FastifyInstance): Promise<void> {
  /**
   * GET /api/calendar/:userId?year=2026&month=11
   * Obtiene el histórico mensual del Calendario Privado de Outfits.
   * Estricto: solo el propietario de la cuenta puede consultar su bóveda privada.
   */
  app.get(
    '/api/calendar/:userId',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const { userId } = request.params as { userId: string };
      const requesterId = request.user.sub;

      if (userId !== requesterId) {
        return reply.code(403).send({
          error: 'FORBIDDEN_PRIVATE_ARCHIVE',
          message: 'El calendario histórico de outfits es estrictamente privado.',
        });
      }

      const parsed = calendarQuerySchema.safeParse(request.query);
      if (!parsed.success) {
        return reply.code(400).send({
          error: 'VALIDATION_ERROR',
          details: parsed.error.flatten(),
        });
      }

      const now = new Date();
      const year = parsed.data.year ?? now.getUTCFullYear();
      const month = parsed.data.month ?? now.getUTCMonth() + 1;

      const startDate = `${year}-${String(month).padStart(2, '0')}-01`;

      const { rows } = await pool.query<{
        id: string;
        worn_date: string;
        is_primary_of_day: boolean;
        width_px: number | null;
        height_px: number | null;
        source_type: string;
        caption: string;
        style_tags: string[];
        dominant_colors: string[];
        ai_metadata: Record<string, unknown>;
        created_at: Date;
      }>(
        `SELECT
           id,
           worn_date::TEXT AS worn_date,
           is_primary_of_day,
           width_px,
           height_px,
           source_type,
           caption,
           style_tags,
           dominant_colors,
           ai_metadata,
           created_at
         FROM outfit_calendar_archive
         WHERE user_id = $1
           AND worn_date >= $2::DATE
           AND worn_date < ($2::DATE + INTERVAL '1 month')::DATE
         ORDER BY worn_date ASC, is_primary_of_day DESC, created_at DESC`,
        [userId, startDate],
      );

      const days = rows.map((row) => ({
        archiveId: row.id,
        wornDate: row.worn_date,
        isPrimaryOfDay: row.is_primary_of_day,
        thumbnailUrl: `/api/media/outfits/${row.id}/thumb`,
        fullImageUrl: `/api/media/outfits/${row.id}/full`,
        widthPx: row.width_px,
        heightPx: row.height_px,
        sourceType: row.source_type,
        caption: row.caption,
        styleTags: row.style_tags,
        dominantColors: row.dominant_colors,
        aiMetadata: row.ai_metadata,
        createdAt: row.created_at.toISOString(),
      }));

      return reply.send({
        userId,
        year,
        month,
        totalLoggedDaysInMonth: new Set(days.map((d) => d.wornDate)).size,
        entries: days,
      });
    },
  );

  /**
   * GET /api/calendar/:userId/recap?year=2026
   * Endpoint preparado para el "Yearly Recap" anual (estilos más usados, colores top, meses más activos)
   */
  app.get(
    '/api/calendar/:userId/recap',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const { userId } = request.params as { userId: string };
      if (userId !== request.user.sub) {
        return reply.code(403).send({
          error: 'FORBIDDEN_PRIVATE_ARCHIVE',
          message: 'El resumen anual es privado.',
        });
      }

      const yearParam = Number((request.query as { year?: string }).year) || new Date().getUTCFullYear();
      const startOfYear = `${yearParam}-01-01`;
      const endOfYear = `${yearParam + 1}-01-01`;

      const [totalsRes, topStylesRes, topColorsRes, byMonthRes] = await Promise.all([
        pool.query<{ total_outfits: string; unique_days: string }>(
          `SELECT
             COUNT(*)::TEXT AS total_outfits,
             COUNT(DISTINCT worn_date)::TEXT AS unique_days
           FROM outfit_calendar_archive
           WHERE user_id = $1 AND worn_date >= $2::DATE AND worn_date < $3::DATE`,
          [userId, startOfYear, endOfYear],
        ),
        pool.query<{ tag: string; uses: string }>(
          `SELECT tag, COUNT(*)::TEXT AS uses
           FROM outfit_calendar_archive, UNNEST(style_tags) AS tag
           WHERE user_id = $1 AND worn_date >= $2::DATE AND worn_date < $3::DATE
           GROUP BY tag
           ORDER BY COUNT(*) DESC
           LIMIT 5`,
          [userId, startOfYear, endOfYear],
        ),
        pool.query<{ color: string; uses: string }>(
          `SELECT color, COUNT(*)::TEXT AS uses
           FROM outfit_calendar_archive, UNNEST(dominant_colors) AS color
           WHERE user_id = $1 AND worn_date >= $2::DATE AND worn_date < $3::DATE
           GROUP BY color
           ORDER BY COUNT(*) DESC
           LIMIT 5`,
          [userId, startOfYear, endOfYear],
        ),
        pool.query<{ month: number; count: string }>(
          `SELECT EXTRACT(MONTH FROM worn_date)::INT AS month, COUNT(DISTINCT worn_date)::TEXT AS count
           FROM outfit_calendar_archive
           WHERE user_id = $1 AND worn_date >= $2::DATE AND worn_date < $3::DATE
           GROUP BY month
           ORDER BY month ASC`,
          [userId, startOfYear, endOfYear],
        ),
      ]);

      return reply.send({
        year: yearParam,
        totalOutfits: Number(totalsRes.rows[0]?.total_outfits ?? 0),
        uniqueDaysLogged: Number(totalsRes.rows[0]?.unique_days ?? 0),
        topStyles: topStylesRes.rows.map((r) => ({ style: r.tag, count: Number(r.uses) })),
        topColors: topColorsRes.rows.map((r) => ({ color: r.color, count: Number(r.uses) })),
        daysByMonth: byMonthRes.rows.map((r) => ({ month: r.month, days: Number(r.count) })),
      });
    },
  );
}
