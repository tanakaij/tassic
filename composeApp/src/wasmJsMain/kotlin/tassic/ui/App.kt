package tassic.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.jetbrains.skia.Image
import tassic.data.Graph
import tassic.data.T
import tassic.platform.awaitOrNull
import tassic.platform.fetchAsDataUrl
import tassic.platform.hideSplash
import tassic.platform.queryParam
import tassic.ui.components.LocalSnackbar
import tassic.ui.components.AmbientBackground
import tassic.ui.components.Pill
import tassic.ui.tabs.FaithTab
import tassic.ui.tabs.JournalTab
import tassic.ui.tabs.LifeTab
import tassic.ui.tabs.MusicTab
import tassic.ui.tabs.TodayTab
import tassic.ui.theme.Amber
import tassic.ui.theme.Muted
import tassic.ui.theme.Navy
import tassic.ui.theme.TassicTheme

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Filled.Today),
    MUSIC("Music", Icons.Filled.MusicNote),
    LIFE("Life & Goals", Icons.Filled.Flag),
    FAITH("Faith", Icons.Filled.Church),
    JOURNAL("Journal", Icons.Filled.AutoStories)
}

/** Resolves the initial tab from a ?tab= deep link (used by TWA app shortcuts); defaults to Today. */
private fun initialTab(): Tab {
    val requested = queryParam("tab")?.lowercase() ?: return Tab.TODAY
    return Tab.entries.firstOrNull { it.name.lowercase() == requested } ?: Tab.TODAY
}

@Composable
fun App() {
    TassicTheme {
        val store = remember { Graph.store }
        val snackbar = remember { SnackbarHostState() }
        var tab by remember { mutableStateOf(initialTab()) }
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        // First-launch editable seed data + splash removal after first frame.
        LaunchedEffect(Unit) {
            store.seedIfEmpty()
            hideSplash()
        }

        // Polls To-Do reminders every minute regardless of which tab is open
        // (mirrors the Faith-tab routine-reminder loop). Only fires while
        // this app/tab is open — see Reminders.kt for why.
        LaunchedEffect(Unit) {
            while (true) {
                tassic.data.Reminders.checkTodoReminders(store)
                delay(60_000)
            }
        }

        CompositionLocalProvider(LocalSnackbar provides snackbar) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                // Default gesturesEnabled = true → edge-swipe from the left opens it,
                // in addition to the hamburger icon in the header below.
                drawerContent = {
                    TassicDrawerContent(
                        current = tab,
                        onSelect = {
                            tab = it
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            ) {
                Box(Modifier.fillMaxSize()) {
                    AmbientBackground(Modifier.fillMaxSize())
                    Scaffold(
                        containerColor = Color.Transparent,
                        snackbarHost = { SnackbarHost(snackbar) },
                        topBar = {
                            TassicHeader(
                                onMenuClick = { scope.launch { drawerState.open() } }
                            )
                        }
                    ) { innerPadding ->
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = {
                                val forward = targetState.ordinal >= initialState.ordinal
                                (
                                    slideInHorizontally(tween(280)) { full -> if (forward) full / 6 else -full / 6 } +
                                        fadeIn(tween(280))
                                    ) togetherWith (
                                    slideOutHorizontally(tween(240)) { full -> if (forward) -full / 6 else full / 6 } +
                                        fadeOut(tween(200))
                                    )
                            },
                            label = "tabSwitch"
                        ) { current ->
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                when (current) {
                                    Tab.TODAY -> TodayTab()
                                    Tab.MUSIC -> MusicTab()
                                    Tab.LIFE -> LifeTab()
                                    Tab.FAITH -> FaithTab()
                                    Tab.JOURNAL -> JournalTab()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- header

@Composable
private fun TassicHeader(onMenuClick: () -> Unit) {
    Surface(color = Color.Transparent) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Filled.Menu, contentDescription = "Open menu", tint = Navy)
            }
            Spacer(Modifier.width(4.dp))
            TassicLogo(Modifier.size(42.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Tassic", style = MaterialTheme.typography.headlineSmall, color = Navy)
                Text("Unified Life OS", style = MaterialTheme.typography.labelMedium, color = Muted)
            }
            Pill(
                text = "${T.dayName(T.today())} · ${T.shortDate(T.today())}",
                bg = Color.White,
                fg = Navy
            )
        }
    }
}

/** logo.png fetched same-origin, decoded via Skia, cached for the session. */
@OptIn(ExperimentalEncodingApi::class)
@Composable
fun TassicLogo(modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null) {
        val dataUrl = fetchAsDataUrl("logo.png").awaitOrNull() ?: return@produceState
        val b64 = dataUrl.toString().substringAfter("base64,", "")
        if (b64.isNotEmpty()) {
            value = runCatching {
                Image.makeFromEncoded(Base64.decode(b64)).toComposeImageBitmap()
            }.getOrNull()
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = "Tassic logo",
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Box(
            modifier.background(Navy, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("T", color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
    }
}

// ---------------------------------------------------------------- navigation drawer

@Composable
private fun TassicDrawerContent(current: Tab, onSelect: (Tab) -> Unit) {
    ModalDrawerSheet(
        drawerContainerColor = Navy,
        modifier = Modifier.windowInsetsPadding(WindowInsets.displayCutout)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = 12.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TassicLogo(Modifier.size(36.dp))
                Spacer(Modifier.width(10.dp))
                Text("Tassic", style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
            Spacer(Modifier.height(8.dp))
            Tab.entries.forEach { tab ->
                val selected = tab == current
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            tab.icon,
                            contentDescription = null,
                            tint = if (selected) Amber else Color.White.copy(alpha = 0.72f)
                        )
                    },
                    label = {
                        Text(
                            tab.label,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.72f)
                        )
                    },
                    selected = selected,
                    onClick = { onSelect(tab) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = Amber.copy(alpha = 0.16f),
                        unselectedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }
        }
    }
}
