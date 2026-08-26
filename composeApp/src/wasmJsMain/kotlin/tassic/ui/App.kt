package tassic.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import tassic.ui.components.softBlob
import tassic.ui.tabs.FaithTab
import tassic.ui.tabs.JournalTab
import tassic.ui.tabs.LifeTab
import tassic.ui.tabs.MusicTab
import tassic.ui.tabs.TodayTab
import tassic.ui.theme.Amber
import tassic.ui.theme.BlueBright
import tassic.ui.theme.Coral
import tassic.ui.theme.Muted
import tassic.ui.theme.Navy
import tassic.ui.theme.NavySoft
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

                // Soft gradient hairline instead of a flat divider — matches the
                // faded-edge language of the ambient backdrop rather than a hard rule.
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
                Spacer(Modifier.height(12.dp))

                Tab.entries.forEach { tab ->
                    val selected = tab == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .then(
                                if (selected) {
                                    Modifier.background(
                                        Brush.horizontalGradient(
                                            listOf(Amber.copy(alpha = 0.22f), Amber.copy(alpha = 0.05f))
                                        )
                                    )
                                } else Modifier
                            )
                            .clickable { onSelect(tab) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .background(
                                    if (selected) Amber.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                tab.icon,
                                contentDescription = null,
                                tint = if (selected) Navy else Color.White.copy(alpha = 0.72f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            tab.label,
                            style = if (selected) {
                                MaterialTheme.typography.titleSmall
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.72f)
                        )
                        if (selected) {
                            Spacer(Modifier.weight(1f))
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .background(Amber, CircleShape)
                            )
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
                    "Tassic \u00b7 v1.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                )
            }
        }
    }
}

/**
 * Textured navy backdrop for the drawer: a diagonal tonal gradient plus a
 * couple of soft, fixed glow blobs (amber + blue, on-brand) so the drawer no
 * longer reads as a flat single-colour panel — same faded-radial-gradient
 * technique as [AmbientBackground], but static (no animation) since the
 * drawer stays composed off-screen for the slide transition and doesn't
 * need to keep spending frames while closed.
 */
@Composable
private fun DrawerBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier.background(Navy)) {
        val w = size.width
        val h = size.height
        val diag = kotlin.math.hypot(w, h)

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Navy, NavySoft, Color(0xFF08192E)),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )
        softBlob(Offset(w * 0.85f, h * 0.06f), diag * 0.40f, Amber, 0.16f)
        softBlob(Offset(w * 0.05f, h * 0.85f), diag * 0.45f, BlueBright, 0.14f)
        softBlob(Offset(w * 0.15f, h * 0.15f), diag * 0.30f, Coral, 0.08f)
    }
}
