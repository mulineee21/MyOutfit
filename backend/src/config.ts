import { z } from 'zod';

const envSchema = z.object({
  NODE_ENV: z.enum(['development', 'production', 'test']).default('development'),
  PORT: z.coerce.number().int().positive().default(3000),
  HOST: z.string().default('0.0.0.0'),
  DATABASE_URL: z.string().min(1, 'DATABASE_URL es obligatorio'),
  JWT_ACCESS_SECRET: z.string().min(16, 'JWT_ACCESS_SECRET debe tener al menos 16 caracteres'),
  JWT_REFRESH_SECRET: z.string().min(16, 'JWT_REFRESH_SECRET debe tener al menos 16 caracteres'),
  JWT_ACCESS_EXPIRES_IN: z.string().default('15m'),
  JWT_REFRESH_EXPIRES_IN_DAYS: z.coerce.number().int().positive().default(30),
  MEDIA_ROOT: z.string().default('/var/lib/myoutfit/media'),
  MAX_UPLOAD_SIZE_MB: z.coerce.number().int().positive().max(50).default(15),
  WEBP_QUALITY: z.coerce.number().int().min(40).max(100).default(82),
  EPHEMERAL_TTL_HOURS: z.coerce.number().int().min(1).max(168).default(24),

  // Google Sign-In (Web Client ID configurado en Google Cloud Console)
  GOOGLE_WEB_CLIENT_ID: z.string().optional().default(''),

  // Configuración SMTP para correos de recuperación de contraseña y bienvenida
  SMTP_HOST: z.string().optional().default(''),
  SMTP_PORT: z.coerce.number().int().positive().default(587),
  SMTP_SECURE: z
    .enum(['true', 'false'])
    .transform((v) => v === 'true')
    .optional()
    .default(false),
  SMTP_USER: z.string().optional().default(''),
  SMTP_PASS: z.string().optional().default(''),
  SMTP_FROM: z.string().optional().default('MyOutfit <no-reply@myoutfit.app>'),
});

export const config = envSchema.parse(process.env);
