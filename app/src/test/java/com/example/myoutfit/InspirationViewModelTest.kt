package com.example.myoutfit

import com.example.myoutfit.data.local.entity.InspirationItem
import com.example.myoutfit.data.repository.InspirationRepository
import com.example.myoutfit.ui.inspiration.InspirationViewModel
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
class InspirationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeRepository = FakeInspirationRepository()
    private lateinit var viewModel: InspirationViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = InspirationViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun styleFilter_filtersInspirationItemsCorrectly() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }

        val item1 = InspirationItem(id = 1, imagePath = "path1", title = "Look Rocker", styleTags = listOf("Gótico", "Rocker"))
        val item2 = InspirationItem(id = 2, imagePath = "path2", title = "Look Playa", styleTags = listOf("Casual"))
        fakeRepository.setItems(listOf(item1, item2))

        viewModel.onStyleFilterSelected("Gótico")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Gótico", state.selectedStyleFilter)
        assertEquals(1, state.items.size)
        assertEquals(item1, state.items.first())
    }

    @Test
    fun searchQuery_filtersByTitleAndNotes() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }

        val item1 = InspirationItem(id = 1, imagePath = "path1", title = "Timothée Chalamet", styleTags = listOf("Formal"), notes = "Traje de terciopelo")
        val item2 = InspirationItem(id = 2, imagePath = "path2", title = "Zendaya", styleTags = listOf("Elegante"), notes = "Vestido rosa")
        fakeRepository.setItems(listOf(item1, item2))

        viewModel.onSearchQueryChanged("terciopelo")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("terciopelo", state.searchQuery)
        assertEquals(1, state.items.size)
        assertEquals(item1, state.items.first())
    }
}

class FakeInspirationRepository : InspirationRepository {
    private val _items = MutableStateFlow<List<InspirationItem>>(emptyList())

    fun setItems(items: List<InspirationItem>) {
        _items.value = items
    }

    override fun getAllInspirationItems(): Flow<List<InspirationItem>> = _items

    override suspend fun getInspirationItemById(id: Long): InspirationItem? = _items.value.find { it.id == id }

    override suspend fun insertInspirationItem(item: InspirationItem): Long {
        _items.value = _items.value + item
        return item.id
    }

    override suspend fun updateInspirationItem(item: InspirationItem) {}

    override suspend fun deleteInspirationItem(item: InspirationItem) {
        _items.value = _items.value.filter { it.id != item.id }
    }

    override suspend fun deleteInspirationItemById(id: Long) {
        _items.value = _items.value.filter { it.id != id }
    }
}
