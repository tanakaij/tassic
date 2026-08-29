package tassic.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tassic.data.GoodDeed
import tassic.data.Graph
import tassic.data.Growth
import tassic.data.GrowthArea
import tassic.data.T
import tassic.ui.components.DeedSheet
import tassic.ui.components.EmptyState
import tassic.ui.components.GhostButton
import tassic.ui.components.GlassCard
import tassic.ui.components.GrowthAreaRow
import tassic.ui.components.GrowthAreaSheet
import tassic.ui.components.GrowthReviewSheet
import tassic.ui.components.InkCard
import tassic.ui.components.MetricTile
import tassic.ui.components.Pill
import tassic.ui.components.SectionTitle
import tassic.ui.components.SegmentedControl
import tassic.ui.components.SoftDivider
import tassic.ui.components.SunkenBox
import tassic.ui.components.TabScaffold
import tassic.ui.components.TassicCard
import tassic.ui.components.pressable
import tassic.ui.components.rememberFeedback
import tassic.ui.components.rememberState
import tassic.ui.theme.AmberDeep
import tassic.ui.theme.Blue
import tassic.ui.theme.Green
import tassic.ui.theme.LocalTokens
import tassic.ui.theme.Violet

/**
 * Growth.
 *
 * Everything else in Tassic counts throughput — tasks closed, sessions logged,
 * days clean. A life can score extremely well on all of that and still be going
 * nowhere in particular, because none of it touches the part that actually
 * decides what kind of person you're turning into.
 *
 * So this tab breaks the app's own rules on purpose. No streaks. No
 * percentages. No red. One honest rating a month against a standard you wrote
 * yourself, and one good thing a month logged after the fact — because the
 * moment either becomes a number you're protecting, you're optimising the
 * metric instead of the life.
 */
@Composable
fun GrowthTab() {
    val store = Graph.store
    val areas by store.growth.items.collectAsState()
    val checkins by store.growthCheckins.items.collectAsState()
    val deeds by store.deeds.items.collectAsState()
    val t = LocalTokens.current
    val today = T.today()

    var view by rememberState("Areas")
    var areaSheet by rememberState(false)
    var areaEdit by rememberState<GrowthArea?>(null)
    var reviewOpen by rememberState(false)
    var deedSheet by rememberState(false)
    var deedEdit by rememberState<GoodDeed?>(null)

    val month = remember(today) { Growth.monthIndex(today) }
    val pulses = remember(areas, checkins, today) { Growth.allPulses(store, today) }
    val unrated = pulses.filter { !it.ratedThisMonth }
    val reviewDue = remember(areas, checkins, today) { Growth.monthlyReviewDue(store, today) }

    TabScaffold(
        fabIcon = Icons.Filled.Add,
        fabLabel = if (view == "Good deeds") "Log something good" else "New area",
        onFab = if (view == "Good deeds") {
            ({ deedEdit = null; deedSheet = true })
        } else {
            ({ areaEdit = null; areaSheet = true })
        }
    ) {
        InkCard {
            Text("BECOMING", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    pulses.isEmpty() -> "Nothing named yet."
                    unrated.isEmpty() -> "${Growth.shortMonthLabel(month)} is rated across all ${pulses.size} areas."
                    reviewDue -> "${Growth.daysLeftInMonth(today)} days left in ${Growth.shortMonthLabel(month)} · ${unrated.size} still to rate."
                    else -> "${pulses.size} areas · ${unrated.size} not yet rated this month."
                },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("${pulses.size}", "areas", tint = Violet, modifier = Modifier.weight(1f))
                MetricTile(
                    "${Growth.monthsWithDeeds(store, today)}",
                    "months giving",
                    tint = Green,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    "${Growth.daysLeftInMonth(today)}",
                    "days left",
                    tint = AmberDeep,
                    modifier = Modifier.weight(1f)
                )
            }
            if (pulses.isNotEmpty() && unrated.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                GhostButton("Rate ${Growth.shortMonthLabel(month)}", { reviewOpen = true })
            }
        }

        SegmentedControl(
            options = listOf("Areas", "Good deeds"),
            selected = view,
            badge = { if (it == "Areas" && unrated.isNotEmpty()) "${unrated.size}" else null },
            onSelect = { view = it }
        )

        if (view == "Areas") {
            if (pulses.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.SelfImprovement,
                    title = "Nothing named yet",
                    hint = "Name one thing you'd like to be better at. Not a task — a way of being. Patience, honesty, how present you are with people.",
                    actionText = "Name an area",
                    onAction = { areaEdit = null; areaSheet = true }
                )
            } else {
                SectionTitle(
                    eyebrow = Growth.monthLabel(month),
                    title = "Where you are",
                    subtitle = "Tap an area to change what you're aiming at"
                )
                TassicCard {
                    pulses.forEachIndexed { index, pulse ->
                        GrowthAreaRow(pulse) { areaEdit = pulse.area; areaSheet = true }
                        if (index != pulses.lastIndex) SoftDivider()
                    }
                }

                val insights = remember(areas, checkins, today) { Growth.insights(store, today) }
                if (insights.isNotEmpty()) {
                    SectionTitle(eyebrow = "Over time", title = "What the ratings say")
                    insights.take(4).forEach { insight ->
                        GlassCard {
                            Text(insight.title, style = MaterialTheme.typography.titleSmall, color = t.textPrimary)
                            Spacer(Modifier.height(3.dp))
                            Text(insight.detail, style = MaterialTheme.typography.bodySmall, color = t.textSecondary)
                        }
                    }
                } else if (pulses.any { it.monthsRated > 0 }) {
                    SunkenBox {
                        Text(
                            "Three rated months before this starts saying anything about direction. Two points is a mood, not a trend.",
                            style = MaterialTheme.typography.bodySmall,
                            color = t.textSecondary
                        )
                    }
                }
            }
        } else {
            GoodDeedsView(
                onNew = { deedEdit = null; deedSheet = true },
                onEdit = { deedEdit = it; deedSheet = true }
            )
        }

        if (areaSheet) GrowthAreaSheet(areaEdit) { areaSheet = false }
        if (reviewOpen) GrowthReviewSheet { reviewOpen = false }
        if (deedSheet) DeedSheet(deedEdit) { deedSheet = false }
    }
}

/**
 * One good thing a month.
 *
 * The count shown is *months containing something*, never consecutive months.
 * That distinction is the entire ethic of the feature: a streak would make a
 * kindness into a score to defend, and something done to keep a counter alive
 * is a different act from the one this is trying to encourage.
 */
@Composable
private fun GoodDeedsView(onNew: () -> Unit, onEdit: (GoodDeed) -> Unit) {
    val store = Graph.store
    val deeds by store.deeds.items.collectAsState()
    val t = LocalTokens.current
    val today = T.today()
    val month = Growth.monthIndex(today)

    val thisMonth = remember(deeds, today) { Growth.deedsInMonth(store, month) }
    val history = remember(deeds, today) { Growth.deedHistory(store, today) }
    val all = remember(deeds) { deeds.sortedByDescending { it.epochDay } }

    TassicCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Green.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = Green, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("One good thing a month", style = MaterialTheme.typography.titleSmall, color = t.textPrimary)
                Text(
                    Growth.deedStatus(store, today),
                    style = MaterialTheme.typography.bodySmall,
                    color = t.textSecondary
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("LAST 12 MONTHS", style = MaterialTheme.typography.labelSmall, color = t.textTertiary)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            history.forEachIndexed { index, filled ->
                val monthIndex = month - (history.size - 1 - index)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(width = 18.dp, height = 26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (filled) Green.copy(alpha = 0.85f) else Color.Transparent)
                            .then(
                                if (filled) Modifier else Modifier.border(1.dp, t.hairline, RoundedCornerShape(6.dp))
                            )
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        Growth.shortMonthLabel(monthIndex).take(1),
                        style = MaterialTheme.typography.labelSmall,
                        color = t.textTertiary
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SunkenBox {
            Text(
                "Filled months, not consecutive ones. A missed month leaves a gap and nothing more — there's nothing here to break.",
                style = MaterialTheme.typography.bodySmall,
                color = t.textSecondary
            )
        }
    }

    if (thisMonth.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Spa,
            title = "Nothing logged for ${Growth.shortMonthLabel(month)}",
            hint = "Log it after you've done it, not before. Something that cost you a little and that you weren't asked for.",
            actionText = "Log something good",
            onAction = onNew
        )
    }

    if (all.isNotEmpty()) {
        SectionTitle(eyebrow = "Recorded", title = "What you've done", subtitle = "${all.size} in total")
        TassicCard {
            all.take(24).forEachIndexed { index, deed ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .pressable { onEdit(deed) }
                        .padding(vertical = 9.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            deed.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = t.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            buildString {
                                append(T.dateLabel(deed.epochDay))
                                append(" · ")
                                append(Growth.deedKindLabel(deed.kind))
                                if (deed.recipient.isNotBlank()) append(" · ${deed.recipient}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = t.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Pill(
                        Growth.shortMonthLabel(deed.monthIndex),
                        bg = Blue.copy(alpha = if (t.dark) 0.22f else 0.13f),
                        fg = Blue
                    )
                }
                if (index != all.take(24).lastIndex) SoftDivider()
            }
        }
    }
}
