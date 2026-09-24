package tassic.data

/**
 * Global search.
 *
 * Seven tabs deep, an app that holds tasks, goals, journal entries, prayer
 * points, practice items, roadmap steps and a wishlist has a real retrieval
 * problem: the user knows they wrote something down and has no way to find it
 * except remembering which section it lived in. That's the point at which
 * people stop trusting a system with anything they'll need later.
 *
 * Deliberately a linear scan rather than a maintained index. Every table lives
 * in memory already, the corpus is a few thousand short strings, and an index
 * would be one more thing that can fall out of sync with the data.
 */

data class SearchHit(
    /** "task" | "goal" | "habit" | "journal" | "prayer" | "wish" | "practice" | "career" | "routine" | "album" */
    val kind: String,
    val id: Long,
    val title: String,
    val subtitle: String,
    /** Tab enum name the UI should open. */
    val tab: String,
    val score: Int,
    val done: Boolean = false
)

data class SearchResults(
    val query: String,
    val hits: List<SearchHit>
) {
    val isEmpty: Boolean get() = hits.isEmpty()
    fun grouped(): List<Pair<String, List<SearchHit>>> =
        hits.groupBy { it.kind }
            .entries
            .sortedByDescending { entry -> entry.value.maxOfOrNull { it.score } ?: 0 }
            .map { it.key to it.value }
}

object Search {

    private const val MAX_HITS = 40

    fun run(store: Store, rawQuery: String, limit: Int = MAX_HITS): SearchResults {
        val query = rawQuery.trim().lowercase()
        if (query.length < 2) return SearchResults(rawQuery, emptyList())
        val terms = query.split(" ").filter { it.isNotBlank() }
        val hits = mutableListOf<SearchHit>()

        fun consider(
            kind: String,
            id: Long,
            title: String,
            subtitle: String,
            tab: String,
            done: Boolean = false,
            extra: String = "",
            bonus: Int = 0
        ) {
            val score = score(terms, title, subtitle + " " + extra)
            if (score <= 0) return
            hits += SearchHit(kind, id, title, subtitle, tab, score + bonus, done)
        }

        store.todos.items.value.forEach { t ->
            consider(
                kind = "task",
                id = t.id,
                title = t.title,
                subtitle = buildString {
                    t.dueEpochDay?.let { append("Due ${T.relativeDays(it, T.today())}") }
                    if (t.tags.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        append(t.tags.joinToString(", ") { tag -> "#$tag" })
                    }
                    if (t.done) {
                        if (isNotEmpty()) append(" · ")
                        append("Done")
                    }
                },
                tab = "TODAY",
                done = t.done,
                extra = t.notes + " " + t.tags.joinToString(" ") + " " + t.subtasks.joinToString(" ") { it.title },
                // Open work is nearly always what someone is searching for.
                bonus = if (t.done) 0 else 12
            )
        }

        store.goals.items.value.forEach { g ->
            consider("goal", g.id, g.title, "${g.horizon.name.lowercase()} · ${g.progress}%", "LIFE", extra = g.description + " " + g.category + " " + g.motivation)
        }

        store.habits.items.value.forEach { h ->
            consider("habit", h.id, h.name, Agenda.habitCadenceLabel(h), "PLAN", extra = h.unit)
        }

        store.journal.items.value.forEach { j ->
            consider(
                kind = "journal",
                id = j.id,
                title = j.title.ifBlank { j.body.take(48) },
                subtitle = T.fullLabel(j.createdAt),
                tab = "JOURNAL",
                extra = j.body + " " + j.tags.joinToString(" ")
            )
        }

        store.prayers.items.value.forEach { p ->
            consider("prayer", p.id, p.title, if (p.answered) "Answered" else p.category, "FAITH", done = p.answered, extra = p.details)
        }

        store.routines.items.value.forEach { r ->
            consider("routine", r.id, r.title, "${r.cadence} rhythm", "FAITH")
        }

        store.wishlist.items.value.forEach { w ->
            consider("wish", w.id, w.name, w.category, "LIFE", done = w.purchased, extra = w.url)
        }

        store.practice.items.value.forEach { p ->
            consider("practice", p.id, p.title, "${p.kind.name.lowercase()} · ${p.section}", "MUSIC", extra = p.detail)
        }

        store.albums.items.value.forEach { a ->
            consider("album", a.id, a.album, "${a.artist} · ${a.learnedTracks}/${a.totalTracks}", "MUSIC")
        }

        store.career.items.value.forEach { c ->
            consider("career", c.id, c.title, "${c.path} · ${c.stage}", "LIFE", done = c.done, extra = c.url)
        }

        store.people.items.value.forEach { p ->
            consider(
                kind = "person",
                id = p.id,
                title = p.name,
                subtitle = People.caption(People.status(p)),
                tab = "PEOPLE",
                extra = p.notes + " " + p.relationship
            )
        }

        store.calendarEvents.items.value.forEach { e ->
            consider(
                kind = "event",
                id = e.id,
                title = e.title,
                subtitle = T.dateLabel(e.startEpochDay) + (e.location.let { if (it.isBlank()) "" else " · $it" }),
                tab = "PLAN",
                extra = e.location
            )
        }

        store.weekPlans.items.value.forEach { w ->
            w.priorities.forEachIndexed { index, intention ->
                consider(
                    kind = "intention",
                    id = w.id * 10 + index,
                    title = intention.title,
                    subtitle = "Week priority",
                    tab = "PLAN",
                    done = intention.done
                )
            }
        }

        store.growth.items.value.forEach { a ->
            consider(
                kind = "growth",
                id = a.id,
                title = a.name,
                subtitle = Growth.dimensionLabel(a.dimension),
                tab = "GROWTH",
                extra = a.intention + " " + a.evidence + " " + a.practices.joinToString(" ")
            )
        }

        store.deeds.items.value.forEach { d ->
            consider(
                kind = "deed",
                id = d.id,
                title = d.title,
                subtitle = "${T.dateLabel(d.epochDay)} · ${Growth.deedKindLabel(d.kind)}",
                tab = "GROWTH",
                extra = d.recipient + " " + d.notes
            )
        }

        store.recovery.items.value.forEach { r ->
            consider("recovery", r.id, r.name, "${store.daysClean(r)} days clean", "TODAY")
        }

        return SearchResults(
            query = rawQuery,
            hits = hits.sortedWith(compareByDescending<SearchHit> { it.score }.thenBy { it.title }).take(limit)
        )
    }

    /**
     * Scores a candidate against the query terms.
     *
     * Every term has to appear somewhere, so a two-word query narrows instead of
     * widening. Position matters: a title that starts with the term beats one
     * that merely contains it, which beats a body-text match.
     */
    private fun score(terms: List<String>, title: String, body: String): Int {
        val t = title.lowercase()
        val b = body.lowercase()
        var total = 0
        terms.forEach { term ->
            val inTitle = t.indexOf(term)
            val inBody = b.indexOf(term)
            val termScore = when {
                inTitle == 0 -> 100
                inTitle > 0 && (t.getOrNull(inTitle - 1) == ' ') -> 70
                inTitle > 0 -> 45
                inBody >= 0 -> 20
                else -> return 0 // every term must land somewhere
            }
            total += termScore
        }
        // Shorter titles matching the same terms are more likely to be the thing.
        return total + (40 - t.length.coerceAtMost(40)) / 4
    }

    fun kindLabel(kind: String): String = when (kind) {
        "task" -> "Tasks"
        "goal" -> "Goals"
        "habit" -> "Habits"
        "journal" -> "Journal"
        "prayer" -> "Prayer points"
        "routine" -> "Rhythms"
        "wish" -> "Wishlist"
        "practice" -> "Practice"
        "album" -> "Albums"
        "career" -> "Roadmap"
        "recovery" -> "Recovery"
        "person" -> "People"
        "event" -> "Calendar"
        "intention" -> "Week priorities"
        "growth" -> "Growth areas"
        "deed" -> "Good deeds"
        else -> kind.replaceFirstChar { it.uppercase() }
    }
}
