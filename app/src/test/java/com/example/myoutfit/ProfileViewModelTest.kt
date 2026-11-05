package com.example.myoutfit

import com.example.myoutfit.data.local.entity.ClothingCategory
import com.example.myoutfit.data.local.entity.ClothingItem
import com.example.myoutfit.data.local.entity.LoggedOutfit
import com.example.myoutfit.data.local.entity.Outfit
import com.example.myoutfit.data.local.entity.WishlistItem
import com.example.myoutfit.ui.profile.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeUserPreferencesRepository = FakeUserPreferencesRepository()
    private val fakeLoggedOutfitRepository = FakeLoggedOutfitRepository()
    private val fakeOutfitRepository = FakeOutfitRepository()
    private val fakeClothingRepository = FakeClothingRepository()
    private val fakeWishlistRepository = FakeWishlistRepository()

    private lateinit var viewModel: ProfileViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeClothingRepository.setItems(
            listOf(
                ClothingItem(
                    id = 1,
                    imagePath = "path1.jpg",
                    category = ClothingCategory.TOPS,
                    color = "Negro",
                    styleTags = listOf("Casual"),
                    description = "Camiseta"
                ),
                ClothingItem(
                    id = 2,
                    imagePath = "path2.jpg",
                    category = ClothingCategory.BOTTOMS,
                    color = "Azul",
                    styleTags = listOf("Casual"),
                    description = "Pantalón"
                )
            )
        )

        fakeWishlistRepository.setItems(
            listOf(
                WishlistItem(
                    id = 1,
                    title = "Chaqueta cuero",
                    productUrl = "https://example.com/item"
                )
            )
        )

        viewModel = ProfileViewModel(
            userPreferencesRepository = fakeUserPreferencesRepository,
            loggedOutfitRepository = fakeLoggedOutfitRepository,
            outfitRepository = fakeOutfitRepository,
            clothingRepository = fakeClothingRepository,
            wishlistRepository = fakeWishlistRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_reflectsDataStoreAndRepoStats() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }

        fakeLoggedOutfitRepository.insertLoggedOutfit(
            LoggedOutfit(
                id = 1,
                clothingItemIds = listOf(1L, 2L),
                rating = 5f,
                notes = "Genial"
            )
        )
        fakeOutfitRepository.insertOutfit(
            Outfit(
                id = 1,
                title = "Look 1",
                clothingItemIds = listOf(1L, 2L),
                styleTag = "Casual",
                isFavorite = true
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Joel Molina", state.userName)
        assertEquals(1, state.loggedOutfitsCount)
        assertEquals(1, state.savedOutfitsCount)
        assertEquals(1, state.favoriteOutfitsCount)
        assertEquals(2, state.closetItemsCount)
        assertEquals(1, state.wishlistItemsCount)
    }

    @Test
    fun updateProfile_updatesNameAndBio() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openEditDialog()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isEditDialogOpen)

        viewModel.updateProfile("Joel M.", "Amante del estilo urbano y minimalista")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Joel M.", state.userName)
        assertEquals("Amante del estilo urbano y minimalista", state.bio)
        assertFalse(state.isEditDialogOpen)
        assertEquals("Perfil actualizado con éxito", state.userMessage)
    }

    @Test
    fun removeProfilePhoto_clearsImagePath() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        fakeUserPreferencesRepository.updateProfileImagePath("file:///storage/profile.jpg")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("file:///storage/profile.jpg", viewModel.uiState.value.profileImagePath)

        viewModel.removeProfilePhoto()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.profileImagePath)
        assertEquals("Foto de perfil eliminada", viewModel.uiState.value.userMessage)
    }

    @Test
    fun toggleAutoCategorize_updatesPreference() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.autoCategorizeWithAi)

        viewModel.toggleAutoCategorize(false)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.autoCategorizeWithAi)
    }

    @Test
    fun updateGeminiApiKey_updatesApiKey() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openApiKeyDialog()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isApiKeyDialogOpen)

        viewModel.updateGeminiApiKey("AIzaSyFakeKey12345")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("AIzaSyFakeKey12345", state.geminiApiKey)
        assertFalse(state.isApiKeyDialogOpen)
    }

    @Test
    fun socialConnectedProfile_syncsAccountNameAndEnforcesMonthlyCooldown() = runTest {
        val fakeSocial = FakeSocialRepository()
        fakeSocial.login("joelmolinanavarro21", "pass1234")

        val syncedVm = ProfileViewModel(
            userPreferencesRepository = fakeUserPreferencesRepository,
            loggedOutfitRepository = fakeLoggedOutfitRepository,
            outfitRepository = fakeOutfitRepository,
            clothingRepository = fakeClothingRepository,
            wishlistRepository = fakeWishlistRepository,
            socialRepository = fakeSocial
        )

        backgroundScope.launch { syncedVm.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. El perfil refleja automáticamente el nombre de la cuenta activa
        assertEquals("joelmolinanavarro21", syncedVm.uiState.value.userName)
        assertTrue(syncedVm.uiState.value.canChangeUsername)

        // 2. Primer cambio de nombre: permitido y activa bloqueo de 30 días
        syncedVm.openChangeUsernameDialog()
        syncedVm.changeAccountUsername("joel_molina")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("joel_molina", syncedVm.uiState.value.userName)
        assertFalse(syncedVm.uiState.value.canChangeUsername)
        assertEquals(30, syncedVm.uiState.value.usernameCooldownDaysRemaining)

        // 3. Segundo intento en el mismo mes: rechazado por el límite mensual
        syncedVm.openChangeUsernameDialog()
        syncedVm.changeAccountUsername("otro_nombre")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("joel_molina", syncedVm.uiState.value.userName)
        assertTrue(syncedVm.uiState.value.usernameDialogError?.contains("1 vez al mes") == true)
    }

    @Test
    fun deleteSocialAccount_executesGdprDeletionAndLogsOut() = runTest {
        val fakeSocial = FakeSocialRepository()
        fakeSocial.login("joel_rgpd", "pass1234")

        val syncedVm = ProfileViewModel(
            userPreferencesRepository = fakeUserPreferencesRepository,
            loggedOutfitRepository = fakeLoggedOutfitRepository,
            outfitRepository = fakeOutfitRepository,
            clothingRepository = fakeClothingRepository,
            wishlistRepository = fakeWishlistRepository,
            socialRepository = fakeSocial
        )

        backgroundScope.launch { syncedVm.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(syncedVm.uiState.value.isSocialConnected)

        syncedVm.deleteSocialAccount()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeSocial.deleteAccountCalled)
        assertFalse(syncedVm.uiState.value.isSocialConnected)
        assertTrue(syncedVm.uiState.value.userMessage?.contains("Art. 17 RGPD") == true)
    }
}
