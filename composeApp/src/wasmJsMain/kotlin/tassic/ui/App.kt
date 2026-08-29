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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
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
import tassic.data.Coach
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
import tassic.ui.components.CommandPalette
import tassic.ui.components.FocusSheet
import tassic.ui.components.LocalSnackbar
import tassic.ui.components.OnboardingSheet
import tassic.ui.components.PaletteAction
import tassic.ui.components.ProgressRing
import tassic.ui.components.QuickCaptureSheet
import tassic.ui.components.ReviewSheet
import tassic.ui.components.WeeklyPlanPrompt
import tassic.ui.components.WeeklyPlanSheet
import tassic.ui.components.pressable
import tassic.ui.components.softBlob
import tassic.ui.tabs.FaithTab
import tassic.ui.tabs.InsightsTab
import tassic.ui.tabs.JournalTab
import tassic.ui.tabs.LifeTab
import tassic.ui.tabs.MusicTab
import tassic.ui.tabs.GrowthTab
import tassic.ui.tabs.PeopleTab
import tassic.ui.tabs.PlanTab
import tassic.ui.tabs.SettingsTab
import tassic.ui.tabs.TodayTab
import tassic.ui.theme.Coral
import tassic.ui.theme.LocalTokens
import tassic.ui.theme.NavySoft
import tassic.ui.theme.TassicTheme

/**
 * Sections.
 *
 * [primary] marks the four that sit in the bottom bar. Everything else lives in
 * the drawer, which is the honest split: a bottom bar is for the handful of
 * places you move between constantly, and a drawer is the full map plus the
 * things you visit deliberately.
 */
enum class Tab(
    val label: String,
    val short: String,
    val icon: ImageVector,
    val primary: Boolean,
    val group: String,
    /** Module key that must be enabled for this section to appear; null = always. */
    val module: String? = null
) {
    TODAY("Today", "Today", Icons.Filled.Today, true, "Daily"),
    PLAN("Plan", "Plan", Icons.Filled.EventNote, true, "Daily"),
    JOURNAL("Journal", "Journal", Icons.Filled.AutoStories, true, "Daily", "JOURNAL"),
    INSIGHTS("Insights", "Insights", Icons.Filled.Insights, true, "Daily"),
    LIFE("Life & Goals", "Life", Icons.Filled.Flag, false, "Long game", "GOALS"),
    PEOPLE("People", "People", Icons.Filled.Group, false, "Long game", "PEOPLE"),
    GROWTH("Growth", "Growth", Icons.Filled.SelfImprovement, false, "Long game", "GROWTH"),
    MUSIC("Music Studio", "Music", Icons.Filled.MusicNote, false, "Long game", "MUSIC"),
    FAITH("Faith", "Faith", Icons.Filled.Church, false, "Long game", "FAITH"),
    SETTINGS("Settings", "Settings", Icons.Filled.Settings, false, "System")
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

        // Global overlays. They live at the shell level rather than inside a
        // tab so capture, search, focus and the review are reachable from
        // anywhere — which is the whole point of them.
        var captureOpen by remember { mutableStateOf(false) }
        var searchOpen by remember { mutableStateOf(false) }
        var focusOpen by remember { mutableStateOf(false) }
        var reviewOpen by remember { mutableStateOf(false) }
        var reviewDismissed by remember { mutableStateOf(false) }
        var weekPlanOpen by remember { mutableStateOf(false) }
        var weekPlanDismissed by remember { mutableStateOf(false) }
        var onboardingOpen by remember { mutableStateOf(false) }

        val todos by store.todos.items.collectAsState()
        val activity by store.activity.items.collectAsState()
        val today = T.today()
        val report = remember(todos, activity, today) { Insights.report(store, today) }

        // First-launch editable seed data + splash removal after first frame.
        LaunchedEffect(Unit) {
            // A returning install seeds as before. A brand new one waits for the
            // module picker, so nobody gets handed CAGED shapes and a fasting
            // rhythm they never asked for.
            if (store.metaGet("seeded") != null || settings.onboarded) {
                store.seedIfEmpty(settings.modules)
            } else {
                onboardingOpen = true
            }
            hideSplash()
            Notifications.registerBackgroundDelivery()
            // App shortcuts can land straight in a capture or a focus block, so
            // the fastest thing in the app is one tap from the home screen.
            when (queryParam("action")?.lowercase()) {
                "capture" -> captureOpen = true
                "focus" -> focusOpen = true
                "search" -> searchOpen = true
                "review" -> reviewOpen = true
                "week" -> weekPlanOpen = true
            }
        }

        // The single reminder loop for the whole app.
        LaunchedEffect(Unit) {
            while (true) {
                Reminders.tick(store)
                delay(30_000)
            }
        }

        // Feeds the widget payload and the pinned summary.
        LaunchedEffect(Unit) {
            while (true) {
                Widgets.push(store.widgetDataJson())
                delay(120_000)
            }
        }

        // Switching a module off — or a ?tab= deep link naming one that's
        // already off — used to leave the app rendering a section that had
        // vanished from every navigation surface, with no way out of it.
        LaunchedEffect(settings.modules, tab) {
            val module = tab.module
            if (module != null && settings.modules.isNotEmpty() && !settings.modules.contains(module)) {
                tab = Tab.TODAY
            }
        }

        val reviewDue = Coach.reviewDue(store, today) && !reviewDismissed
        val weekPlanDue = Coach.weeklyPlanDue(store, today) && !weekPlanDismissed

        CompositionLocalProvider(LocalSnackbar provides snackbar) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    TassicDrawerContent(
                        current = tab,
                        modules = settings.modules,
                        momentum = report.momentum,
                        streak = report.activeStreak,
                        dueToday = report.dueToday,
                        onSelect = {
                            tab = it
                            scope.launch { drawerState.close() }
                        },
                        onCapture = {
                            captureOpen = true
                            scope.launch { drawerState.close() }
                        },
                        onSearch = {
                            searchOpen = true
                            scope.launch { drawerState.close() }
                        },
                        onFocus = {
                            focusOpen = true
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
                                momentum = report.momentum,
                                onMenuClick = { scope.launch { drawerState.open() } },
                                onSearch = { searchOpen = true },
                                onMomentum = { tab = Tab.INSIGHTS }
                            )
                        },
                        bottomBar = {
                            Column {
                                // Only one prompt at a time. Two stacked bars
                                // above the navigation is a wall, and the
                                // weekly one is the rarer and more valuable of
                                // the pair, so it wins the slot.
                                if (weekPlanDue) {
                                    WeeklyPlanPrompt(
                                        onOpen = { weekPlanOpen = true },
                                        onDismiss = { weekPlanDismissed = true }
                                    )
                                } else if (reviewDue) {
                                    ReviewPrompt(
                                        onOpen = { reviewOpen = true },
                                        onDismiss = { reviewDismissed = true }
                                    )
                                }
                                TassicBottomNav(
                                    current = tab,
                                    modules = settings.modules,
                                    onSelect = { tab = it },
                                    onCapture = { captureOpen = true }
                                )
                            }
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
                                    Tab.PLAN -> PlanTab(onOpenTab = { tab = it })
                                    Tab.MUSIC -> MusicTab()
                                    Tab.INSIGHTS -> InsightsTab(onOpenTab = { tab = it })
                                    Tab.LIFE -> LifeTab()
                                    Tab.PEOPLE -> PeopleTab()
                                    Tab.GROWTH -> GrowthTab()
                                    Tab.FAITH -> FaithTab()
                                    Tab.JOURNAL -> JournalTab()
                                    Tab.SETTINGS -> SettingsTab()
                                }
                            }
                        }
                    }

                    // Search sits above the scaffold rather than inside a tab:
                    // it searches everything, so it belongs to the app, not to
                    // whichever screen happened to be open.
                    if (searchOpen) {
                        CommandPalette(
                            onDismiss = { searchOpen = false },
                            onNavigate = { name ->
                                Tab.entries
                                    .firstOrNull { it.name == name }
                                    ?.takeIf { entry ->
                                        entry.module == null ||
                                            settings.modules.isEmpty() ||
                                            settings.modules.contains(entry.module)
                                    }
                                    ?.let { tab = it }
                                searchOpen = false
                            },
                            onAction = { action ->
                                searchOpen = false
                                when (action) {
                                    PaletteAction.CAPTURE -> captureOpen = true
                                    PaletteAction.FOCUS -> focusOpen = true
                                    PaletteAction.REVIEW, PaletteAction.CHECK_IN -> reviewOpen = true
                                    PaletteAction.WEEK -> weekPlanOpen = true
                                    PaletteAction.BACKUP -> tab = Tab.SETTINGS
                                }
                            }
                        )
                    }
                }
            }
        }

        if (onboardingOpen) {
            OnboardingSheet { chosen ->
                store.updateSettings { it.copy(modules = chosen, onboarded = true) }
                store.seedIfEmpty(chosen)
                onboardingOpen = false
            }
        }
        if (captureOpen) QuickCaptureSheet(onDismiss = { captureOpen = false })
        if (focusOpen) FocusSheet(onDismiss = { focusOpen = false })
        if (reviewOpen) {
            ReviewSheet(onDismiss = {
                reviewOpen = false
                reviewDismissed = true
            })
        }
        if (weekPlanOpen) {
            WeeklyPlanSheet(onDismiss = {
                weekPlanOpen = false
                weekPlanDismissed = true
            })
        }
    }
}

// ---------------------------------------------------------------- header

/**
 * Contextual header.
 *
 * Time-of-day greeting, the current section, one tap to search everything and a
 * live momentum ring that opens Insights. The settings cog that used to sit
 * here has moved to the drawer — it was permanent chrome for something visited
 * once a month, while search, which is needed constantly, had nowhere to live.
 */
@Composable
private fun TassicHeader(
    tab: Tab,
    momentum: Int,
    onMenuClick: () -> Unit,
    onSearch: () -> Unit,
    onMomentum: () -> Unit
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
                .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Filled.Menu, contentDescription = "Open menu", tint = t.textPrimary)
            }
            TassicLogo(Modifier.size(32.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "$greeting · ${T.dayName(today)} ${T.shortDate(today)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = t.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    tab.label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = t.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = "Search everything", tint = t.textPrimary)
            }
            Box(
                Modifier
                    .clip(CircleShape)
                    .pressable(onClick = onMomentum)
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                ProgressRing(
                    progress = momentum / 100f,
                    diameter = 38,
                    thickness = 4,
                    color = t.accent,
                    trackColor = t.hairline
                ) {
                    Text(
                        "$momentum",
                        style = MaterialTheme.typography.labelMedium,
                        color = t.textPrimary
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------ review prompt

/**
 * The evening nudge, in the app rather than only in a notification.
 *
 * Notifications get swiped away and permission is often never granted at all,
 * so the prompt that actually closes the loop has to exist on screen too. It
 * appears after the review hour and disappears for the day once the review is
 * done or dismissed.
 */
@Composable
private fun ReviewPrompt(onOpen: () -> Unit, onDismiss: () -> Unit) {
    val t = LocalTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(t.card)
            .pressable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(t.accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.NightsStay, contentDescription = null, tint = t.accentDeep, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Close the day", style = MaterialTheme.typography.titleSmall, color = t.textPrimary)
            Text(
                "Two minutes: what landed, what slipped, how it felt.",
                style = MaterialTheme.typography.bodySmall,
                color = t.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = t.accentDeep,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "Not now",
            style = MaterialTheme.typography.labelSmall,
            color = t.textTertiary,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .pressable(onClick = onDismiss)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

// ------------------------------------------------------------ bottom nav

/**
 * Floating bottom navigation with a raised capture button.
 *
 * Four destinations and one action. The action sits in the middle because
 * capture is the most-used thing in the app and the middle of the bar is the
 * easiest place on a phone to reach — and because putting it here means it is
 * available from every screen without each screen having to offer it.
 */
@Composable
private fun TassicBottomNav(
    current: Tab,
    modules: List<String>,
    onSelect: (Tab) -> Unit,
    onCapture: () -> Unit
) {
    val t = LocalTokens.current
    // Journal is the only bar item that can be switched off; dropping it leaves
    // three, which the SpaceEvenly row handles without looking broken.
    val tabs = Tab.entries.filter { entry ->
        entry.primary && (entry.module == null || modules.isEmpty() || modules.contains(entry.module))
    }
    val split = (tabs.size + 1) / 2
    val left = tabs.take(split)
    val right = tabs.drop(split)

    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp, top = 6.dp)
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
            left.forEach { entry -> NavItem(entry, entry == current) { onSelect(entry) } }

            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(t.accent, t.accentDeep)))
                    .pressable(onClick = onCapture),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Capture",
                    tint = t.onAccent,
                    modifier = Modifier.size(24.dp)
                )
            }

            right.forEach { entry -> NavItem(entry, entry == current) { onSelect(entry) } }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    entry: Tab,
    selected: Boolean,
    onClick: () -> Unit
) {
    val t = LocalTokens.current
    val lift by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "navLift"
    )
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(18.dp))
            .pressable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(width = 40.dp, height = 28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(t.accent.copy(alpha = 0.92f * lift)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                entry.icon,
                contentDescription = entry.short,
                tint = if (selected) t.onAccent else Color.White.copy(alpha = 0.62f),
                modifier = Modifier.size(18.dp)
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
private fun TassicDrawerContent(
    current: Tab,
    modules: List<String>,
    momentum: Int,
    streak: Int,
    dueToday: Int,
    onSelect: (Tab) -> Unit,
    onCapture: () -> Unit,
    onSearch: () -> Unit,
    onFocus: () -> Unit
) {
    val t = LocalTokens.current

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
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
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

                // A live readout in the drawer, so opening navigation also
                // answers "how am I doing" without a trip to Insights.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DrawerStat("$momentum%", "momentum", t.accent)
                    DrawerStat("$streak", "day streak", Color(0xFF7BC86C))
                    DrawerStat("$dueToday", "due today", Color(0xFF8FC7EC))
                }

                Spacer(Modifier.height(12.dp))

                // Quick actions. These are verbs, not places — keeping them
                // visually distinct from the destination list below is what
                // stops the drawer reading as one undifferentiated menu.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DrawerAction(Icons.Filled.Add, "Capture", Modifier.weight(1f), onCapture)
                    DrawerAction(Icons.Filled.Search, "Search", Modifier.weight(1f), onSearch)
                    DrawerAction(Icons.Filled.Timer, "Focus", Modifier.weight(1f), onFocus)
                }

                Spacer(Modifier.height(12.dp))
                DrawerRule()
                Spacer(Modifier.height(6.dp))

                var lastGroup = ""
                // A section the user switched off doesn't appear at all. Half
                // the value of module toggles is what stops being on screen.
                Tab.entries.filter { entry ->
                    entry.module == null || modules.isEmpty() || modules.contains(entry.module)
                }.forEach { entry ->
                    if (entry.group != lastGroup) {
                        lastGroup = entry.group
                        Text(
                            entry.group.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.36f),
                            modifier = Modifier.padding(start = 26.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }
                    val selected = entry == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
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
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(32.dp)
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
                                modifier = Modifier.size(17.dp)
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

                Spacer(Modifier.height(16.dp))
                DrawerRule()
                Text(
                    "Tassic \u00b7 v3.1",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun DrawerRule() {
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
}

@Composable
private fun DrawerAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .pressable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
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
