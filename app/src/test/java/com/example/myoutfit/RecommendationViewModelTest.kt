package com.example.myoutfit

import com.example.myoutfit.data.local.entity.ClothingCategory
import com.example.myoutfit.data.local.entity.ClothingItem
import com.example.myoutfit.data.local.entity.LoggedOutfit
import com.example.myoutfit.data.local.entity.Outfit
import com.example.myoutfit.data.local.preferences.UserPreferences
import com.example.myoutfit.data.local.preferences.UserPreferencesRepository
import com.example.myoutfit.data.repository.LoggedOutfitRepository
import com.example.myoutfit.data.repository.OutfitRepository
import com.example.myoutfit.ui.recommendation.RecommendationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecommendationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeClothingRepository = FakeClothingRepository()
    private val fakeInspirationRepository = FakeInspirationRepository()
    private val fakeUserPreferencesRepository = FakeUserPreferencesRepository()
    private val fakeOutfitRepository = FakeOutfitRepository()
    private val fakeLoggedOutfitRepository = FakeLoggedOutfitRepository()

    private lateinit var viewModel: RecommendationViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        val top = ClothingItem(
            id = 1,
            imagePath = "top.jpg",
            category = ClothingCategory.TOPS,
            color = "Negro",
            styleTags = listOf("Gótico"),
            description = "Camiseta negra"
        )
        val bottom = ClothingItem(
            id = 2,
            imagePath = "bottom.jpg",
            category = ClothingCategory.BOTTOMS,
            color = "Negro",
            styleTags = listOf("Gótico"),
            description = "Pantalón de cuero"
        )
        fakeClothingRepository.setItems(listOf(top, bottom))

        viewModel = RecommendationViewModel(
            clothingRepository = fakeClothingRepository,
            inspirationRepository = fakeInspirationRepository,
            userPreferencesRepository = fakeUserPreferencesRepository,
            outfitRepository = fakeOutfitRepository,
            loggedOutfitRepository = fakeLoggedOutfitRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectStyleFilter_updatesSelectedStyleAndRecommendation() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectStyleFilter("Gótico")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Gótico", state.selectedStyleFilter)
        assertNotNull(state.currentRecommendation)
        assertEquals("Gótico", state.currentRecommendation?.styleTag)
    }

    @Test
    fun saveCurrentToFavorites_insertsOutfitToRepository() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectStyleFilter("Gótico")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveCurrentToFavorites()
        testDispatcher.scheduler.advanceUntilIdle()

        val favorites = fakeOutfitRepository.getFavoritesSync()
        assertEquals(1, favorites.size)
        assertTrue(favorites.first().isFavorite)
    }

    @Test
    fun logCurrentAsWorn_insertsLoggedOutfitToRepository() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectStyleFilter("Gótico")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.logCurrentAsWorn(rating = 5.0f, notes = "Excelente look")
        testDispatcher.scheduler.advanceUntilIdle()

        val loggedList = fakeLoggedOutfitRepository.getLoggedSync()
        assertEquals(1, loggedList.size)
        assertEquals(5.0f, loggedList.first().rating, 0.01f)
        assertEquals("Excelente look", loggedList.first().notes)
    }

    @Test
    fun geminiInputs_updateUiStateProperly() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setActivityPlan("Ir al gym y luego oficina")
        viewModel.setDesiredVibe("Deportivo pero formal")
        viewModel.setUseInspirations(false)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Ir al gym y luego oficina", state.activityPlan)
        assertEquals("Deportivo pero formal", state.desiredVibe)
        assertEquals(false, state.useInspirations)
    }

    @Test
    fun saveGeminiApiKey_updatesPreferencesAndState() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveGeminiApiKey("test-gemini-key-12345")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("test-gemini-key-12345", state.userPreferences.geminiApiKey)
        assertEquals(false, state.showApiKeyDialog)
    }

    @Test
    fun generateGeminiRecommendation_withoutApiKey_opensApiKeyDialog() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.generateGeminiRecommendation()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.showApiKeyDialog)
    }
}

class FakeUserPreferencesRepository : UserPreferencesRepository {
    private val _prefs = MutableStateFlow(UserPreferences())

    override val userPreferencesFlow: Flow<UserPreferences> = _prefs

    override suspend fun updateFavoriteStyles(styles: Set<String>) {
        _prefs.value = _prefs.value.copy(favoriteStyles = styles)
    }

    override suspend fun addFavoriteStyle(style: String) {
        _prefs.value = _prefs.value.copy(favoriteStyles = _prefs.value.favoriteStyles + style)
    }

    override suspend fun removeFavoriteStyle(style: String) {
        _prefs.value = _prefs.value.copy(favoriteStyles = _prefs.value.favoriteStyles - style)
    }

    override suspend fun updateSelectedTheme(theme: String) {
        _prefs.value = _prefs.value.copy(selectedTheme = theme)
    }

    override suspend fun updateGeminiApiKey(apiKey: String) {
        _prefs.value = _prefs.value.copy(geminiApiKey = apiKey)
    }
}

class FakeOutfitRepository : OutfitRepository {
    private val _outfits = MutableStateFlow<List<Outfit>>(emptyList())

    fun getFavoritesSync(): List<Outfit> = _outfits.value.filter { it.isFavorite }

    override fun getAllOutfits(): Flow<List<Outfit>> = _outfits

    override suspend fun getOutfitById(id: Long): Outfit? = _outfits.value.find { it.id == id }

    override fun getFavoriteOutfits(): Flow<List<Outfit>> {
        return _outfits.map { list -> list.filter { it.isFavorite } }
    }

    override fun getOutfitsByStyleTag(styleTag: String): Flow<List<Outfit>> {
        return _outfits.map { list -> list.filter { it.styleTag == styleTag } }
    }

    override suspend fun insertOutfit(outfit: Outfit): Long {
        val newId = if (outfit.id == 0L) (System.currentTimeMillis()) else outfit.id
        val newOutfit = outfit.copy(id = newId)
        _outfits.value = _outfits.value + newOutfit
        return newId
    }

    override suspend fun updateOutfit(outfit: Outfit) {
        _outfits.value = _outfits.value.map { if (it.id == outfit.id) outfit else it }
    }

    override suspend fun deleteOutfit(outfit: Outfit) {
        _outfits.value = _outfits.value.filter { it.id != outfit.id }
    }

    override suspend fun deleteOutfitById(id: Long) {
        _outfits.value = _outfits.value.filter { it.id != id }
    }

    override suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean) {
        _outfits.value = _outfits.value.map { if (it.id == id) it.copy(isFavorite = isFavorite) else it }
    }
}

class FakeLoggedOutfitRepository : LoggedOutfitRepository {
    private val _logged = MutableStateFlow<List<LoggedOutfit>>(emptyList())

    fun getLoggedSync(): List<LoggedOutfit> = _logged.value

    override fun getAllLoggedOutfits(): Flow<List<LoggedOutfit>> = _logged

    override suspend fun getLoggedOutfitById(id: Long): LoggedOutfit? = _logged.value.find { it.id == id }

    override fun getLoggedOutfitsForDateRange(startDate: Long, endDate: Long): Flow<List<LoggedOutfit>> {
        return MutableStateFlow(_logged.value.filter { it.dateUsed in startDate..endDate })
    }

    override suspend fun insertLoggedOutfit(loggedOutfit: LoggedOutfit): Long {
        val newId = if (loggedOutfit.id == 0L) System.currentTimeMillis() else loggedOutfit.id
        val item = loggedOutfit.copy(id = newId)
        _logged.value = _logged.value + item
        return newId
    }

    override suspend fun updateLoggedOutfit(loggedOutfit: LoggedOutfit) {
        _logged.value = _logged.value.map { if (it.id == loggedOutfit.id) loggedOutfit else it }
    }

    override suspend fun deleteLoggedOutfit(loggedOutfit: LoggedOutfit) {
        _logged.value = _logged.value.filter { it.id != loggedOutfit.id }
    }

    override suspend fun deleteLoggedOutfitById(id: Long) {
        _logged.value = _logged.value.filter { it.id != id }
    }
}
