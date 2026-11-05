import nodemailer from 'nodemailer';
import { config } from '../config.js';

const hasSmtpConfigured =
  config.SMTP_HOST.trim().length > 0 && config.SMTP_USER.trim().length > 0;

const transporter = hasSmtpConfigured
  ? nodemailer.createTransport({
      host: config.SMTP_HOST,
      port: config.SMTP_PORT,
      secure: config.SMTP_SECURE,
      auth: {
        user: config.SMTP_USER,
        pass: config.SMTP_PASS,
      },
    })
  : null;

export async function sendPasswordResetEmail(
  toEmail: string,
  displayName: string,
  code: string,
): Promise<void> {
  const subject = `${code} es tu código de recuperación de MyOutfit`;
  const html = `
    <div style="font-family: system-ui, -apple-system, sans-serif; max-width: 480px; margin: 0 auto; padding: 24px; border: 1px solid #e5e7eb; border-radius: 16px;">
      <h2 style="margin-top: 0; color: #111827;">Hola, ${escapeHtml(displayName)}</h2>
      <p style="color: #374151; line-height: 1.5;">
        Hemos recibido una solicitud para restablecer la contraseña de tu cuenta en <strong>MyOutfit</strong>.
        Introduce este código de 6 dígitos en la aplicación:
      </p>
      <div style="background: #f3f4f6; border-radius: 12px; padding: 18px; text-align: center; margin: 20px 0;">
        <span style="font-size: 32px; font-weight: 800; letter-spacing: 6px; color: #111827;">${code}</span>
      </div>
      <p style="color: #6b7280; font-size: 13px;">
        Este código caduca en <strong>15 minutos</strong>. Si no has solicitado este cambio, puedes ignorar este correo con total tranquilidad.
      </p>
    </div>
  `;

  if (!transporter) {
    if (config.NODE_ENV !== 'production') {
      console.info(`[DEV-SMTP] Código de recuperación para ${toEmail}: ${code}`);
    } else {
      console.warn('[SMTP] SMTP_HOST no está configurado; no se pudo enviar el email de recuperación.');
    }
    return;
  }

  await transporter.sendMail({
    from: config.SMTP_FROM,
    to: toEmail,
    subject,
    html,
  });
}

function escapeHtml(raw: string): string {
  return raw
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
