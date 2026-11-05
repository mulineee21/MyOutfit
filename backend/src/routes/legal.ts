import type { FastifyInstance } from 'fastify';

export const CURRENT_PRIVACY_POLICY_VERSION = '2026.1-ES';

export interface PrivacyPolicySection {
  title: string;
  body: string;
}

export const PRIVACY_POLICY_DATA = {
  version: CURRENT_PRIVACY_POLICY_VERSION,
  lastUpdated: '2026-11-05',
  jurisdiction: 'Unión Europea (RGPD 2016/679) y España (LOPDGDD Ley Orgánica 3/2018)',
  controller: {
    name: 'Joel Molina — Proyecto MyOutfit',
    contactEmail: 'jomenesgussy@gmail.com',
    supervisoryAuthority: 'Agencia Española de Protección de Datos (AEPD) — https://www.aepd.es',
  },
  minimumAgeYears: 14,
  sections: [
    {
      title: '1. Responsable del Tratamiento (Art. 13.1.a RGPD)',
      body: 'El responsable del tratamiento de tus datos personales en MyOutfit es Joel Molina (contacto de privacidad y ejercicio de derechos: jomenesgussy@gmail.com). Los datos de la red social y del calendario en la nube se alojan en infraestructura propia ubicada en territorio español (Unión Europea), cifrada en tránsito mediante TLS.',
    },
    {
      title: '2. Datos Tratados y Minimización desde el Diseño (Art. 5.1.c y Art. 25 RGPD)',
      body: 'Únicamente tratamos los datos estrictamente necesarios: identificador de cuenta (@usuario, modificable 1 vez cada 30 días), correo electrónico, hash criptográfico Argon2/scrypt de contraseña o identificador federado de Google, foto de perfil opcional, publicaciones diarias de outfits y tu archivo privado de calendario. Al subir cualquier fotografía, nuestro servidor elimina automáticamente todas las cabeceras EXIF y coordenadas GPS antes de convertirla a WebP. Las direcciones IP en registros técnicos de seguridad se anonimizan truncando el último octeto.',
    },
    {
      title: '3. Finalidad y Base Jurídica (Art. 6.1 RGPD)',
      body: 'a) Ejecución de la relación contractual/servicio (Art. 6.1.b RGPD): gestionar tu cuenta, mostrar tus outfits durante 24 horas a tus seguidores autorizados (con cuenta privada activada por defecto según el Art. 25.2 RGPD) y conservar tu calendario privado anual.\nb) Consentimiento explícito (Art. 6.1.a RGPD): categorización opcional de prendas mediante IA (Google Gemini), desactivable en cualquier momento desde Mi Perfil.\nc) Interés legítimo de seguridad (Art. 6.1.f RGPD): protección frente a accesos no autorizados y abuso mediante limitación de peticiones (rate-limiting) con IPs seudonimizadas.',
    },
    {
      title: '4. Edad Mínima Legal en España (Art. 7 LOPDGDD)',
      body: 'Conforme al artículo 7.1 de la Ley Orgánica 3/2018 (LOPDGDD), el uso de la red social MyOutfit requiere tener al menos 14 años cumplidos. Si eres menor de 14 años, no debes registrarte sin la autorización verificable de tus padres o tutores legales.',
    },
    {
      title: '5. Plazos de Conservación y Borrado Automático (Art. 5.1.e RGPD)',
      body: '• Feed Social 24h: las publicaciones efímeras, junto con sus reacciones y comentarios, se eliminan automáticamente de la tabla pública transcurridas exactamente 24 horas.\n• Calendario Privado: tus fotografías de archivo solo son visibles para ti y se conservan hasta que borres una entrada o elimines tu cuenta.\n• Códigos de recuperación y sesiones: se purgan automáticamente tras su uso o caducidad.',
    },
    {
      title: '6. Destinatarios y Transferencias Internacionales (Art. 44 RGPD)',
      body: 'No vendemos ni cedemos tus datos a terceros ni incluimos rastreadores publicitarios. Únicamente intervienen proveedores tecnológicos estrictamente necesarios cuando tú los utilizas: Google Ireland Limited (si eliges "Continuar con Google" o si mantienes activa la función opcional de IA Gemini para etiquetar prendas) y Gmail SMTP exclusivamente cuando solicitas un código de recuperación de contraseña.',
    },
    {
      title: '7. Tus Derechos RGPD / LOPDGDD (Acceso, Rectificación, Supresión, Portabilidad, Limitación y Oposición)',
      body: 'Puedes ejercer tus derechos directamente y de forma inmediata desde la propia aplicación en "Mi Perfil → Privacidad y Derechos RGPD":\n• Portabilidad y Acceso (Art. 15 y 20 RGPD): descarga inmediata en formato JSON estructurado de todos tus datos almacenados en el servidor.\n• Derecho al Olvido / Supresión Total (Art. 17 RGPD): botón "Eliminar mi cuenta y todos mis datos", que borra de forma irreversible tanto tus registros en base de datos como todos tus archivos fotográficos WebP del disco del servidor.\nTambién puedes escribir a jomenesgussy@gmail.com o presentar una reclamación ante la Agencia Española de Protección de Datos (www.aepd.es).',
    },
  ] satisfies PrivacyPolicySection[],
};

export const CURRENT_TERMS_VERSION = '2026.1-EU';

export const COMMUNITY_TERMS_DATA = {
  version: CURRENT_TERMS_VERSION,
  lastUpdated: '2026-11-05',
  jurisdiction: 'Unión Europea (Reglamento UE 2022/2065 DSA) y España (LSSI-CE Ley 34/2002)',
  sections: [
    {
      title: '1. Aceptación y Objeto de los Términos (Art. 14 DSA)',
      body: 'Las presentes Condiciones de Uso y Normas de la Comunidad regulan el acceso, registro y participación en la red social efímera MyOutfit ("Outfits 24h"). Al registrarte o utilizar la plataforma, aceptas plenamente estas condiciones.',
    },
    {
      title: '2. Política de Tolerancia Cero frente al Contenido Ilícito y Abusivo (UGC Policy & Art. 16 DSA)',
      body: 'MyOutfit mantiene una estricta política de TOLERANCIA CERO ante comportamientos o contenidos abusivos. Queda terminantemente prohibido publicar, comentar o transmitir:\n• Desnudez, pornografía o contenido de índole sexual o sugestiva.\n• Acoso, intimidación, hostigamiento, difamación o amenazas hacia cualquier persona.\n• Discurso de odio, discriminación por origen, etnia, religión, género, discapacidad u orientación sexual.\n• Promoción de violencia, autolesiones, actividades ilegales o sustancias no autorizadas.\n• Vulneración de la propia imagen de terceros (fotos tomadas sin consentimiento) o infracción de derechos de autor y propiedad intelectual.\n• Spam, estafas, suplantación de identidad o engaño comercial.',
    },
    {
      title: '3. Medidas de Protección: Notificación (Denuncia) y Bloqueo de Usuarios',
      body: 'Para garantizar un entorno seguro y dar cumplimiento al Reglamento de Servicios Digitales (DSA) y a las políticas de seguridad de aplicaciones móviles:\n• Denunciar Contenido (Art. 16 DSA): cualquier usuario puede reportar de inmediato una publicación ilícita u ofensiva a través del menú de 3 puntos (⋮). Las publicaciones denunciadas se ocultan preventivamente.\n• Bloquear Usuarios: cualquier usuario puede bloquear de forma instantánea a otra persona desde su perfil o publicación. Los usuarios bloqueados no pueden ver tus publicaciones, enviarte solicitudes ni encontrarte en búsquedas.',
    },
    {
      title: '4. Moderación de Contenidos y Sanciones (Art. 14 y 17 DSA)',
      body: 'El equipo de MyOutfit revisa las denuncias en un plazo máximo de 24 horas laborables. Si se constata una vulneración de estas normas, nos reservamos el derecho de:\n• Retirar de inmediato el contenido infractor.\n• Suspender temporal o permanentemente la cuenta del usuario infractor sin previo aviso en casos de infracciones graves.\n• Notificar a las autoridades competentes en caso de detección de delitos o abusos a menores.',
    },
    {
      title: '5. Derecho al Olvido y Carácter Efímero (Art. 17 RGPD)',
      body: 'Todas las publicaciones del feed de outfits tienen una caducidad estricta de 24 horas, tras la cual son purgadas automáticamente. Asimismo, el autor puede suprimir voluntariamente su fotografía en cualquier instante, eliminándose de forma definitiva e irreversible de los servidores.',
    },
    {
      title: '6. Ley Aplicable y Resolución de Litigios (ODR Unión Europea)',
      body: 'Estas condiciones se rigen por la legislación española y el Derecho de la Unión Europea. Conforme al Reglamento UE 524/2013, la Comisión Europea facilita una plataforma de resolución de litigios en línea accesible en: https://ec.europa.eu/consumers/odr.',
    },
  ] satisfies PrivacyPolicySection[],
};

export const LEGAL_NOTICE_DATA = {
  providerName: 'Joel Molina',
  projectName: 'MyOutfit',
  contactEmail: 'jomenesgussy@gmail.com',
  location: 'España (Unión Europea)',
  applicableLaw: 'Legislación Española y Derecho Comunitario Europeo',
  supervisoryAuthority: 'Agencia Española de Protección de Datos (AEPD)',
};

export async function legalRoutes(app: FastifyInstance): Promise<void> {
  app.get('/api/legal/privacy', async (_request, reply) => {
    return reply.send(PRIVACY_POLICY_DATA);
  });

  app.get('/api/legal/terms', async (_request, reply) => {
    return reply.send(COMMUNITY_TERMS_DATA);
  });

  app.get('/privacy', async (_request, reply) => {
    const sectionsHtml = PRIVACY_POLICY_DATA.sections
      .map(
        (s) => `
      <section style="margin-bottom:20px;">
        <h2 style="font-size:18px;color:#1f1f1f;margin-bottom:6px;">${s.title}</h2>
        <p style="white-space:pre-line;line-height:1.6;color:#444;margin:0;">${s.body}</p>
      </section>`,
      )
      .join('');

    const html = `<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Política de Privacidad y Protección de Datos (RGPD / LOPDGDD) — MyOutfit</title>
</head>
<body style="font-family:system-ui,-apple-system,sans-serif;max-width:760px;margin:32px auto;padding:0 20px;background:#fafafa;color:#222;">
  <div style="background:#fff;border-radius:16px;padding:28px;box-shadow:0 2px 12px rgba(0,0,0,0.06);">
    <h1 style="margin-top:0;color:#6750A4;">Política de Privacidad y Protección de Datos — MyOutfit</h1>
    <p style="font-size:13px;color:#666;">Versión ${PRIVACY_POLICY_DATA.version} · Última actualización: ${PRIVACY_POLICY_DATA.lastUpdated} · Marco normativo: ${PRIVACY_POLICY_DATA.jurisdiction}</p>
    <div style="margin:12px 0;"><a href="/terms" style="color:#6750A4;margin-right:12px;">Ver Términos y Normas de la Comunidad (DSA)</a> <a href="/legal-notice" style="color:#6750A4;">Ver Aviso Legal</a></div>
    <hr style="border:none;border-top:1px solid #eee;margin:20px 0;" />
    ${sectionsHtml}
  </div>
</body>
</html>`;

    return reply.type('text/html; charset=utf-8').send(html);
  });

  app.get('/terms', async (_request, reply) => {
    const sectionsHtml = COMMUNITY_TERMS_DATA.sections
      .map(
        (s) => `
      <section style="margin-bottom:20px;">
        <h2 style="font-size:18px;color:#1f1f1f;margin-bottom:6px;">${s.title}</h2>
        <p style="white-space:pre-line;line-height:1.6;color:#444;margin:0;">${s.body}</p>
      </section>`,
      )
      .join('');

    const html = `<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Términos de Servicio y Normas de la Comunidad (DSA) — MyOutfit</title>
</head>
<body style="font-family:system-ui,-apple-system,sans-serif;max-width:760px;margin:32px auto;padding:0 20px;background:#fafafa;color:#222;">
  <div style="background:#fff;border-radius:16px;padding:28px;box-shadow:0 2px 12px rgba(0,0,0,0.06);">
    <h1 style="margin-top:0;color:#6750A4;">Términos de Servicio y Normas de la Comunidad — MyOutfit</h1>
    <p style="font-size:13px;color:#666;">Versión ${COMMUNITY_TERMS_DATA.version} · Última actualización: ${COMMUNITY_TERMS_DATA.lastUpdated} · Marco: ${COMMUNITY_TERMS_DATA.jurisdiction}</p>
    <div style="margin:12px 0;"><a href="/privacy" style="color:#6750A4;margin-right:12px;">Ver Política de Privacidad (RGPD)</a> <a href="/legal-notice" style="color:#6750A4;">Ver Aviso Legal</a></div>
    <hr style="border:none;border-top:1px solid #eee;margin:20px 0;" />
    ${sectionsHtml}
  </div>
</body>
</html>`;

    return reply.type('text/html; charset=utf-8').send(html);
  });

  app.get('/legal-notice', async (_request, reply) => {
    const html = `<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Aviso Legal (LSSI-CE Ley 34/2002) — MyOutfit</title>
</head>
<body style="font-family:system-ui,-apple-system,sans-serif;max-width:760px;margin:32px auto;padding:0 20px;background:#fafafa;color:#222;">
  <div style="background:#fff;border-radius:16px;padding:28px;box-shadow:0 2px 12px rgba(0,0,0,0.06);">
    <h1 style="margin-top:0;color:#6750A4;">Aviso Legal e Información General — MyOutfit</h1>
    <p style="font-size:13px;color:#666;">Cumplimiento del Art. 10 de la Ley 34/2002 (LSSI-CE) en España</p>
    <hr style="border:none;border-top:1px solid #eee;margin:20px 0;" />
    <p><strong>Titular del Servicio:</strong> ${LEGAL_NOTICE_DATA.providerName}</p>
    <p><strong>Proyecto:</strong> ${LEGAL_NOTICE_DATA.projectName} (Red social y organizador de outfits)</p>
    <p><strong>Correo electrónico de contacto y ejercicio de derechos:</strong> <a href="mailto:${LEGAL_NOTICE_DATA.contactEmail}">${LEGAL_NOTICE_DATA.contactEmail}</a></p>
    <p><strong>Ubicación de servidores:</strong> ${LEGAL_NOTICE_DATA.location}</p>
    <p><strong>Autoridad de Control:</strong> ${LEGAL_NOTICE_DATA.supervisoryAuthority}</p>
    <div style="margin-top:24px;">
      <a href="/privacy" style="color:#6750A4;margin-right:12px;">Política de Privacidad</a>
      <a href="/terms" style="color:#6750A4;">Términos de Servicio y Normas (DSA)</a>
    </div>
  </div>
</body>
</html>`;

    return reply.type('text/html; charset=utf-8').send(html);
  });
}
