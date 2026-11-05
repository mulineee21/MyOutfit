import type { FastifyInstance } from 'fastify';
import type { WebSocket } from '@fastify/websocket';
import { pool } from '../db.js';

export interface SocialWsEvent {
  type: 'NEW_POST' | 'NEW_REACTION' | 'NEW_COMMENT' | 'FOLLOW_EVENT' | 'POST_EXPIRED';
  payload: Record<string, unknown>;
}

const clientsByUserId = new Map<string, Set<WebSocket>>();

export function addWsClient(userId: string, socket: WebSocket): void {
  let set = clientsByUserId.get(userId);
  if (!set) {
    set = new Set();
    clientsByUserId.set(userId, set);
  }
  set.add(socket);

  socket.on('close', () => {
    const current = clientsByUserId.get(userId);
    if (!current) return;
    current.delete(socket);
    if (current.size === 0) {
      clientsByUserId.delete(userId);
    }
  });
}

export function sendToUser(userId: string, event: SocialWsEvent): void {
  const sockets = clientsByUserId.get(userId);
  if (!sockets || sockets.size === 0) return;

  const serialized = JSON.stringify(event);
  for (const ws of sockets) {
    if (ws.readyState === 1) {
      ws.send(serialized);
    }
  }
}

/**
 * Envía en tiempo real una notificación a los seguidores aceptados conectados cuando alguien publica su BeReal de Outfit.
 */
export async function broadcastToFollowers(
  authorUserId: string,
  event: SocialWsEvent,
): Promise<void> {
  const { rows } = await pool.query<{ follower_id: string }>(
    `SELECT follower_id FROM follows WHERE following_id = $1 AND status = 'ACCEPTED'`,
    [authorUserId],
  );
  for (const row of rows) {
    sendToUser(row.follower_id, event);
  }
}

export async function registerWsRoutes(app: FastifyInstance): Promise<void> {
  app.get('/api/ws', { websocket: true }, async (socket, request) => {
    try {
      const query = request.query as { token?: string };
      const authHeader = request.headers.authorization;
      const rawToken =
        query.token ||
        (authHeader?.startsWith('Bearer ') ? authHeader.slice(7) : undefined);

      if (!rawToken) {
        socket.close(1008, 'Missing token');
        return;
      }

      const decoded = app.jwt.verify<{ sub: string; username: string }>(rawToken);
      addWsClient(decoded.sub, socket);

      socket.send(
        JSON.stringify({
          type: 'CONNECTED',
          payload: { userId: decoded.sub, serverTime: new Date().toISOString() },
        }),
      );
    } catch {
      socket.close(1008, 'Invalid token');
    }
  });
}
