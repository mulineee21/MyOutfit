package com.example.myoutfit.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myoutfit.MyOutfitApplication
import com.example.myoutfit.ui.backup.BackupDialog
import com.example.myoutfit.ui.backup.BackupViewModel
import com.example.myoutfit.ui.backup.BackupViewModelFactory
import com.example.myoutfit.ui.closet.DigitalClosetScreen
import com.example.myoutfit.ui.closet.DigitalClosetViewModel
import com.example.myoutfit.ui.closet.DigitalClosetViewModelFactory
import com.example.myoutfit.ui.inspiration.InspirationLookbookScreen
import com.example.myoutfit.ui.inspiration.InspirationViewModel
import com.example.myoutfit.ui.inspiration.InspirationViewModelFactory
import com.example.myoutfit.ui.logfavorites.LogAndFavoritesScreen
import com.example.myoutfit.ui.logfavorites.LogAndFavoritesViewModel
import com.example.myoutfit.ui.logfavorites.LogAndFavoritesViewModelFactory
import com.example.myoutfit.ui.recommendation.RecommendationScreen
import com.example.myoutfit.ui.recommendation.RecommendationViewModel
import com.example.myoutfit.ui.recommendation.RecommendationViewModelFactory
import com.example.myoutfit.ui.wishlist.WishlistScreen
import com.example.myoutfit.ui.wishlist.WishlistViewModel
import com.example.myoutfit.ui.wishlist.WishlistViewModelFactory

enum class MainTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    CLOSET("Armario", Icons.Filled.Checkroom, Icons.Outlined.Checkroom),
    RECOMMENDATION("Recomendador", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    WISHLIST("Deseos", Icons.Filled.CardGiftcard, Icons.Outlined.CardGiftcard),
    LOOKBOOK("Inspiración", Icons.Filled.Style, Icons.Outlined.Style),
    LOG_FAVORITES("Diario", Icons.AutoMirrored.Filled.EventNote, Icons.AutoMirrored.Outlined.EventNote)
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    sharedWishlistUrl: String? = null
) {
    val context = LocalContext.current
    val app = context.applicationContext as MyOutfitApplication

    val closetViewModel: DigitalClosetViewModel = viewModel(
        factory = remember {
            DigitalClosetViewModelFactory(
                repository = app.clothingRepository,
                userPreferencesRepository = app.userPreferencesRepository
            )
        }
    )

    val recommendationViewModel: RecommendationViewModel = viewModel(
        factory = remember {
            RecommendationViewModelFactory(
                clothingRepository = app.clothingRepository,
                inspirationRepository = app.inspirationRepository,
                userPreferencesRepository = app.userPreferencesRepository,
                outfitRepository = app.outfitRepository,
                loggedOutfitRepository = app.loggedOutfitRepository
            )
        }
    )

    val wishlistViewModel: WishlistViewModel = viewModel(
        factory = remember {
            WishlistViewModelFactory(
                wishlistRepository = app.wishlistRepository,
                clothingRepository = app.clothingRepository,
                userPreferencesRepository = app.userPreferencesRepository
            )
        }
    )

    val inspirationViewModel: InspirationViewModel = viewModel(
        factory = remember { InspirationViewModelFactory(app.inspirationRepository) }
    )

    val logAndFavoritesViewModel: LogAndFavoritesViewModel = viewModel(
        factory = remember {
            LogAndFavoritesViewModelFactory(
                loggedOutfitRepository = app.loggedOutfitRepository,
                outfitRepository = app.outfitRepository,
                clothingRepository = app.clothingRepository
            )
        }
    )

    val backupViewModel: BackupViewModel = viewModel(
        factory = remember {
            BackupViewModelFactory(app.backupManager)
        }
    )

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showBackupDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sharedWishlistUrl) {
        if (!sharedWishlistUrl.isNullOrBlank()) {
            selectedTabIndex = MainTab.WISHLIST.ordinal
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        label = { Text(tab.title) },
                        icon = {
                            Icon(
                                imageVector = if (selectedTabIndex == index) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedTabIndex) {
            0 -> DigitalClosetScreen(
                viewModel = closetViewModel,
                modifier = Modifier.padding(innerPadding),
                onOpenBackup = { showBackupDialog = true }
            )
            1 -> RecommendationScreen(
                viewModel = recommendationViewModel,
                modifier = Modifier.padding(innerPadding)
            )
            2 -> WishlistScreen(
                viewModel = wishlistViewModel,
                modifier = Modifier.padding(innerPadding),
                initialSharedUrl = sharedWishlistUrl,
                onOpenBackup = { showBackupDialog = true }
            )
            3 -> InspirationLookbookScreen(
                viewModel = inspirationViewModel,
                modifier = Modifier.padding(innerPadding)
            )
            4 -> LogAndFavoritesScreen(
                viewModel = logAndFavoritesViewModel,
                modifier = Modifier.padding(innerPadding),
                onOpenBackup = { showBackupDialog = true }
            )
        }
    }

    if (showBackupDialog) {
        BackupDialog(
            viewModel = backupViewModel,
            onDismiss = { showBackupDialog = false }
        )
    }
}
