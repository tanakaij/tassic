package tassic.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image
import tassic.data.Graph
import tassic.data.Insights
import tassic.data.Reminders
import tassic.data.T
import tassic.platform.Notifications
import tassic.platform.Widgets
import tassic.platform.awaitOrNull
import tassic.platform.fetchAsDataUrl
import tassic.platform.hideSplash
import tassic.platform.queryParam
import tassic.ui.components.AmbientBackground
import tassic.ui.components.LocalSnackbar
import tassic.ui.components.Pill
import tassic.ui.components.pressable
import tassic.ui.components.softBlob
import tassic.ui.tabs.FaithTab
import tassic.ui.tabs.InsightsTab
import tassic.ui.tabs.JournalTab
import tassic.ui.tabs.LifeTab
import tassic.ui.tabs.MusicTab
import tassic.ui.tabs.SettingsTab
import tassic.ui.tabs.TodayTab
import tassic.ui.theme.Coral
import tassic.ui.theme.LocalTokens
import tassic.ui.theme.NavySoft
import tassic.ui.theme.TassicTheme

enum class Tab(val label: String, val short: String, val icon: ImageVector, val primary: Boolean) {
    TODAY("Today", "Today", Icons.Filled.Today, true),
    MUSIC("Music Studio", "Music", Icons.Filled.MusicNote, true),
    INSIGHTS("Insights", "Insights", Icons.Filled.Insights, true),
    LIFE("Life & Goals", "Life", Icons.Filled.Flag, true),
    JOURNAL("Journal", "Journal", Icons.Filled.AutoStories, true),
    FAITH("Faith", "Faith", Icons.Filled.Church, false),
    SETTINGS("Settings", "Settings", Icons.Filled.Settings, false)
}

/** Resolves the initial tab from a ?tab= deep link (used by TWA app shortcuts). */
private fun initialTab(): Tab {
    val requested = queryParam("tab")?.lowercase() ?: return Tab.TODAY
    return Tab.entries.firstOrNull { it.name.lowercase() == requested } ?: Tab.TODAY
}

@Composable
fun App() {
    val store = remember { Graph.store }
    val settings by store.settingsState.collectAsState()

    TassicTheme(
        themeMode = settings.themeMode,
        accentName = settings.accent,
        reduceMotion = settings.reduceMotion
    ) {
        val snackbar = remember { SnackbarHostState() }
        var tab by remember { mutableStateOf(initialTab()) }
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        // First-launch editable seed data + splash removal after first frame.
        LaunchedEffect(Unit) {
            store.seedIfEmpty()
            hideSplash()
            // Register the background-delivery paths as early as possible so a
            // user who never opens Settings still gets closed-app reminders
            // wherever the browser supports them.
            Notifications.registerBackgroundDelivery()
        }

        // The single reminder loop for the whole app.
        //
        // This replaces three separate polls (todos here, faith routines inside
        // FaithTab, widgets on their own timer). Routine reminders living inside
        // a tab meant they only ran while that tab was on screen, which is a
        // large part of why "notifications don't work" — you had to be sitting
        // on the Faith tab for a Faith reminder to fire.
        LaunchedEffect(Unit) {
            while (true) {
                Reminders.tick(store)
                delay(30_000)
            }
        }

        // Feeds the widget payload and the pinned summary. Slower cadence
        // because it's a rendering refresh, not a delivery deadline.
        LaunchedEffect(Unit) {
            while (true) {
                Widgets.push(store.widgetDataJson())
                delay(120_000)
            }
        }

        CompositionLocalProvider(LocalSnackbar provides snackbar) {
            ModalNavigationDrawer(
                drawerState = drawerState,
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
                    if (settings.ambientBackground) {
                        AmbientBackground(Modifier.fillMaxSize())
                    } else {
                        Box(Modifier.fillMaxSize().background(LocalTokens.current.canvasMid))
                    }
                    Scaffold(
                        containerColor = Color.Transparent,
                        snackbarHost = { SnackbarHost(snackbar) },
                        topBar = {
                            TassicHeader(
                                tab = tab,
                                onMenuClick = { scope.launch { drawerState.open() } },
                                onSettings = { tab = Tab.SETTINGS }
                            )
                        },
                        bottomBar = {
                            TassicBottomNav(current = tab, onSelect = { tab = it })
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
                                    Tab.TODAY -> TodayTab(onOpenTab = { tab = it })
                                    Tab.MUSIC -> MusicTab()
                                    Tab.INSIGHTS -> InsightsTab(onOpenTab = { tab = it })
                                    Tab.LIFE -> LifeTab()
                                    Tab.FAITH -> FaithTab()
                                    Tab.JOURNAL -> JournalTab()
                                    Tab.SETTINGS -> SettingsTab()
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

/**
 * Contextual header.
 *
 * Shows a time-of-day greeting and the current section rather than repeating
 * the app name on every screen — the app name is already on the icon the user
 * tapped to get here.
 */
@Composable
private fun TassicHeader(
    tab: Tab,
    onMenuClick: () -> Unit,
    onSettings: () -> Unit
) {
    val t = LocalTokens.current
    val today = T.today()
    val greeting = remember(T.localHour()) { Insights.greeting(T.localHour()) }

    Surface(color = Color.Transparent) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(start = 4.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Filled.Menu, contentDescription = "Open menu", tint = t.textPrimary)
            }
            TassicLogo(Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    greeting,
                    style = MaterialTheme.typography.labelMedium,
                    color = t.textSecondary
                )
                Text(
                    tab.label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = t.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Pill(text = "${T.dayName(today)} · ${T.shortDate(today)}")
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = t.textSecondary)
            }
        }
    }
}

// ------------------------------------------------------------ bottom nav

/**
 * Floating bottom navigation.
 *
 * A drawer alone is the wrong primary navigation for a five-section mobile app:
 * every section change cost a swipe plus a tap, and nothing on screen said where
 * you were. The five most-used tabs now sit one thumb-tap away; the drawer keeps
 * the full list including Faith and Settings.
 */
@Composable
private fun TassicBottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    val t = LocalTokens.current
    val tabs = Tab.entries.filter { it.primary }

    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, bottom = 12.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(t.chrome, t.chrome.copy(alpha = 0.94f))
                    )
                )
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { entry ->
                val selected = entry == current
                val lift by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                    label = "navLift"
                )
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .pressable { onSelect(entry) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .size(width = 40.dp, height = 30.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(t.accent.copy(alpha = 0.92f * lift)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            entry.icon,
                            contentDescription = entry.short,
                            tint = if (selected) t.onAccent else Color.White.copy(alpha = 0.62f),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        entry.short,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** logo.png fetched same-origin, decoded via Skia, cached for the session. */
@OptIn(ExperimentalEncodingApi::class)
@Composable
fun TassicLogo(modifier: Modifier = Modifier) {
    val t = LocalTokens.current
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
            modifier.background(t.chrome, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("T", color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
    }
}

// ---------------------------------------------------------------- drawer

@Composable
private fun TassicDrawerContent(current: Tab, onSelect: (Tab) -> Unit) {
    val t = LocalTokens.current
    val store = Graph.store
    val activity by store.activity.items.collectAsState()
    val todos by store.todos.items.collectAsState()
    val report = remember(activity, todos, current) { Insights.report(store) }

    ModalDrawerSheet(
        drawerContainerColor = Color.Transparent,
        modifier = Modifier.windowInsetsPadding(WindowInsets.displayCutout)
    ) {
        Box(Modifier.fillMaxSize()) {
            DrawerBackdrop(Modifier.fillMaxSize())
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
                    Box(
                        Modifier
                            .size(46.dp)
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                            .padding(5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        TassicLogo(Modifier.size(36.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Tassic", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(
                            "Unified Life OS",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                // A live momentum readout in the drawer, so opening navigation
                // also answers "how am I doing" without a trip to Insights.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DrawerStat("${report.momentum}%", "momentum", t.accent)
                    DrawerStat("${report.activeStreak}", "day streak", Color(0xFF7BC86C))
                    DrawerStat("${report.dueToday}", "due today", Color(0xFF8FC7EC))
                }

                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.18f), Color.Transparent)
                            )
                        )
                )
                Spacer(Modifier.height(10.dp))

                Tab.entries.forEach { entry ->
                    val selected = entry == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .then(
                                if (selected) {
                                    Modifier.background(
                                        Brush.horizontalGradient(
                                            listOf(t.accent.copy(alpha = 0.22f), t.accent.copy(alpha = 0.05f))
                                        )
                                    )
                                } else Modifier
                            )
                            .clickable { onSelect(entry) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .background(
                                    if (selected) t.accent.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                entry.icon,
                                contentDescription = null,
                                tint = if (selected) t.onAccent else Color.White.copy(alpha = 0.72f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            entry.label,
                            style = if (selected) {
                                MaterialTheme.typography.titleSmall
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.72f)
                        )
                        if (selected) {
                            Spacer(Modifier.weight(1f))
                            Box(Modifier.size(6.dp).background(t.accent, CircleShape))
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.14f), Color.Transparent)
                            )
                        )
                )
                Text(
                    "Tassic \u00b7 v2.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun DrawerStat(value: String, label: String, tint: Color) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
    }
}

/**
 * Textured navy backdrop for the drawer: a diagonal tonal gradient plus a
 * couple of soft, fixed glow blobs so the drawer doesn't read as a flat panel.
 * Static rather than animated, since the drawer stays composed off-screen for
 * the slide transition and shouldn't keep spending frames while closed.
 */
@Composable
private fun DrawerBackdrop(modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Canvas(modifier.background(t.chrome)) {
        val w = size.width
        val h = size.height
        val diag = kotlin.math.hypot(w, h)

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(t.chrome, NavySoft, Color(0xFF08192E)),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )
        softBlob(Offset(w * 0.85f, h * 0.06f), diag * 0.40f, t.accent, 0.16f)
        softBlob(Offset(w * 0.05f, h * 0.85f), diag * 0.45f, Color(0xFF2D9CDB), 0.14f)
        softBlob(Offset(w * 0.15f, h * 0.15f), diag * 0.30f, Coral, 0.08f)
    }
}
