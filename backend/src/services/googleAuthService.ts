import { OAuth2Client } from 'google-auth-library';
import { config } from '../config.js';
import { processAndSaveAvatar } from './mediaService.js';

export interface VerifiedGoogleProfile {
  googleSub: string;
  email: string;
  emailVerified: boolean;
  displayName: string;
  pictureUrl?: string;
}

const oauthClient = new OAuth2Client();

export async function verifyGoogleIdToken(idToken: string): Promise<VerifiedGoogleProfile> {
  if (!config.GOOGLE_WEB_CLIENT_ID) {
    throw new Error('GOOGLE_WEB_CLIENT_ID no está configurado en el servidor');
  }

  const ticket = await oauthClient.verifyIdToken({
    idToken,
    audience: config.GOOGLE_WEB_CLIENT_ID,
  });

  const payload = ticket.getPayload();
  if (!payload || !payload.sub || !payload.email) {
    throw new Error('El token de Google no contiene información de cuenta válida');
  }

  return {
    googleSub: payload.sub,
    email: payload.email.toLowerCase(),
    emailVerified: Boolean(payload.email_verified),
    displayName: (payload.name || payload.given_name || payload.email.split('@')[0] || 'Usuario').slice(0, 60),
    pictureUrl: payload.picture,
  };
}

export async function downloadAndSaveGoogleAvatar(
  pictureUrl: string | undefined,
  userId: string,
): Promise<string | null> {
  if (!pictureUrl) return null;
  try {
    const res = await fetch(pictureUrl, { signal: AbortSignal.timeout(6_000) });
    if (!res.ok) return null;
    const arrayBuffer = await res.arrayBuffer();
    if (arrayBuffer.byteLength === 0 || arrayBuffer.byteLength > 5 * 1024 * 1024) {
      return null;
    }
    return await processAndSaveAvatar(Buffer.from(arrayBuffer), userId);
  } catch {
    return null;
  }
}
