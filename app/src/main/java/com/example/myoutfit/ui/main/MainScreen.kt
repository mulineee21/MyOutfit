package com.example.myoutfit.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
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
import com.example.myoutfit.ui.profile.ProfileScreen
import com.example.myoutfit.ui.profile.ProfileViewModel
import com.example.myoutfit.ui.profile.ProfileViewModelFactory
import com.example.myoutfit.ui.recommendation.RecommendationScreen
import com.example.myoutfit.ui.recommendation.RecommendationViewModel
import com.example.myoutfit.ui.recommendation.RecommendationViewModelFactory
import com.example.myoutfit.ui.social.SocialFeedScreen
import com.example.myoutfit.ui.social.SocialFeedViewModel
import com.example.myoutfit.ui.social.SocialFeedViewModelFactory
import com.example.myoutfit.ui.wishlist.WishlistScreen
import com.example.myoutfit.ui.wishlist.WishlistViewModel
import com.example.myoutfit.ui.wishlist.WishlistViewModelFactory

enum class MainTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    SOCIAL("24h", Icons.Filled.Groups, Icons.Outlined.Groups),
    CLOSET("Armario", Icons.Filled.Checkroom, Icons.Outlined.Checkroom),
    RECOMMENDATION("IA", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    WISHLIST("Deseos", Icons.Filled.CardGiftcard, Icons.Outlined.CardGiftcard),
    LOOKBOOK("Looks", Icons.Filled.Style, Icons.Outlined.Style),
    PROFILE("Perfil", Icons.Filled.Person, Icons.Outlined.Person)
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    sharedWishlistUrl: String? = null
) {
    val context = LocalContext.current
    val app = context.applicationContext as MyOutfitApplication

    val socialFeedViewModel: SocialFeedViewModel = viewModel(
        factory = remember {
            SocialFeedViewModelFactory(
                socialRepository = app.socialRepository,
                loggedOutfitRepository = app.loggedOutfitRepository
            )
        }
    )

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

    val profileViewModel: ProfileViewModel = viewModel(
        factory = remember {
            ProfileViewModelFactory(
                userPreferencesRepository = app.userPreferencesRepository,
                loggedOutfitRepository = app.loggedOutfitRepository,
                outfitRepository = app.outfitRepository,
                clothingRepository = app.clothingRepository,
                wishlistRepository = app.wishlistRepository,
                socialRepository = app.socialRepository
            )
        }
    )

    var selectedTabIndex by remember { mutableIntStateOf(MainTab.SOCIAL.ordinal) }
    var showDiarySubscreen by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sharedWishlistUrl) {
        if (!sharedWishlistUrl.isNullOrBlank()) {
            showDiarySubscreen = false
            selectedTabIndex = MainTab.WISHLIST.ordinal
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEachIndexed { index, tab ->
                    val isSelected = !showDiarySubscreen && selectedTabIndex == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            showDiarySubscreen = false
                            selectedTabIndex = index
                        },
                        label = { Text(tab.title) },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        if (showDiarySubscreen) {
            LogAndFavoritesScreen(
                viewModel = logAndFavoritesViewModel,
                modifier = Modifier.padding(innerPadding),
                onOpenBackup = { showBackupDialog = true }
            )
        } else {
            when (selectedTabIndex) {
                MainTab.SOCIAL.ordinal -> SocialFeedScreen(
                    viewModel = socialFeedViewModel,
                    modifier = Modifier.padding(innerPadding)
                )
                MainTab.CLOSET.ordinal -> DigitalClosetScreen(
                    viewModel = closetViewModel,
                    modifier = Modifier.padding(innerPadding),
                    onOpenBackup = { showBackupDialog = true },
                    onOpenProfile = { selectedTabIndex = MainTab.PROFILE.ordinal }
                )
                MainTab.RECOMMENDATION.ordinal -> RecommendationScreen(
                    viewModel = recommendationViewModel,
                    modifier = Modifier.padding(innerPadding)
                )
                MainTab.WISHLIST.ordinal -> WishlistScreen(
                    viewModel = wishlistViewModel,
                    modifier = Modifier.padding(innerPadding),
                    initialSharedUrl = sharedWishlistUrl,
                    onOpenBackup = { showBackupDialog = true }
                )
                MainTab.LOOKBOOK.ordinal -> InspirationLookbookScreen(
                    viewModel = inspirationViewModel,
                    modifier = Modifier.padding(innerPadding)
                )
                MainTab.PROFILE.ordinal -> ProfileScreen(
                    viewModel = profileViewModel,
                    modifier = Modifier.padding(innerPadding),
                    onNavigateToDiary = { showDiarySubscreen = true },
                    onNavigateToCloset = { selectedTabIndex = MainTab.CLOSET.ordinal },
                    onNavigateToWishlist = { selectedTabIndex = MainTab.WISHLIST.ordinal },
                    onOpenBackup = { showBackupDialog = true }
                )
            }
        }
    }

    if (showBackupDialog) {
        BackupDialog(
            viewModel = backupViewModel,
            onDismiss = { showBackupDialog = false }
        )
    }
}
