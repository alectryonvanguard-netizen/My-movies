@file:OptIn(ExperimentalMaterial3Api::class)

package com.mymovie.app.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mymovie.app.data.MediaType
import com.mymovie.app.ui.screens.*
import com.mymovie.app.ui.theme.*
import kotlinx.coroutines.launch

enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Filled.Home),
    Movies("movies", "Movies", Icons.Filled.Movie),
    Tv("tv", "TV Series", Icons.Filled.Tv),
    Anime("anime", "Anime", Icons.Filled.Face),
    Discover("discover", "Discover", Icons.Filled.Explore),
    Library("library", "My Library", Icons.Filled.Bookmark),
    Downloads("downloads", "Downloads", Icons.Filled.Download),
    Settings("settings", "Settings", Icons.Filled.Settings)
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val current = Dest.entries.firstOrNull { route.startsWith(it.route) }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    fun go(d: Dest) {
        nav.navigate(d.route) {
            popUpTo("home") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun openItem(type: MediaType, id: Int) = nav.navigate("detail/${type.path}/$id")

    val content: @Composable (Boolean) -> Unit = { wide ->
        Scaffold(
            containerColor = Black,
            contentWindowInsets = WindowInsets.navigationBars,
            topBar = {
                if (current != null && current != Dest.Discover) {
                    TopBar(showMenu = !wide, onMenu = { scope.launch { drawerState.open() } }, onSearch = { go(Dest.Discover) })
                }
            }
        ) { pad ->
            NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
                composable("home") {
                    HomeScreen(
                        onItem = { openItem(it.type, it.id) },
                        onGenre = { nav.navigate("discover?genre=${Uri.encode(it.name)}") },
                        onAnime = { go(Dest.Anime) },
                        onLibrary = { go(Dest.Library) },
                        onDownloads = { go(Dest.Downloads) },
                        onSettings = { go(Dest.Settings) }
                    )
                }
                composable("movies") { CatalogScreen(CatalogKind.MOVIES, { openItem(it.type, it.id) }, { go(Dest.Settings) }) }
                composable("tv") { CatalogScreen(CatalogKind.TV, { openItem(it.type, it.id) }, { go(Dest.Settings) }) }
                composable("anime") { CatalogScreen(CatalogKind.ANIME, { openItem(it.type, it.id) }, { go(Dest.Settings) }) }
                composable(
                    "discover?genre={genre}",
                    arguments = listOf(navArgument("genre") { type = NavType.StringType; nullable = true; defaultValue = null })
                ) { e ->
                    DiscoverScreen(e.arguments?.getString("genre"), { openItem(it.type, it.id) }, { go(Dest.Settings) })
                }
                composable("library") { LibraryScreen { openItem(it.type, it.id) } }
                composable("downloads") { DownloadsScreen() }
                composable("settings") { SettingsScreen() }
                composable(
                    "detail/{type}/{id}",
                    arguments = listOf(
                        navArgument("type") { type = NavType.StringType },
                        navArgument("id") { type = NavType.IntType }
                    )
                ) { e ->
                    DetailScreen(
                        type = MediaType.from(e.arguments?.getString("type")),
                        id = e.arguments?.getInt("id") ?: 0,
                        onBack = { nav.popBackStack() },
                        onItem = { openItem(it.type, it.id) },
                        onOpenSettings = { go(Dest.Settings) }
                    )
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Black)) {
        val wide = maxWidth >= 840.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(230.dp).fillMaxHeight().background(Charcoal).statusBarsPadding().navigationBarsPadding().padding(12.dp)) {
                    Logo()
                    Spacer(Modifier.height(20.dp))
                    Dest.entries.forEach { SidebarItem(it, it == current) { go(it) } }
                    Spacer(Modifier.weight(1f))
                    AdFreeCard()
                }
                Box(Modifier.weight(1f)) { content(true) }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = current != null,
                drawerContent = {
                    ModalDrawerSheet(drawerContainerColor = Charcoal) {
                        Column(Modifier.statusBarsPadding().padding(12.dp)) {
                            Logo()
                            Spacer(Modifier.height(20.dp))
                            Dest.entries.forEach { d ->
                                SidebarItem(d, d == current) { go(d); scope.launch { drawerState.close() } }
                            }
                            Spacer(Modifier.height(24.dp))
                            AdFreeCard()
                        }
                    }
                }
            ) { content(false) }
        }
    }
}

@Composable
private fun Logo() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, top = 8.dp)) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(BrandBrush), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Black)) { append("My ") }
                withStyle(SpanStyle(color = VioletSoft, fontWeight = FontWeight.Black)) { append("Movie") }
            },
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun SidebarItem(d: Dest, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Violet else Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(d.icon, null, tint = if (selected) Color.White else TextDim)
            Spacer(Modifier.width(14.dp))
            Text(d.label, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AdFreeCard() {
    Surface(shape = RoundedCornerShape(16.dp), color = CardColor, border = BorderStroke(1.dp, Outline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.WorkspacePremium, null, tint = Color(0xFFFFC107))
            Text("Ad-free by design", style = MaterialTheme.typography.labelLarge)
            Text("No ads • No tracking", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFC107))
        }
    }
}

@Composable
private fun TopBar(showMenu: Boolean, onMenu: () -> Unit, onSearch: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showMenu) IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, "Menu") }
        Surface(
            onClick = onSearch, shape = RoundedCornerShape(28.dp), color = CardColor,
            border = BorderStroke(1.dp, Outline), modifier = Modifier.weight(1f).height(48.dp)
        ) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, null, tint = TextDim)
                Spacer(Modifier.width(12.dp))
                Text("Search movies, TV series, anime…", color = TextDim, maxLines = 1)
            }
        }
    }
}
