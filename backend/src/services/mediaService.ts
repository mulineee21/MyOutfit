import { randomUUID } from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import sharp from 'sharp';
import { config } from '../config.js';

export interface ProcessedOutfitMedia {
  imageWebpPath: string;
  thumbnailWebpPath: string;
  widthPx: number;
  heightPx: number;
}

export async function ensureMediaDirectories(): Promise<void> {
  await Promise.all([
    fs.mkdir(path.join(config.MEDIA_ROOT, 'avatars'), { recursive: true }),
    fs.mkdir(path.join(config.MEDIA_ROOT, 'outfits', 'full'), { recursive: true }),
    fs.mkdir(path.join(config.MEDIA_ROOT, 'outfits', 'thumbs'), { recursive: true }),
  ]);
}

/**
 * Convierte la foto subida desde Android a WebP optimizado:
 * 1. Corrige rotación EXIF de cámaras móviles automáticamente (`.rotate()`).
 * 2. Elimina metadatos EXIF/GPS sensibles por privacidad.
 * 3. Genera imagen principal (máx 1440x1920) + miniatura ligera (400x533) para el Calendario Privado.
 */
export async function processAndSaveOutfitImage(
  inputBuffer: Buffer,
): Promise<ProcessedOutfitMedia> {
  const now = new Date();
  const yearMonth = `${now.getUTCFullYear()}-${String(now.getUTCMonth() + 1).padStart(2, '0')}`;
  const fileId = randomUUID();

  const fullRelDir = path.posix.join('outfits', 'full', yearMonth);
  const thumbRelDir = path.posix.join('outfits', 'thumbs', yearMonth);

  await Promise.all([
    fs.mkdir(path.join(config.MEDIA_ROOT, fullRelDir), { recursive: true }),
    fs.mkdir(path.join(config.MEDIA_ROOT, thumbRelDir), { recursive: true }),
  ]);

  const imageWebpPath = path.posix.join(fullRelDir, `${fileId}.webp`);
  const thumbnailWebpPath = path.posix.join(thumbRelDir, `${fileId}_thumb.webp`);

  const fullAbsPath = resolveSafeMediaPath(imageWebpPath);
  const thumbAbsPath = resolveSafeMediaPath(thumbnailWebpPath);

  const fullInfo = await sharp(inputBuffer)
    .rotate() // Respeta orientación EXIF de Android y limpia cabeceras EXIF/GPS por defecto
    .resize({
      width: 1440,
      height: 1920,
      fit: 'inside',
      withoutEnlargement: true,
    })
    .webp({ quality: config.WEBP_QUALITY, effort: 4 })
    .toFile(fullAbsPath);

  await sharp(inputBuffer)
    .rotate()
    .resize({
      width: 400,
      height: 533,
      fit: 'cover',
      position: 'attention',
    })
    .webp({ quality: 74, effort: 4 })
    .toFile(thumbAbsPath);

  return {
    imageWebpPath,
    thumbnailWebpPath,
    widthPx: fullInfo.width,
    heightPx: fullInfo.height,
  };
}

export async function processAndSaveAvatar(
  inputBuffer: Buffer,
  userId: string,
): Promise<string> {
  const fileName = `${userId}_${Date.now()}.webp`;
  const relPath = path.posix.join('avatars', fileName);
  const absPath = resolveSafeMediaPath(relPath);

  await sharp(inputBuffer)
    .rotate()
    .resize({ width: 512, height: 512, fit: 'cover' })
    .webp({ quality: 80, effort: 4 })
    .toFile(absPath);

  return relPath;
}

export async function safeDeleteMediaFile(relativePath: string | null | undefined): Promise<void> {
  if (!relativePath) return;
  try {
    const absPath = resolveSafeMediaPath(relativePath);
    await fs.unlink(absPath);
  } catch {
    // Ignorar si ya no existe en disco
  }
}

/**
 * Impide ataques de Path Traversal asegurando que la ruta resuelta esté estrictamente dentro de MEDIA_ROOT.
 */
export function resolveSafeMediaPath(relativePath: string): string {
  const rootResolved = path.resolve(config.MEDIA_ROOT);
  const targetResolved = path.resolve(rootResolved, relativePath);
  if (!targetResolved.startsWith(rootResolved + path.sep)) {
    throw new Error('Ruta multimedia inválida o fuera del volumen permitido');
  }
  return targetResolved;
}
