# Project Plan

App para Android 'MyOutfit' que selecciona el outfit diario del usuario.
Características principales:
1. Armario digital (Digital Closet): El usuario toma fotos de su ropa, guardadas localmente en el dispositivo, categorizadas por tipo (Camisetas, Camisas, Pantalones, Calzado, Chaquetas, Accesorios, etc.).
2. Selección de Outfit por IA / Algoritmo Inteligente: Genera combinaciones diarias según la ropa disponible en el armario y los gustos/estilos seleccionados por el usuario (Gótico, Colorido, Casual, Formal, Streetwear, Vintage, etc.), clima/ocasión, etc.
3. Inspiración y Lookbook (Inspiration Board): Categoría donde el usuario puede guardar imágenes de outfits que le gustan (famosos, influencers, revistas). La IA analiza o toma en cuenta estas referencias/estilos guardados para refinar las recomendaciones de conjuntos con la ropa real del usuario.
4. Interfaz moderna, intuitiva, responsive y atractiva con Jetpack Compose, almacenamiento local (Room / Datastore / almacenamiento de imágenes en almacenamiento interno), integración con IA/algoritmo inteligente para matching de ropa y colores.

## Project Brief

# Project Brief: MyOutfit

## Features
1. **Digital Closet (Armario Digital)**: Capture, categorize (Tops, Bottoms, Footwear, Outerwear, Accessories), and manage personal clothing items stored locally on the device.
2. **AI Outfit Recommendation Engine**: Generate smart daily outfit combinations matching user clothes with selected style preferences (Casual, Formal, Streetwear, Vintage, Goth, etc.), weather, and occasion.
3. **Inspiration Board (Lookbook)**: Save reference outfit images from external sources to train AI styling preferences and refine recommendation logic.
4. **Daily Outfit Log & Favorites**: Save top combinations, view historical outfit choices, and easily plan daily wear schedules.

## High-Level Tech Stack
* **Language**: Kotlin
* **UI Framework**: Jetpack Compose (Material Design 3)
* **Navigation & Adaptive Strategy**:
  * **Jetpack Navigation 3** (state-driven architecture)
  * **Compose Material Adaptive** library for screen-size responsiveness and multi-pane support
* **Core & Architecture**:
  * Clean Architecture with MVVM / Unidirectional Data Flow (UDF)
  * Kotlin Coroutines & Flow for asynchronous operations
* **Persistence & Local Storage**:
  * **Room Database**: Structured metadata (categories, clothing tags, outfit history, inspiration notes)
  * **Jetpack DataStore**: User preferences and style profiles
  * **Android Internal Storage**: Local image persistence for wardrobe & inspiration photos
  * **Coil**: Async image loading and caching

## Implementation Steps
**Total Duration:** 22m 12s

### Task_1_DataAndDomainLayer: Setup Room database entities (ClothingItem, Outfit, InspirationItem, LoggedOutfit), DAOs, DataStore repository for style preferences, and internal storage image helper.
- **Status:** COMPLETED
- **Updates:** Entidades Room (ClothingItem, Outfit, InspirationItem, LoggedOutfit), DAOs, MyOutfitDatabase con TypeConverters, UserPreferencesRepository con DataStore, e ImageStorageManager para fotos locales implementados correctamente. Pruebas unitarias ejecutadas con éxito y build `./gradlew assembleDebug` pasando sin errores.
- **Acceptance Criteria:**
  - Room Database with Clothing, Outfit, Inspiration, and Log entities and DAOs implemented
  - DataStore configured for user style preferences
  - Image storage helper created for internal storage photo management
  - Build succeeds without errors
- **Duration:** 6m 53s

### Task_2_DigitalClosetAndLookbookUI: Implement Digital Closet screen for viewing, adding, and categorizing clothing items, and Inspiration Board (Lookbook) screen with Jetpack Compose.
- **Status:** COMPLETED
- **Updates:** Digital Closet UI (DigitalClosetScreen, DigitalClosetViewModel, AddClothingDialog con captura de cámara/galería) e Inspiration Board / Lookbook UI (InspirationLookbookScreen, InspirationViewModel, AddInspirationDialog) implementadas con Jetpack Compose y Coil. Navegación principal agregada y FileProvider configurado. Compilación y tests unitarios pasados exitosamente.
- **Acceptance Criteria:**
  - Digital Closet UI displays categorized items with photo capture/picker support
  - Inspiration Board UI supports viewing and adding reference outfit images
  - State management and ViewModel bindings complete for Closet and Lookbook
- **Duration:** 4m 58s

### Task_3_OutfitRecommendationAndLogUI: Implement AI Recommendation Engine screen (generating outfit combinations based on style preferences and clothing items) and Daily Outfit Log & Favorites screen with Navigation.
- **Status:** COMPLETED
- **Updates:** Motor de recomendación de IA (OutfitRecommendationEngine) con cálculo de coincidencia de colores y estilos guardados en DataStore e Inspiration Board implementado. Interfaz RecommendationScreen, Diario y Favoritos (LogAndFavoritesScreen) con 4 pestañas en Bottom Navigation integradas. Build assembleDebug y tests unitarios 18/18 pasando.
- **Acceptance Criteria:**
  - AI Recommendation Engine generates outfit combinations based on closet items and style preferences
  - Daily Outfit Log & Favorites UI allows saving, viewing history, and favoriting outfits
  - Jetpack Navigation set up connecting Closet, Recommendation Engine, Lookbook, and Log screens
- **Duration:** 5m 20s

### Task_4_RunAndVerify: Run the application, verify application stability, instruct critic_agent to verify app stability (no crashes), confirm alignment with user requirements, and report critical UI issues.
- **Status:** COMPLETED
- **Updates:** Critic Agent ha verificado la instalación, ejecución y funcionalidad de MyOutfit en el emulador/dispositivo. No hay crashes (100% estable), las 4 secciones principales (Armario Digital, Recomendador IA con estilos Gótico/Colorido/Casual, Tablón de Inspiración y Diario/Favoritos) funcionan correctamente con interfaz Material Design 3. Quality Gate APROBADO.
- **Acceptance Criteria:**
  - Build pass
  - App does not crash
  - Make sure all existing tests pass
  - critic_agent verifies application stability and reports any issues
- **Duration:** 5m 1s

