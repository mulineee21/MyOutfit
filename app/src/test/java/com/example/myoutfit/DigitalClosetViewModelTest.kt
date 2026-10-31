package com.example.myoutfit

import com.example.myoutfit.data.local.entity.ClothingCategory
import com.example.myoutfit.data.local.entity.ClothingItem
import com.example.myoutfit.data.repository.ClothingRepository
import com.example.myoutfit.ui.closet.DigitalClosetViewModel
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DigitalClosetViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeRepository = FakeClothingRepository()
    private lateinit var viewModel: DigitalClosetViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = DigitalClosetViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun categoryFilter_filtersClothingItemsCorrectly() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }

        val item1 = ClothingItem(id = 1, imagePath = "path1", category = ClothingCategory.TOPS, color = "Negro", styleTags = listOf("Casual"), description = "Camiseta")
        val item2 = ClothingItem(id = 2, imagePath = "path2", category = ClothingCategory.BOTTOMS, color = "Azul", styleTags = listOf("Gótico"), description = "Jeans")
        fakeRepository.setItems(listOf(item1, item2))

        viewModel.onCategorySelected(ClothingCategory.TOPS)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ClothingCategory.TOPS, state.selectedCategory)
        assertEquals(1, state.items.size)
        assertEquals(item1, state.items.first())
    }

    @Test
    fun searchQuery_filtersByColorAndTags() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }

        val item1 = ClothingItem(id = 1, imagePath = "path1", category = ClothingCategory.TOPS, color = "Negro", styleTags = listOf("Gótico"), description = "Chaqueta")
        val item2 = ClothingItem(id = 2, imagePath = "path2", category = ClothingCategory.BOTTOMS, color = "Rojo", styleTags = listOf("Casual"), description = "Falda")
        fakeRepository.setItems(listOf(item1, item2))

        viewModel.onSearchQueryChanged("Gótico")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Gótico", state.searchQuery)
        assertEquals(1, state.items.size)
        assertEquals(item1, state.items.first())
    }
}

class FakeClothingRepository : ClothingRepository {
    private val _items = MutableStateFlow<List<ClothingItem>>(emptyList())

    fun setItems(items: List<ClothingItem>) {
        _items.value = items
    }

    override fun getAllClothingItems(): Flow<List<ClothingItem>> = _items

    override suspend fun getClothingItemById(id: Long): ClothingItem? = _items.value.find { it.id == id }

    override fun getClothingItemsByCategory(category: ClothingCategory): Flow<List<ClothingItem>> {
        return MutableStateFlow(_items.value.filter { it.category == category })
    }

    override fun searchClothingItems(query: String): Flow<List<ClothingItem>> {
        return MutableStateFlow(_items.value.filter { it.description.contains(query) })
    }

    override suspend fun insertClothingItem(item: ClothingItem): Long {
        val updated = _items.value + item
        _items.value = updated
        return item.id
    }

    override suspend fun updateClothingItem(item: ClothingItem) {}

    override suspend fun deleteClothingItem(item: ClothingItem) {
        _items.value = _items.value.filter { it.id != item.id }
    }

    override suspend fun deleteClothingItemById(id: Long) {
        _items.value = _items.value.filter { it.id != id }
    }
}
