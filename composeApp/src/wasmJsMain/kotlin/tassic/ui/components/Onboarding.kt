@file:OptIn(ExperimentalEncodingApi::class)

package tassic.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
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
import tassic.platform.MediaStore
import tassic.platform.awaitOrNull
import tassic.ui.theme.LocalTokens

/** One switchable domain, with the copy shown in the picker. */
data class ModuleOption(
    val key: String,
    val title: String,
    val blurb: String,
    val icon: ImageVector,
    /** Core domains can't be switched off — the app would have nothing left. */
    val required: Boolean = false
)

val MODULE_OPTIONS = listOf(
    ModuleOption("TASKS", "Tasks", "To-dos, repeats, reminders", Icons.Filled.Today, required = true),
    ModuleOption("PLANNER", "Planner", "Day timeline and week view", Icons.Filled.EventNote, required = true),
    ModuleOption("HABITS", "Habits", "Daily repeats and streaks", Icons.Filled.Repeat),
    ModuleOption("GOALS", "Goals", "Short, medium and long horizons", Icons.Filled.Flag),
    ModuleOption("JOURNAL", "Journal", "Writing, photos and voice notes", Icons.Filled.AutoStories),
    ModuleOption("PEOPLE", "People", "Birthdays and keeping in touch", Icons.Filled.Group),
    ModuleOption("GROWTH", "Growth", "Areas to work on, and a good deed a month", Icons.Filled.SelfImprovement),
    ModuleOption("FITNESS", "Training", "Bodyweight sets and streaks", Icons.Filled.FitnessCenter),
    ModuleOption("MUSIC", "Music studio", "CAGED, modes, keys, albums", Icons.Filled.MusicNote),
    ModuleOption("FAITH", "Faith", "Rhythms and prayer points", Icons.Filled.Church),
    ModuleOption("RECOVERY", "Recovery", "Days clean and trigger logs", Icons.Filled.SelfImprovement),
    ModuleOption("CAREER", "Roadmap", "Career stages and resources", Icons.Filled.TrendingUp),
    ModuleOption("WISHLIST", "Wishlist", "Things to buy, priced", Icons.Filled.ShoppingBag)
)

/**
 * First run.
 *
 * Everyone who opened Tassic previously received CAGED guitar shapes, Thursday
 * fasting and a recovery counter, whether or not any of it applied to them.
 * That is the fastest way to make an app feel like it was built for somebody
 * else and left on your phone by accident, and it made the whole thing
 * impossible to hand to anyone.
 *
 * Choosing here also decides what gets seeded, so an unchosen module doesn't
 * leave preset rows lying in storage waiting to reappear if it's switched on.
 */
@Composable
fun OnboardingSheet(onDone: (List<String>) -> Unit) {
    val t = LocalTokens.current
    var selected by rememberState(
        MODULE_OPTIONS.filter { it.required || it.key in setOf("HABITS", "GOALS", "JOURNAL", "GROWTH") }
            .map { it.key }
            .toSet()
    )

    TassicSheet(title = "What should Tassic keep track of?", onDismiss = { onDone(selected.toList()) }) {
        Text(
            "Pick the parts of your life you actually want here. Everything can be switched on or off later in Settings, and nothing you don't choose gets set up in the background.",
            style = MaterialTheme.typography.bodyMedium,
            color = t.textSecondary
        )
        Spacer(Modifier.height(14.dp))

        MODULE_OPTIONS.forEach { option ->
            val on = option.key in selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(t.radiusControl.dp))
                    .background(if (on) t.accent.copy(alpha = if (t.dark) 0.16f else 0.10f) else t.cardSunken)
                    .border(
                        1.dp,
                        if (on) t.accent.copy(alpha = 0.5f) else Color.Transparent,
                        RoundedCornerShape(t.radiusControl.dp)
                    )
                    .pressable(enabled = !option.required) {
                        selected = if (on) selected - option.key else selected + option.key
                    }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (on) t.accent.copy(alpha = 0.9f) else t.card),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        option.icon,
                        contentDescription = null,
                        tint = if (on) t.onAccent else t.textSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(option.title, style = MaterialTheme.typography.titleSmall, color = t.textPrimary)
                    Text(option.blurb, style = MaterialTheme.typography.bodySmall, color = t.textSecondary)
                }
                if (option.required) {
                    Pill("Core")
                } else if (on) {
                    Icon(Icons.Filled.Check, contentDescription = "On", tint = t.accentDeep, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SunkenBox {
            Text(
                "Everything stays on this device. There's no account and nothing is uploaded — which also means Settings → Data → Export is the only backup that exists.",
                style = MaterialTheme.typography.bodySmall,
                color = t.textSecondary
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            GhostButton("Everything", { onDone(MODULE_OPTIONS.map { it.key }) })
            PrimaryButton("Start", { onDone(selected.toList()) })
        }
    }
}

// ------------------------------------------------------------------ images

/**
 * Renders a photo held in [MediaStore].
 *
 * Journal rows store only the key; the bytes live in IndexedDB, so the image
 * has to be fetched and decoded asynchronously. Decoding goes through Skia,
 * the same path the app logo already uses, so there is no second image
 * pipeline to maintain.
 */
@Composable
fun StoredImage(
    imageId: String,
    modifier: Modifier = Modifier,
    height: Int = 180
) {
    val t = LocalTokens.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, imageId) {
        val dataUrl = MediaStore.get(imageId).awaitOrNull()?.toString()
        val b64 = dataUrl?.substringAfter("base64,", "").orEmpty()
        value = if (b64.isEmpty()) {
            null
        } else {
            runCatching { Image.makeFromEncoded(Base64.decode(b64)).toComposeImageBitmap() }.getOrNull()
        }
    }

    val bmp = bitmap
    Box(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(t.radiusControl.dp))
            .background(t.cardSunken),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = "Journal photo",
                modifier = Modifier.fillMaxWidth().height(height.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                "Loading photo…",
                style = MaterialTheme.typography.bodySmall,
                color = t.textTertiary
            )
        }
    }
}
