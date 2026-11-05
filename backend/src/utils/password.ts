import { createHash, randomBytes, scrypt as scryptCb, timingSafeEqual } from 'node:crypto';
import { promisify } from 'node:util';

const scrypt = promisify(scryptCb);
const KEY_LENGTH = 64;

/**
 * Hash de contraseñas seguro con scrypt nativo de OpenSSL (Node.js 22)
 * Formato almacenado: scrypt$<saltHex>$<derivedKeyHex>
 */
export async function hashPassword(plainPassword: string): Promise<string> {
  const salt = randomBytes(16).toString('hex');
  const derivedKey = (await scrypt(plainPassword, salt, KEY_LENGTH)) as Buffer;
  return `scrypt$${salt}$${derivedKey.toString('hex')}`;
}

export async function verifyPassword(
  plainPassword: string,
  storedHash: string,
): Promise<boolean> {
  const parts = storedHash.split('$');
  if (parts.length !== 3 || parts[0] !== 'scrypt') {
    return false;
  }
  const salt = parts[1];
  const keyHex = parts[2];
  if (!salt || !keyHex) return false;

  const expectedBuffer = Buffer.from(keyHex, 'hex');
  const derivedKey = (await scrypt(plainPassword, salt, expectedBuffer.length)) as Buffer;
  if (expectedBuffer.length !== derivedKey.length) return false;

  return timingSafeEqual(expectedBuffer, derivedKey);
}

export function sha256Hex(value: string): string {
  return createHash('sha256').update(value).digest('hex');
}

export function generateOpaqueToken(): string {
  return randomBytes(48).toString('base64url');
}
