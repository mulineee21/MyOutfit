# MyOutfit 👗👔

**MyOutfit** es una aplicación moderna para Android desarrollada en Kotlin y Jetpack Compose que selecciona y recomienda tu outfit diario de forma personalizada e inteligente, adaptándose a:
- 🎯 **Ocasión y propósito** (Casual, Trabajo/Formal, Fiesta, Deportes, etc.)
- 🌤️ **Clima y temporada**
- 🎨 **Estilo personal** (Streetwear, Casual, Elegante, Vintage, Gótico, Colorido, etc.)
- 👕 **Tu propio armario**: Selecciona combinaciones utilizando únicamente las prendas que tienes registradas en tu armario digital.

---

## ✨ Características Principales

1. **🚪 Armario Digital (Digital Closet):**
   - Captura y almacena fotos de tus prendas directamente en el almacenamiento interno de tu dispositivo.
   - Categorización detallada de prendas: Camisetas, Camisas, Tops, Sudaderas/Suéteres, Vestidos, Monos, Pantalones, Vaqueros, Faldas, Shorts, Abrigos/Chaquetas, Calzado, Bolsos y Accesorios.
   - Edición completa de prendas existentes (cambio de foto, categoría, color, etiquetas de estilo y descripción).
   - Asignación de colores, estilos y etiquetas para un matching preciso.

2. **🤖 Recomendaciones con Inteligencia Artificial (Google Gemini AI):**
   - Integración nativa con la API de Google Gemini (`gemini-1.5-flash`).
   - Personalización según tus planes del día (ej. ir a la oficina, al gym, salir de fiesta, cena formal, etc.).
   - Personalización según tu estilo/vibe deseado (ej. elegante, cómodo, llamativo, minimalista, etc.).
   - Interruptor para alinear la recomendación con tus referencias del Tablero de Inspiración (Lookbook).
   - Generación de consejos de estilo prácticos personalizados para cada outfit.
   - Motor heurístico local alternativo para sugerencias instantáneas por estilo.

3. **💡 Tablero de Inspiración (Inspiration Lookbook):**
   - Guarda imágenes de referencia (revistas, redes sociales, influencers).
   - Sirve como guía de estilo para refinar tus preferencias y gustos.

4. **⭐ Registro de Outfits y Favoritos:**
   - Marca tus combinaciones favoritas para acceder a ellas rápidamente.
   - Historial de outfits usados por día para evitar repetir y planificar la semana.

---

## 🛠️ Stack Tecnológico

- **Lenguaje:** Kotlin
- **UI:** Jetpack Compose (Material Design 3)
- **Arquitectura:** Clean Architecture con patrón MVVM / Unidirectional Data Flow (UDF)
- **Asincronía:** Kotlin Coroutines & Flow
- **Base de datos local:** Room Database (con TypeConverters)
- **Preferencias:** Jetpack DataStore Preferences
- **Carga de imágenes:** Coil (Compose)
- **Gestión de archivos:** Android Internal Storage + FileProvider para captura con cámara

---

## 🚀 Cómo abrir y trabajar en otro ordenador

Para continuar el desarrollo en otro ordenador:

1. **Clonar el repositorio:**
   ```bash
   git clone https://github.com/mulineee21/MyOutfit.git
   cd MyOutfit
   ```

2. **Abrir en Android Studio:**
   - Abre Android Studio (Ladybug / Koala o versión reciente).
   - Selecciona **File -> Open...** y elige la carpeta `MyOutfit`.
   - Deja que Android Studio descargue las dependencias de Gradle y configure el SDK local automáticamente (`local.properties` se generará solo).

3. **Compilar y Ejecutar:**
   - Selecciona un emulador con Android 8.0+ (API 26+) o un dispositivo físico conectado en modo depuración USB.
   - Presiona **Run** (`Shift + F10`) o compila desde la terminal:
     ```bash
     ./gradlew assembleDebug
     ```

---

## 📂 Estructura del Proyecto

```text
app/src/main/java/com/example/myoutfit/
├── MyOutfitApplication.kt
├── MainActivity.kt
├── data/
│   ├── local/
│   │   ├── converters/      # Conversores para Room
│   │   ├── dao/             # Data Access Objects (DAOs)
│   │   ├── db/              # MyOutfitDatabase (Room)
│   │   ├── entity/          # Entidades (ClothingItem, Outfit, LoggedOutfit, etc.)
│   │   └── preferences/     # UserPreferencesRepository (DataStore)
│   └── repository/          # Repositorios de datos
├── domain/
│   └── recommendation/      # Motor de recomendación inteligente de outfits
├── ui/
│   ├── closet/              # Armario Digital (Pantalla, ViewModel, Diálogos)
│   ├── inspiration/         # Lookbook / Inspiración
│   ├── logfavorites/        # Favoritos e Historial
│   ├── main/                # Navegación y pantallas principales
│   ├── recommendation/      # Pantalla del generador de outfits
│   └── theme/               # Temas, tipografía y paleta de colores Material 3
└── util/                    # Utilidades de imágenes, URI y almacenamiento interno
```
