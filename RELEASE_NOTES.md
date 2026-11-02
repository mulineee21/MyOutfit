# 🎉 MyOutfit v1.1.0 - Lista de Deseos con IA, Integración Compartir Enlace, Autocategorización y Copia de Seguridad Portátil

¡Segunda versión oficial de **MyOutfit**! Desarrollada por **Joel Molina** ([@mulineee21](https://github.com/mulineee21)). Esta actualización introduce funciones muy solicitadas: una **Lista de Deseos inteligente** impulsada por IA, integración directa con el menú **Compartir de Android**, **autocategorización con IA** en el armario y un sistema integral de **Copia de Seguridad y Restauración (Exportar/Importar)** en formato `.zip` para facilitar el cambio de dispositivo.

---

## ✨ Novedades principales en la v1.1.0

### 🛍️ 1. Lista de Deseos Inteligente con IA (Wishlist)
- **Añade prendas mediante enlace:** Pega cualquier enlace de producto de tiendas como **SHEIN, Amazon, Zara, ASOS**, entre otras.
- **Extracción automática de metadatos:** Obtiene en segundo plano la fotografía oficial, el nombre del producto, el precio, la moneda y detecta la tienda de origen.
- **Análisis de estilo con Gemini IA:** La IA analiza la prenda y genera automáticamente una descripción estilística, la categoría recomendada y etiquetas de estilo (*#gótico, #deporte, #casual, #aesthetic, #vintage, #elegante...*).
- **Filtros rápidos:** Filtra tus deseos por tienda o por estilo de moda.
- **Acceso directo y control:** Botón "Ver en tienda" para abrir el enlace original en tu navegador, opción de marcar prendas como compradas o eliminarlas.

### 📲 2. Compartir enlaces directamente desde apps externas (Share Sheet)
- **Integración nativa con Android:** Cuando encuentres una prenda en SHEIN, Amazon, AliExpress o cualquier navegador, simplemente pulsa **Compartir** y selecciona **MyOutfit**.
- **Apertura automática:** La aplicación se abre directamente en la pestaña **Deseos**, pega el enlace recibido e inicia el análisis automático con IA sin que tengas que copiar y pegar manualmente.

### 🤖 3. Autocategorización de Ropa con Gemini IA en el Armario
- **Detección inteligente de prendas:** Al subir una foto desde la cámara o galería a tu Armario Digital, Gemini Vision analiza la imagen para sugerir la categoría adecuada (*Vestidos, Camisetas, Sudaderas, etc.*), el color predominante y etiquetas de estilo.
- **Control total del usuario (Toggle On/Off):** Puedes activar o desactivar la categorización asistida por IA con un solo toque desde la barra superior del armario o en la configuración de la app.

### 📦 4. Copia de Seguridad y Restauración Completa (Exportar & Importar ZIP)
- **Migración sin esfuerzo entre teléfonos:** Exporta absolutamente todos tus datos en un único archivo `.zip` comprimido y portátil.
- **Respaldo integral:**
  - Todas las fotos de tu armario digital y lookbook en resolución completa.
  - Base de datos completa: prendas, conjuntos guardados, historial del diario de outfits, favoritos, inspiraciones, lista de deseos y preferencias.
- **Importación robusta:** Verificación de integridad del archivo, remapeo automático de identificadores y rutas locales de imagen para garantizar la coherencia relacional sin duplicados ni errores.
- **Fácil acceso:** Botón de copia de seguridad disponible directamente en la barra superior de la pantalla principal.

---

## 🛠️ Mejoras Técnicas y Estabilidad
- **Room Database v2:** Migración estructurada de base de datos para dar soporte a la entidad `WishlistItem` y nuevos campos de preferencias.
- **Suite de Pruebas Unitarias:** Cobertura ampliada con 32 tests unitarios pasando exitosamente (`BackupManagerTest`, `WishlistViewModelTest`, `DigitalClosetViewModelTest`, etc.).
- **Empaquetado Seguro:** Compilación firmada oficialmente con el mismo certificado y clave que la versión inicial (`keystore/myoutfit-joelmolina.jks`) y mismo logotipo/icono.

---

## 📱 Requisitos técnicos y compatibilidad
- **Versión mínima:** Android 11 (API 30).
- **Versión optimizada:** Android 15+ (API 37).
- **Diseño:** Material Design 3 con modo oscuro adaptativo y animaciones fluidas con Jetpack Compose.
- **Privacidad:** Almacenamiento 100% local en tu dispositivo con cifrado en reposo del sistema.

---

## 📦 Archivos descargables
- **`MyOutfit-JoelMolina-v1.1.0.apk`**: APK oficial firmado y listo para instalar en cualquier teléfono Android 11+.

---

## 📜 Historial de versiones anteriores

<details>
<summary><b>Ver notas de la versión v1.0.0</b></summary>

### 🎉 MyOutfit v1.0.0 - Tu Armario Digital & Estilista Personal con IA
- Recomendador inteligente con Google Gemini IA y motor heurístico local offline.
- Armario Digital privado con 14 categorías y almacenamiento interno seguro.
- Lookbook de inspiración para almacenar referencias estéticas.
- Diario de outfits con calificación de estrellas y gestión de favoritos.
- Base de datos Room 100% local y Jetpack DataStore Preferences.

</details>
