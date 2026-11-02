package com.example.myoutfit

import android.content.Context
import com.example.myoutfit.data.local.entity.ClothingCategory
import com.example.myoutfit.data.local.entity.ClothingItem
import com.example.myoutfit.data.local.entity.WishlistItem
import com.example.myoutfit.data.repository.ClothingRepository
import com.example.myoutfit.data.repository.WishlistRepository
import com.example.myoutfit.domain.wishlist.ProductMetadataExtractor
import com.example.myoutfit.ui.wishlist.WishlistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

class FakeWishlistRepository : WishlistRepository {
    private val _items = MutableStateFlow<List<WishlistItem>>(emptyList())
    private var nextId = 1L

    fun setItems(items: List<WishlistItem>) {
        _items.value = items
    }

    override fun getAllWishlistItems(): Flow<List<WishlistItem>> = _items

    override suspend fun getWishlistItemById(id: Long): WishlistItem? =
        _items.value.find { it.id == id }

    override suspend fun insertWishlistItem(item: WishlistItem): Long {
        val id = if (item.id == 0L) nextId++ else item.id
        val newItem = item.copy(id = id)
        _items.value = listOf(newItem) + _items.value
        return id
    }

    override suspend fun updateWishlistItem(item: WishlistItem) {
        _items.value = _items.value.map { if (it.id == item.id) item else it }
    }

    override suspend fun deleteWishlistItem(item: WishlistItem) {
        _items.value = _items.value.filter { it.id != item.id }
    }

    override suspend fun moveToCloset(
        context: Context,
        item: WishlistItem,
        clothingRepository: ClothingRepository,
        category: ClothingCategory,
        color: String,
        deleteFromWishlist: Boolean
    ): ClothingItem {
        val clothingItem = ClothingItem(
            imagePath = item.imageUrl,
            category = category,
            color = color,
            styleTags = item.styleTags,
            description = item.title
        )
        val id = clothingRepository.insertClothingItem(clothingItem)
        if (deleteFromWishlist) {
            deleteWishlistItem(item)
        }
        return clothingItem.copy(id = id)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WishlistViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeWishlistRepository = FakeWishlistRepository()
    private val fakeClothingRepository = FakeClothingRepository()
    private val fakePreferencesRepository = FakeUserPreferencesRepository()
    private lateinit var viewModel: WishlistViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = WishlistViewModel(
            wishlistRepository = fakeWishlistRepository,
            clothingRepository = fakeClothingRepository,
            userPreferencesRepository = fakePreferencesRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun saveWishlistItem_addsItemToRepository() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.saveWishlistItem(
            title = "Sudadera Gótica",
            productUrl = "https://shein.com/item123",
            imageUrl = "https://img.shein.com/123.jpg",
            description = "Sudadera oversize oscura",
            styleTags = listOf("Gótico", "Streetwear"),
            category = "Sudaderas",
            color = "Negro",
            price = "19.99 €",
            storeName = "SHEIN"
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.items.size)
        assertEquals("Sudadera Gótica", state.items[0].title)
        assertEquals("SHEIN", state.items[0].storeName)
        assertTrue(state.items[0].styleTags.contains("Gótico"))
    }

    @Test
    fun filterByStyle_filtersItemsCorrectly() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }

        val item1 = WishlistItem(id = 1, title = "Camiseta Dark", productUrl = "", styleTags = listOf("Gótico"), storeName = "SHEIN")
        val item2 = WishlistItem(id = 2, title = "Leggings Gym", productUrl = "", styleTags = listOf("Deporte"), storeName = "Amazon")
        fakeWishlistRepository.setItems(listOf(item1, item2))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStyleTagSelected("Gótico")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.items.size)
        assertEquals("Camiseta Dark", state.items[0].title)
    }

    @Test
    fun filterByStore_filtersItemsCorrectly() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }

        val item1 = WishlistItem(id = 1, title = "Camiseta Dark", productUrl = "", styleTags = listOf("Gótico"), storeName = "SHEIN")
        val item2 = WishlistItem(id = 2, title = "Zapatillas Running", productUrl = "", styleTags = listOf("Deporte"), storeName = "Amazon")
        fakeWishlistRepository.setItems(listOf(item1, item2))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStoreSelected("Amazon")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.items.size)
        assertEquals("Zapatillas Running", state.items[0].title)
    }

    @Test
    fun deleteWishlistItem_removesItem() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }

        val item = WishlistItem(id = 10, title = "Vestido", productUrl = "", storeName = "Zara")
        fakeWishlistRepository.setItems(listOf(item))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteWishlistItem(item)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.items.size)
    }

    @Test
    fun productMetadataExtractor_detectsStoreCorrectly() {
        val extractor = ProductMetadataExtractor()

        assertEquals("SHEIN", extractor.detectStoreFromUrl("https://es.shein.com/item-12345.html"))
        assertEquals("Amazon", extractor.detectStoreFromUrl("https://www.amazon.es/dp/B08N5WRWNW"))
        assertEquals("Zara", extractor.detectStoreFromUrl("https://www.zara.com/es/es/vestido-p123.html"))
        assertEquals("Bershka", extractor.detectStoreFromUrl("https://www.bershka.com/es/camisa-c0p123.html"))
        assertEquals("AliExpress", extractor.detectStoreFromUrl("https://es.aliexpress.com/item/100500123.html"))
    }
}
