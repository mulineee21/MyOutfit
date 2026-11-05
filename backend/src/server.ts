import fastifyCors from '@fastify/cors';
import fastifyMultipart from '@fastify/multipart';
import fastifyRateLimit from '@fastify/rate-limit';
import fastifyWebsocket from '@fastify/websocket';
import Fastify from 'fastify';
import { config } from './config.js';
import { pool } from './db.js';
import { registerAuthPlugin } from './plugins/auth.js';
import { authRoutes } from './routes/auth.js';
import { calendarRoutes } from './routes/calendar.js';
import { legalRoutes } from './routes/legal.js';
import { mediaRoutes } from './routes/media.js';
import { postRoutes } from './routes/posts.js';
import { userRoutes } from './routes/users.js';
import {
  anonymizeIpAddress,
  startGdprRetentionWorker,
} from './services/gdprRetentionWorker.js';
import { ensureMediaDirectories } from './services/mediaService.js';
import { registerWsRoutes } from './services/wsHub.js';

async function buildServer() {
  const app = Fastify({
    logger: {
      level: config.NODE_ENV === 'production' ? 'info' : 'debug',
      // RGPD Art. 5.1.c & Art. 32: Prohibición de volcar credenciales y anonimización de IP en logs
      redact: {
        paths: [
          'req.headers.authorization',
          'req.headers.cookie',
          'res.headers["set-cookie"]',
        ],
        remove: true,
      },
      serializers: {
        req(request) {
          return {
            method: request.method,
            url: request.url,
            host: request.hostname,
            remoteAddress: anonymizeIpAddress(request.ip),
          };
        },
      },
    },
    trustProxy: true,
  });

  await app.register(fastifyCors, {
    origin: true,
    credentials: true,
  });

  await app.register(fastifyRateLimit, {
    max: 120,
    timeWindow: '1 minute',
  });

  await app.register(fastifyMultipart, {
    limits: {
      files: 1,
      fields: 15,
      fileSize: config.MAX_UPLOAD_SIZE_MB * 1024 * 1024,
    },
  });

  await app.register(fastifyWebsocket);
  await registerAuthPlugin(app);

  // Healthcheck usado por Docker Compose y Caddy
  app.get('/api/health', async (_request, reply) => {
    try {
      await pool.query('SELECT 1');
      return reply.send({
        status: 'ok',
        service: 'myoutfit-social-api',
        gdprCompliant: true,
        timestamp: new Date().toISOString(),
      });
    } catch {
      return reply.code(503).send({ status: 'degraded', db: 'unreachable' });
    }
  });

  // Registro de módulos REST + WebSockets + Legal RGPD
  await registerWsRoutes(app);
  await app.register(legalRoutes);
  await app.register(authRoutes);
  await app.register(postRoutes);
  await app.register(calendarRoutes);
  await app.register(userRoutes);
  await app.register(mediaRoutes);

  return app;
}

async function main() {
  await ensureMediaDirectories();
  const app = await buildServer();
  const retentionTimer = startGdprRetentionWorker(app.log);

  const shutdown = async (signal: string) => {
    app.log.info(`Recibido ${signal}, cerrando servidor de forma limpia...`);
    clearInterval(retentionTimer);
    await app.close();
    await pool.end();
    process.exit(0);
  };

  process.on('SIGINT', () => void shutdown('SIGINT'));
  process.on('SIGTERM', () => void shutdown('SIGTERM'));

  try {
    await app.listen({ port: config.PORT, host: config.HOST });
  } catch (err) {
    app.log.error(err);
    process.exit(1);
  }
}

void main();
