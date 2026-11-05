# MyOutfit 👗👔

[![Autor](https://img.shields.io/badge/Autor-Joel%20Molina%20(@mulineee21)-8b5cf6?style=for-the-badge&logo=github)](https://github.com/mulineee21)
[![Licencia](https://img.shields.io/badge/Licencia-GPL%20v3%20o%20superior-blue.svg?style=for-the-badge)](LICENSE)
[![Sitio Web Oficial](https://img.shields.io/badge/Web%20Oficial-mulineee21.github.io%2Fmyoutfit--web-ec4899?style=for-the-badge)](https://mulineee21.github.io/myoutfit-web/)
[![Release Oficial](https://img.shields.io/badge/Release-v2.0-emerald.svg?style=for-the-badge&logo=android)](https://github.com/mulineee21/MyOutfit/releases/tag/v2.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7f52ff?style=for-the-badge&logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285f4?style=for-the-badge&logo=android)](https://developer.android.com/jetpack/compose)

> **MyOutfit** es una aplicación moderna y nativa para Android creada por **Joel Molina**, desarrollada en **Kotlin** y **Jetpack Compose**. Integra la Inteligencia Artificial de **Google Gemini** para recomendarte qué ropa ponerte cada día, autocategorizar tus prendas, organizar tu **Lista de Deseos**, y en su **versión 2.0** estrena una **Red Social Efímera de Outfits 24h** con total **cumplimiento normativo europeo (RGPD y DSA)**.

🌐 **Sitio Web Oficial & Demo:** [https://mulineee21.github.io/myoutfit-web/](https://mulineee21.github.io/myoutfit-web/)  
📦 **Descarga Directa de APK (v2.0):** [MyOutfit-JoelMolina-v2.0.apk](https://github.com/mulineee21/MyOutfit/releases/download/v2.0/MyOutfit-JoelMolina-v2.0.apk)

---

## 👤 Autor

* **Creador & Desarrollador Principal:** **Joel Molina**
* **Perfil de GitHub:** [@mulineee21](https://github.com/mulineee21)
* **Repositorio Web:** [mulineee21/myoutfit-web](https://github.com/mulineee21/myoutfit-web)

---

## 🏛️ Arquitecturas y Patrones de Diseño Utilizados

El proyecto ha sido desarrollado bajo los más rigurosos estándares de la ingeniería de software moderna en Android, garantizando escalabilidad, testabilidad y separación de responsabilidades:

### 1. Clean Architecture (Arquitectura Limpia)
El código está estructurado en tres capas desacopladas:
* **Capa de Presentación (`ui/`):** Construida de forma reactiva y declarativa con **Jetpack Compose** y **Material Design 3 (Material You)**. Se encarga de renderizar el estado visual y capturar eventos de usuario.
* **Capa de Dominio (`domain/`):** Contiene la lógica pura de negocio, reglas de recomendación y servicios de IA:
  - `OutfitRecommendationEngine`: Algoritmo heurístico local para matching de prendas según ocasión, temporadas, compatibilidad de colores y tipos de piezas.
  - `GeminiOutfitService`: Servicio de conexión, formulación de prompts estructurados y parsing seguro JSON para recomendación de outfits.
  - `GeminiClothingAnalysisService`: Servicio multimodal con Gemini Vision para autocategorización de prendas y análisis estilístico de productos por enlace.
  - `ProductMetadataExtractor`: Extractor en segundo plano de metadatos OpenGraph y HTML (título, foto, precio, tienda) para enlaces de SHEIN, Amazon, Zara, etc.
* **Capa de Datos (`data/`):** Implementación de persistencia local, repositorios concretos y motor de respaldo:
  - `ClothingRepository`, `OutfitRepository`, `InspirationRepository`, `LoggedOutfitRepository`, `WishlistRepository`.
  - `BackupManager`: Motor de exportación e importación en formato `.zip` que serializa la base de datos completa e incluye las imágenes del armario con resolución original.

### 2. Patrón MVVM (Model-View-ViewModel)
* **Separación de responsabilidades:** La interfaz de usuario observa los cambios de estado emitidos por los `ViewModel` (`DigitalClosetViewModel`, `RecommendationViewModel`, `WishlistViewModel`, `InspirationViewModel`, `LogAndFavoritesViewModel`, `BackupViewModel`).
* **Supervivencia a cambios de configuración:** Los ViewModels gestionan el ciclo de vida de la actividad y la ejecución asíncrona mediante `viewModelScope`.

### 3. Flujo Unidireccional de Datos (UDF - Unidirectional Data Flow)
* **Estado inmutable:** Los datos fluyen en una sola dirección mediante `StateFlow` y `asStateFlow()`.
* **Eventos explícitos:** Las acciones de la UI se transmiten a los ViewModels a través de funciones de eventos claramente definidas, evitando mutaciones concurrentes descontroladas.

### 4. Repository Pattern (Patrón Repositorio)
* Actúa como única fuente de la verdad (*Single Source of Truth*), abstrayendo a los casos de uso y ViewModels de los detalles de implementación de la base de datos o almacenamiento en disco.

### 5. Arquitectura Offline-First y Privacidad
* **Persistencia Total Local:** Todo el armario, lookbook, diario de outfits y lista de deseos se almacena localmente en SQLite mediante **Room Database**.
* **Privacidad por Diseño:** Las fotos de las prendas se guardan en el almacenamiento interno privado de la aplicación, garantizando que tus imágenes personales permanezcan siempre bajo tu control.
* **Jetpack DataStore Preferences:** Para almacenar de manera reactiva y segura las preferencias de usuario, opciones de IA y la clave de API de Gemini.

### 6. Concurrencia Estructurada y Programación Reactiva
* **Kotlin Coroutines:** Para la gestión eficiente y no bloqueante de tareas en segundo plano (consultas IO a Room, llamadas HTTP a Gemini, extracción de metadatos, compresión ZIP).
* **Kotlin Flow:** Para la propagación reactiva en tiempo real de los datos desde la base de datos hasta la interfaz de usuario.

---

## ✨ Características Principales

1. **🌟 Red Social Efímera «Outfits 24h» (¡Novedad v2.0!):**
   - Publica tu outfit diario con duración estricta de 24 horas y cuenta atrás en tiempo real.
   - **Diseño Moderno & Elegante:** Tarjetas estilizadas en Jetpack Compose con degradados sutiles, estados de interacción suaves y botón de "Me gusta" reactivo.
   - **Comentarios con Foto de Perfil:** Visualización rica de opiniones de la comunidad acompañadas del avatar de cada usuario.
   - **Perfiles Públicos e Inspección:** Pulsa sobre cualquier usuario para consultar su perfil público, biografía y sus looks activos.
   - **Notificaciones de Expiración:** Alertas automáticas para avisar al usuario cuando su publicación de 24 horas esté por concluir o haya finalizado.

2. **🇪🇺 Cumplimiento Legal Europeo Total (RGPD, LOPDGDD y DSA) (¡Novedad v2.0!):**
   - **Derecho a la Supresión (Derecho al Olvido):** El usuario puede borrar sus publicaciones y comentarios en cualquier momento de forma inmediata e irreversible tanto localmente como en el servidor.
   - **Mecanismo de Notificación y Acción (Ley de Servicios Digitales - DSA):** Sistema de denuncia/reporte de publicaciones y comentarios con clasificación de infracciones (contenido inapropiado, spam, odio, suplantación, etc.).
   - **Sistema de Bloqueo de Usuarios:** Capacidad de bloquear usuarios conflictivos, ocultando bidireccional e instantáneamente todas sus publicaciones e interacciones.
   - **Portal Legal Integrado:** Acceso directo a Términos y Condiciones, Política de Privacidad de la UE y Aviso Legal desde la pantalla de perfil.

3. **🛍️ Lista de Deseos Inteligente con IA (Wishlist):**
   - Agrega prendas mediante enlaces de producto (**SHEIN, Amazon, Zara, ASOS, etc.**) o de forma manual.
   - Extracción automática de metadatos: foto de alta calidad, precio, moneda y nombre del producto.
   - Análisis estilístico con Gemini IA: clasificación por tipo de prenda y etiquetas de estilo (#gótico, #deporte, #casual, #vintage, #aesthetic).
   - Filtros por tienda y estilo de moda. Enlace directo al producto ("Ver en tienda") y gestión de comprados.

4. **📲 Integración Nativa con el Menú Compartir de Android:**
   - Comparte enlaces directamente desde aplicaciones externas (SHEIN, Amazon, Chrome, etc.) hacia **MyOutfit**.
   - La app se abre directamente en la Lista de Deseos, autocompleta el enlace recibido e inicia el análisis con IA automáticamente.

5. **🚪 Armario Digital con Autocategorización por IA:**
   - Guarda fotos de tus prendas tomadas con la cámara o elegidas de la galería.
   - **Autocategorización asistida por IA:** Gemini Vision analiza la prenda y sugiere la categoría (de entre 14 opciones), color predominante y etiquetas de estilo.
   - **Interruptor configurable:** El usuario decide libremente si desea activar o desactivar la asistencia de IA en cualquier momento.

6. **🤖 Recomendaciones de Ropa con Google Gemini IA:**
   - Selección inteligente de outfits basada en tu plan del día (oficina, gym, fiesta, etc.) y tu vibe/estilo deseado.
   - Conexión opcional con tu Tablero de Inspiración (Lookbook).
   - Consejos de estilista personalizados para cada conjunto.
   - **Motor Heurístico Local:** Motor alternativo offline para sugerencias instantáneas sin conexión a internet ni consumo de API.

7. **📦 Copia de Seguridad y Restauración Completa (Backup & Restore):**
   - Exporta toda tu información en un archivo comprimido portátil `.zip` para transferir tus datos fácilmente al cambiar de móvil.
   - Incluye todas las fotos reales en resolución completa y la base de datos completa con remapeo de identificadores y validación de integridad.

8. **💡 Tablero de Inspiración (Lookbook):**
   - Guarda imágenes de referencia de moda y tendencias para que la IA aprenda de tus gustos estéticos.

9. **⭐ Diario de Outfits & Favoritos:**
   - Registro diario de qué outfit usaste para evitar repeticiones.
   - Guarda tus combinaciones favoritas para acceder a ellas en cualquier momento.

---

## 🛠️ Stack Tecnológico

| Componente | Tecnología |
| :--- | :--- |
| **Lenguaje** | Kotlin 100% |
| **Interfaz de Usuario** | Jetpack Compose + Material Design 3 (Material You) |
| **Arquitectura** | Clean Architecture + MVVM + UDF + Repository Pattern |
| **Inteligencia Artificial** | Google Gemini API (`gemini-1.5-flash` Multimodal y Texto) |
| **Base de Datos** | Room Database v2 (SQLite) + TypeConverters |
| **Preferencias** | Jetpack DataStore Preferences |
| **Asincronía & Reactividad** | Kotlin Coroutines & StateFlow / Flow |
| **Carga de Imágenes** | Coil (Compose) |
| **Cliente HTTP** | OkHttp3 |
| **Compresión de Datos** | Java ZipOutputStream / ZipInputStream |
| **Almacenamiento de Archivos** | Android Internal Storage + FileProvider |

---

## 📂 Estructura del Código Fuente

```text
app/src/main/java/com/example/myoutfit/
├── MyOutfitApplication.kt           # Inicialización de dependencias y repositorios de la app
├── MainActivity.kt                  # Activity principal con soporte de Intent ACTION_SEND
├── data/                            # CAPA DE DATOS (Data Layer)
│   ├── backup/                      # Sistema de Backup & Restore ZIP (BackupManager, BackupModels)
│   ├── local/
│   │   ├── converters/              # Conversores de tipos para Room
│   │   ├── dao/                     # DAOs (ClothingDao, OutfitDao, WishlistDao, etc.)
│   │   ├── db/                      # MyOutfitDatabase (Room v2 con migraciones)
│   │   ├── entity/                  # Entidades (ClothingItem, Outfit, WishlistItem, etc.)
│   │   └── preferences/             # UserPreferencesRepository (DataStore)
│   └── repository/                  # Repositorios concretos (Clothing, Wishlist, etc.)
├── domain/                          # CAPA DE DOMINIO (Domain Layer)
│   ├── ai/                          # GeminiClothingAnalysisService (Visión y análisis multimodal)
│   ├── recommendation/              # GeminiOutfitService & OutfitRecommendationEngine
│   └── wishlist/                    # ProductMetadataExtractor (OpenGraph & HTML)
├── ui/                              # CAPA DE PRESENTACIÓN (UI / Presentation Layer)
│   ├── backup/                      # Diálogo y ViewModel de Copia de Seguridad
│   ├── closet/                      # Pantalla de Armario Digital con toggle de IA
│   ├── inspiration/                 # Lookbook de Inspiración y ViewModel
│   ├── logfavorites/                # Diario de Outfits, Favoritos e Historial
│   ├── main/                        # Navegación principal (Scaffold, Bottom Navigation Bar)
│   ├── recommendation/              # Pantalla del Recomendador con IA y ViewModel
│   ├── theme/                       # Paleta Material 3, tipografías y Dynamic Color
│   └── wishlist/                    # Pantalla de Lista de Deseos y ViewModel
└── util/                            # Utilidades de persistencia, FileProvider y URIs de imágenes
```

---

## 🚀 Instalación y Desarrollo

### Requisitos
- Android Studio Ladybug, Koala o versión reciente.
- Android SDK con soporte para Android 11+ (API 30+) hasta Android 16 (API 36).
- Java 17 / JDK 17 o superior.

### Pasos
1. **Clonar el repositorio:**
   ```bash
   git clone https://github.com/mulineee21/MyOutfit.git
   cd MyOutfit
   ```

2. **Abrir en Android Studio:**
   - Selecciona **File -> Open...** y elige la carpeta `MyOutfit`.
   - Deja que Android Studio descargue las dependencias de Gradle y sincronice el proyecto.

3. **Compilar y Ejecutar:**
   ```bash
   ./gradlew assembleDebug
   ```

---

## 📄 Licencia

Este proyecto está distribuido bajo la licencia **GNU General Public License v3.0 o superior (GPL-3.0-or-later)**.

```text
MyOutfit — App de recomendación inteligente de ropa para Android
Copyright (C) 2026 Joel Molina (@mulineee21)

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

Consulta el archivo [LICENSE](LICENSE) para ver el texto legal completo de la licencia GPL v3.
