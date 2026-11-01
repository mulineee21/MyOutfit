# MyOutfit 👗👔

[![Autor](https://img.shields.io/badge/Autor-Joel%20Molina%20(@mulineee21)-8b5cf6?style=for-the-badge&logo=github)](https://github.com/mulineee21)
[![Licencia](https://img.shields.io/badge/Licencia-GPL%20v3%20o%20superior-blue.svg?style=for-the-badge)](LICENSE)
[![Sitio Web Oficial](https://img.shields.io/badge/Web%20Oficial-mulineee21.github.io%2Fmyoutfit--web-ec4899?style=for-the-badge)](https://mulineee21.github.io/myoutfit-web/)
[![Release Oficial](https://img.shields.io/badge/Release-v1.0.0-emerald.svg?style=for-the-badge&logo=android)](https://github.com/mulineee21/MyOutfit/releases/tag/v1.0.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7f52ff?style=for-the-badge&logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285f4?style=for-the-badge&logo=android)](https://developer.android.com/jetpack/compose)

> **MyOutfit** es una aplicación moderna y nativa para Android creada por **Joel Molina**, desarrollada en **Kotlin** y **Jetpack Compose**. Integra la Inteligencia Artificial de **Google Gemini** para seleccionar y recomendarte qué ropa ponerte cada día de manera personalizada, combinando de forma inteligente las prendas reales que tienes registradas en tu propio armario.

🌐 **Sitio Web Oficial & Demo:** [https://mulineee21.github.io/myoutfit-web/](https://mulineee21.github.io/myoutfit-web/)  
📦 **Descarga Directa de APK (v1.0.0):** [MyOutfit-JoelMolina-v1.0.0.apk](https://github.com/mulineee21/MyOutfit/releases/download/v1.0.0/MyOutfit-JoelMolina-v1.0.0.apk)

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
* **Capa de Dominio (`domain/`):** Contiene la lógica pura de negocio y las reglas de recomendación.
  - `OutfitRecommendationEngine`: Algoritmo heurístico local para matching de prendas según ocasión, temporadas, compatibilidad de colores y tipos de piezas.
  - `GeminiOutfitService`: Servicio de conexión, formulación de prompts estructurados y parsing seguro JSON con la API de Google Gemini (`gemini-1.5-flash`).
* **Capa de Datos (`data/`):** Implementación de persistencia local y repositorios concretos (`ClothingRepository`, `OutfitRepository`, `InspirationRepository`, `LoggedOutfitRepository`).

### 2. Patrón MVVM (Model-View-ViewModel)
* **Separación de responsabilidades:** La interfaz de usuario observa los cambios de estado emitidos por los `ViewModel` (`DigitalClosetViewModel`, `RecommendationViewModel`, `InspirationViewModel`, `LogAndFavoritesViewModel`).
* **Supervivencia a cambios de configuración:** Los ViewModels gestionan el ciclo de vida de la actividad y la ejecución asíncrona mediante `viewModelScope`.

### 3. Flujo Unidireccional de Datos (UDF - Unidirectional Data Flow)
* **Estado inmutable:** Los datos fluyen en una sola dirección mediante `StateFlow` y `asStateFlow()`.
* **Eventos explícitos:** Las acciones de la UI se transmiten a los ViewModels a través de funciones de eventos claramente definidas, evitando mutaciones concurrentes descontroladas.

### 4. Repository Pattern (Patrón Repositorio)
* Actúa como única fuente de la verdad (*Single Source of Truth*), abstrayendo a los casos de uso y ViewModels de los detalles de implementación de la base de datos o almacenamiento en disco.

### 5. Arquitectura Offline-First
* **Persistencia Total Local:** Todo el armario, lookbook y registro de outfits se guarda en una base de datos local SQLite mediante **Room Database**.
* **Privacidad por Diseño:** Las fotos de las prendas se gestionan mediante el almacenamiento interno del dispositivo (`FileProvider`), garantizando que las imágenes no salgan del móvil ni se suban a servidores ajenos.
* **Jetpack DataStore Preferences:** Para almacenar de manera reactiva y segura las preferencias de usuario, estilos favoritos y la API Key de Gemini.

### 6. Concurrencia Estructurada y Programación Reactiva
* **Kotlin Coroutines:** Para la gestión eficiente y no bloqueante de tareas en segundo plano (consultas IO a Room, llamadas HTTP a Gemini, procesamiento de imágenes).
* **Kotlin Flow:** Para la propagación reactiva en tiempo real de los datos desde la base de datos hasta la interfaz de usuario.

---

## ✨ Características Principales

1. **🚪 Armario Digital Privado (Digital Closet):**
   - Captura y almacena fotos de tus prendas directamente en el almacenamiento interno de tu dispositivo.
   - Categorización detallada: *Camisetas, Camisas, Tops, Sudaderas/Suéteres, Vestidos, Monos, Pantalones, Vaqueros, Faldas, Shorts, Abrigos/Chaquetas, Calzado, Bolsos y Accesorios*.
   - Edición completa de prendas existentes (cambio de foto, categoría, color, etiquetas de estilo y descripción).
   - Asignación de colores, estilos y etiquetas para un matching preciso.

2. **🤖 Recomendaciones de Ropa con Google Gemini IA:**
   - Integración nativa con la API de Google Gemini (`gemini-1.5-flash`).
   - Personalización según tus planes del día (oficina, gym, fiesta, cena formal, relax).
   - Personalización según tu estilo o vibe deseado (elegante, cómodo, llamativo, minimalista, etc.).
   - Conexión con el Tablero de Inspiración (Lookbook) para alinear la recomendación con tus referencias.
   - Generación de consejos de estilo prácticos personalizados para cada outfit.
   - **Motor Heurístico Local:** Motor alternativo para sugerencias instantáneas por estilo que funciona 100% offline sin necesidad de conexión ni consumo de tokens.

3. **💡 Tablero de Inspiración (Inspiration Lookbook):**
   - Guarda imágenes de referencia (revistas, redes sociales, influencers).
   - Sirve como guía de estilo para refinar tus preferencias y gustos.

4. **⭐ Diario de Outfits & Anti-Repetición:**
   - Historial de outfits usados por día para evitar repetir prendas en la misma semana y planificar tu estilo.
   - Marcador de outfits favoritos para acceder a ellos rápidamente.

---

## 🛠️ Stack Tecnológico

| Componente | Tecnología |
| :--- | :--- |
| **Lenguaje** | Kotlin 100% |
| **Interfaz de Usuario** | Jetpack Compose + Material Design 3 (Material You) |
| **Arquitectura** | Clean Architecture + MVVM + UDF + Repository Pattern |
| **Inteligencia Artificial** | Google Gemini API (`gemini-1.5-flash`) |
| **Base de Datos** | Room Database (SQLite) + TypeConverters |
| **Preferencias** | Jetpack DataStore Preferences |
| **Asincronía & Reactividad** | Kotlin Coroutines & StateFlow / Flow |
| **Carga de Imágenes** | Coil (Compose) |
| **Cliente HTTP** | OkHttp3 |
| **Almacenamiento de Archivos** | Android Internal Storage + FileProvider |

---

## 📂 Estructura del Código Fuente

```text
app/src/main/java/com/example/myoutfit/
├── MyOutfitApplication.kt           # Inicialización de dependencias y repositorios de la app
├── MainActivity.kt                  # Activity principal contenedora de Compose
├── data/                            # CAPA DE DATOS (Data Layer)
│   ├── local/
│   │   ├── converters/              # Conversores de tipos para Room
│   │   ├── dao/                     # Data Access Objects (ClothingDao, OutfitDao, etc.)
│   │   ├── db/                      # MyOutfitDatabase (Room)
│   │   ├── entity/                  # Entidades (ClothingItem, Outfit, LoggedOutfit, etc.)
│   │   └── preferences/             # UserPreferencesRepository (DataStore)
│   └── repository/                  # Repositorios concretos que implementan la lógica de datos
├── domain/                          # CAPA DE DOMINIO (Domain Layer)
│   └── recommendation/              # Motores de recomendación (GeminiOutfitService & OutfitRecommendationEngine)
├── ui/                              # CAPA DE PRESENTACIÓN (UI / Presentation Layer)
│   ├── closet/                      # Pantalla de Armario Digital y ViewModel
│   ├── inspiration/                 # Lookbook de Inspiración y ViewModel
│   ├── logfavorites/                # Diario de Outfits, Favoritos e Historial
│   ├── main/                        # Navegación principal (Scaffold, Bottom Navigation Bar)
│   ├── recommendation/              # Pantalla del Recomendador con IA y ViewModel
│   └── theme/                       # Paleta Material 3, tipografías y soporte de Dynamic Color
└── util/                            # Utilidades de persistencia, FileProvider y URIs de imágenes
```

---

## 🚀 Instalación y Desarrollo

### Requisitos
- Android Studio Ladybug, Koala o versión reciente.
- Android SDK con soporte para Android 8.0 Oreo (API 26+) o superior.
- Java 17 / JDK 17 o superior.

### Pasos
1. **Clonar el repositorio:**
   ```bash
   git clone https://github.com/mulineee21/MyOutfit.git
   cd MyOutfit
   ```

2. **Abrir en Android Studio:**
   - Selecciona **File -> Open...** y elige la carpeta `MyOutfit`.
   - Deja que Android Studio descargue las dependencias de Gradle y configure el SDK local automáticamente.

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
