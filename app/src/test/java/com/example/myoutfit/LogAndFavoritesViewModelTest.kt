package com.example.myoutfit

import com.example.myoutfit.data.local.entity.ClothingCategory
import com.example.myoutfit.data.local.entity.ClothingItem
import com.example.myoutfit.data.local.entity.Outfit
import com.example.myoutfit.ui.logfavorites.LogAndFavoritesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogAndFavoritesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeClothingRepository = FakeClothingRepository()
    private val fakeOutfitRepository = FakeOutfitRepository()
    private val fakeLoggedOutfitRepository = FakeLoggedOutfitRepository()

    private lateinit var viewModel: LogAndFavoritesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        val item1 = ClothingItem(
            id = 1,
            imagePath = "top.jpg",
            category = ClothingCategory.TOPS,
            color = "Negro",
            styleTags = listOf("Casual"),
            description = "Camiseta negra"
        )
        fakeClothingRepository.setItems(listOf(item1))

        viewModel = LogAndFavoritesViewModel(
            loggedOutfitRepository = fakeLoggedOutfitRepository,
            outfitRepository = fakeOutfitRepository,
            clothingRepository = fakeClothingRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun logCustomOutfit_addsEntryToLoggedOutfits() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.logCustomOutfit(
            selectedItemIds = listOf(1L),
            rating = 4.5f,
            notes = "Día casual"
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.loggedOutfits.size)
        assertEquals(4.5f, state.loggedOutfits.first().loggedOutfit.rating, 0.01f)
        assertEquals("Día casual", state.loggedOutfits.first().loggedOutfit.notes)
    }

    @Test
    fun deleteLoggedOutfit_removesEntryFromRepository() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.logCustomOutfit(
            selectedItemIds = listOf(1L),
            rating = 5.0f,
            notes = "Look top"
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val id = viewModel.uiState.value.loggedOutfits.first().loggedOutfit.id

        viewModel.deleteLoggedOutfit(id)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.loggedOutfits.isEmpty())
    }

    @Test
    fun toggleFavoriteStatus_updatesFavoriteInRepository() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        val favOutfit = Outfit(
            id = 10,
            title = "Look Gótico",
            clothingItemIds = listOf(1L),
            styleTag = "Gótico",
            isFavorite = true
        )
        fakeOutfitRepository.insertOutfit(favOutfit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.favoriteOutfits.size)

        viewModel.toggleFavoriteStatus(10L, false)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.favoriteOutfits.isEmpty())
    }
}
