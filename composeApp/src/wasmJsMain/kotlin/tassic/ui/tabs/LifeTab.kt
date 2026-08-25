package tassic.ui.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tassic.data.CareerItem
import tassic.data.GoalItem
import tassic.data.Graph
import tassic.data.Horizon
import tassic.data.T
import tassic.data.WishItem
import tassic.ui.components.CheckRow
import tassic.ui.components.EmptyState
import tassic.ui.components.GhostButton
import tassic.ui.components.IconActionBtn
import tassic.ui.components.ItemMenu
import tassic.ui.components.JournalComposerSheet
import tassic.ui.components.MilestoneSheet
import tassic.ui.components.Pill
import tassic.ui.components.PrimaryButton
import tassic.ui.components.SelectChips
import tassic.ui.components.rememberState
import tassic.ui.components.SectionHeader
import tassic.ui.components.TabScaffold
import tassic.ui.components.TassicCard
import tassic.ui.components.TassicProgress
import tassic.ui.components.GoalSheet
import tassic.ui.components.WishSheet
import tassic.ui.theme.AmberDeep
import tassic.ui.theme.Blue
import tassic.ui.theme.Coral
import tassic.ui.theme.Green
import tassic.ui.theme.Ink
import tassic.ui.theme.Muted
import tassic.ui.theme.Navy
import tassic.ui.theme.SkySoft
import tassic.ui.theme.horizonColor
import tassic.ui.theme.priorityColor
import tassic.platform.openUrl

@Composable
fun LifeTab() {
    val store = Graph.store
    val goals by store.goals.items.collectAsState()
    val wishlist by store.wishlist.items.collectAsState()
    val career by store.career.items.collectAsState()
    val today = T.today()

    var view by rememberState("Goals")
    var goalOpen by rememberState(false)
    var goalEdit by rememberState<GoalItem?>(null)
    var wishOpen by rememberState(false)
    var wishEdit by rememberState<WishItem?>(null)
    var milestoneOpen by rememberState(false)
    var milestoneEdit by rememberState<CareerItem?>(null)
    var milestoneStage by rememberState("")
    var journalOpen by rememberState(false)

    val paths = store.careerPaths().let { stored ->
        career.map { it.path }.distinct().ifEmpty { stored }
    }
    var activePath by rememberState(paths.firstOrNull() ?: "GeoDev Roadmap")

    val fabLabel = when (view) {
        "Goals" -> "New Goal"
        "Wishlist" -> "Add Item"
        else -> "New Milestone"
    }
    val onFab = {
        when (view) {
            "Goals" -> { goalEdit = null; goalOpen = true }
            "Wishlist" -> { wishEdit = null; wishOpen = true }
            else -> { milestoneEdit = null; milestoneStage = ""; milestoneOpen = true }
        }
    }

    TabScaffold(fabIcon = Icons.Filled.Add, fabLabel = fabLabel, onFab = onFab) {
        SelectChips(listOf("Goals", "Wishlist", "GeoDev"), view) { view = it }

        when (view) {
            "Wishlist" -> {
                SectionHeader("Purchases & Wishlist", "${wishlist.count { !it.purchased }} items to go")
                if (wishlist.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.ShoppingBag,
                        title = "Wishlist is empty",
                        hint = "Gear, electronics, licenses — plan the buys."
                    )
                }
                TassicCard {
                    wishlist.forEach { w ->
                        var menu by rememberState(false)
                        CheckRow(
                            title = w.name,
                            subtitle = buildString {
                                append(w.category)
                                if (w.price > 0) append(" · $${if (w.price % 1.0 == 0.0) w.price.toInt() else w.price}")
                            },
                            checked = w.purchased,
                            onChecked = { store.toggleWishPurchased(w) },
                            tint = Green,
                            trailing = {
                                if (w.url.isNotBlank()) {
                                    IconActionBtn(Icons.Filled.OpenInNew, "Open link") { openUrl(w.url) }
                                }
                                Pill(
                                    w.priority.name.lowercase().replaceFirstChar { it.uppercase() },
                                    bg = priorityColor(w.priority).copy(alpha = 0.15f),
                                    fg = priorityColor(w.priority)
                                )
                                IconActionBtn(Icons.Filled.MoreVert, "Options") { menu = true }
                                ItemMenu(
                                    expanded = menu,
                                    onDismiss = { menu = false },
                                    onEdit = { wishEdit = w; wishOpen = true },
                                    onDelete = { store.deleteWish(w.id) }
                                )
                            }
                        )
                    }
                }
            }

            "GeoDev" -> {
                SectionHeader("Career Roadmaps", "Notion-style milestone databases")
                SelectChips(paths, activePath) { activePath = it }
                val inPath = career.filter { it.path == activePath }
                if (inPath.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.TrendingUp,
                        title = "No milestones yet",
                        hint = "Add your first stage via the FAB."
                    )
                } else {
                    val overall = inPath.count { it.done }.toFloat() / inPath.size
                    TassicCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(activePath, style = MaterialTheme.typography.titleMedium, color = Navy)
                                Text(
                                    "${inPath.count { it.done }} of ${inPath.size} milestones complete",
                                    style = MaterialTheme.typography.bodySmall, color = Muted
                                )
                            }
                            Pill("${(overall * 100).toInt()}%", bg = Green.copy(alpha = 0.15f), fg = Green)
                        }
                        TassicProgress(overall, color = Green, modifier = Modifier.padding(top = 8.dp))
                        GhostButton("Log build progress → Journal", { journalOpen = true })
                    }

                    val stages = inPath
                        .groupBy { it.stage }
                        .entries
                        .sortedBy { it.value.firstOrNull()?.stageOrder ?: 0 }

                    stages.forEach { (stage, items) ->
                        var expanded by rememberState(items.firstOrNull()?.stageOrder == 1)
                        val stageProgress = items.count { it.done }.toFloat() / items.size
                        TassicCard {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expanded = !expanded }
                            ) {
                                Pill("S${items.firstOrNull()?.stageOrder ?: 0}", bg = Navy, fg = Color.White)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stage, style = MaterialTheme.typography.titleSmall, color = Ink)
                                    Text(
                                        "${items.count { it.done }}/${items.size} done",
                                        style = MaterialTheme.typography.bodySmall, color = Muted
                                    )
                                }
                                Icon(
                                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null, tint = Muted
                                )
                            }
                            if (expanded) {
                                TassicProgress(stageProgress, color = Blue, modifier = Modifier.padding(vertical = 8.dp))
                                items.sortedBy { it.sortOrder }.forEach { m ->
                                    var menu by rememberState(false)
                                    CheckRow(
                                        title = m.title,
                                        checked = m.done,
                                        onChecked = { store.toggleCareer(m) },
                                        trailing = {
                                            if (m.url.isNotBlank()) {
                                                IconActionBtn(Icons.Filled.OpenInNew, "Open resource") { openUrl(m.url) }
                                            }
                                            IconActionBtn(Icons.Filled.MoreVert, "Options") { menu = true }
                                            ItemMenu(
                                                expanded = menu,
                                                onDismiss = { menu = false },
                                                onEdit = { milestoneEdit = m; milestoneStage = m.stage; milestoneOpen = true },
                                                onDelete = { store.deleteCareer(m.id) },
                                                onUp = { store.moveCareer(m, up = true) },
                                                onDown = { store.moveCareer(m, up = false) }
                                            )
                                        }
                                    )
                                }
                                GhostButton("+ Milestone", {
                                    milestoneEdit = null
                                    milestoneStage = stage
                                    milestoneOpen = true
                                })
                            }
                        }
                    }
                }
            }

            else -> {
                SectionHeader("Life Goals", "Short · medium · long horizon")
                if (goals.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.Flag,
                        title = "No goals yet",
                        hint = "Dream in horizons — add your first goal."
                    )
                }
                goals.forEach { g ->
                    TassicCard {
                        var menu by rememberState(false)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill(
                                when (g.horizon) {
                                    Horizon.SHORT -> "Short-term"
                                    Horizon.MEDIUM -> "Medium-term"
                                    Horizon.LONG -> "Long-term"
                                },
                                bg = horizonColor(g.horizon).copy(alpha = 0.15f),
                                fg = horizonColor(g.horizon)
                            )
                            Spacer(Modifier.width(8.dp))
                            Pill(g.category, bg = SkySoft)
                            Spacer(Modifier.weight(1f))
                            IconActionBtn(Icons.Filled.Remove, "Progress -5") { store.bumpGoal(g, -5) }
                            Text("${g.progress}%", style = MaterialTheme.typography.labelLarge, color = Navy)
                            IconActionBtn(Icons.Filled.Add, "Progress +5") { store.bumpGoal(g, +5) }
                            IconActionBtn(Icons.Filled.MoreVert, "Options") { menu = true }
                            ItemMenu(
                                expanded = menu,
                                onDismiss = { menu = false },
                                onEdit = { goalEdit = g; goalOpen = true },
                                onDelete = { store.deleteGoal(g.id) }
                            )
                        }
                        Text(
                            g.title,
                            style = MaterialTheme.typography.titleLarge, color = Ink,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        if (g.description.isNotBlank()) {
                            Text(g.description, style = MaterialTheme.typography.bodyMedium, color = Muted)
                        }
                        TassicProgress(g.progress / 100f, color = horizonColor(g.horizon), modifier = Modifier.padding(top = 10.dp))
                        g.targetEpochDay?.let { target ->
                            Text(
                                "Target ${T.shortDate(target)} · ${T.relativeDays(target, today)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (target < today && g.progress < 100) Coral else Muted,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // ---- Sheets ------------------------------------------------------------
        if (goalOpen) GoalSheet(goalEdit) { goalOpen = false }
        if (wishOpen) WishSheet(wishEdit) { wishOpen = false }
        if (milestoneOpen) {
            MilestoneSheet(
                edit = milestoneEdit,
                paths = paths,
                defaultPath = activePath,
                defaultStage = milestoneStage
            ) { milestoneOpen = false }
        }
        if (journalOpen) JournalComposerSheet(edit = null, prefillTag = "portfolio") { journalOpen = false }
    }
}
