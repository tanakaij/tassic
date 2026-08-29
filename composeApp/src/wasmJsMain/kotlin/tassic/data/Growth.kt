package tassic.data

/**
 * Becoming, as opposed to doing.
 *
 * The rest of the app is very good at counting: tasks closed, sessions logged,
 * days clean, streaks held. All of that is *doing*, and a life measured only in
 * throughput can be extremely busy and going nowhere in particular. This is the
 * other half — patience, honesty, generosity, the way you treat people when
 * you're tired and nobody is watching.
 *
 * Two deliberate design decisions, both of which run against how the rest of
 * the app works:
 *
 * **Monthly, not daily.** Character doesn't move on a daily scale. Asking
 * someone to rate their humility every evening produces noise, and worse, it
 * produces the habit of grading yourself constantly — which for most people is
 * not growth, it's just a nicer-looking form of self-criticism.
 *
 * **No streaks on good deeds.** A streak turns a kindness into a number you're
 * protecting, and the moment you're doing it to keep the count you've lost the
 * thing you were trying to build. One a month, logged after the fact, and the
 * app says how many months have something in them rather than how many in a
 * row.
 */

/** The dimensions a growth area can sit in. */
data class Dimension(val key: String, val label: String, val blurb: String)

/** A month's worth of a growth area, with the direction it's moving. */
data class GrowthPulse(
    val area: GrowthArea,
    val currentRating: Int?,
    val previousRating: Int?,
    /** Ratings for the last twelve months, oldest first. Null where not rated. */
    val history: List<Int?>,
    val monthsRated: Int
) {
    val delta: Int get() = if (currentRating == null || previousRating == null) 0 else currentRating - previousRating
    val ratedThisMonth: Boolean get() = currentRating != null
    /** Mean of everything rated, or null when nothing has been. */
    val average: Double?
        get() {
            val values = history.filterNotNull()
            return if (values.isEmpty()) null else values.sum().toDouble() / values.size
        }
}

object Growth {

    const val HISTORY_MONTHS = 12

    val DIMENSIONS = listOf(
        Dimension("CHARACTER", "Character", "Patience, honesty, temper, follow-through"),
        Dimension("MIND", "Mind", "What you read, learn and pay attention to"),
        Dimension("BODY", "Body", "Sleep, food, movement, how you treat yourself"),
        Dimension("SPIRIT", "Spirit", "Prayer, stillness, conviction, conscience"),
        Dimension("RELATIONSHIPS", "Relationships", "How you show up for the people close to you"),
        Dimension("CRAFT", "Craft", "The work itself — skill, standards, discipline"),
        Dimension("MONEY", "Money", "Generosity, restraint, honesty with yourself"),
        Dimension("SERVICE", "Service", "What you give that you can't be repaid for")
    )

    val DEED_KINDS = listOf(
        "PERSON" to "A person",
        "FAMILY" to "Family",
        "COMMUNITY" to "Community",
        "STRANGER" to "A stranger",
        "CREATION" to "The world around you",
        "ANONYMOUS" to "Anonymously"
    )

    fun dimensionLabel(key: String): String =
        DIMENSIONS.firstOrNull { it.key == key.uppercase() }?.label ?: key.lowercase().replaceFirstChar { it.uppercase() }

    fun deedKindLabel(key: String): String =
        DEED_KINDS.firstOrNull { it.first == key.uppercase() }?.second ?: key

    // ----------------------------------------------------------------- months

    /** `year * 12 + (month - 1)`, so months sort and subtract without calendar maths. */
    fun monthIndex(epochDay: Long = T.today()): Long {
        val (year, month, _) = T.civilFromDays(epochDay)
        return year * 12 + (month - 1)
    }

    fun monthLabel(index: Long): String {
        val year = index / 12
        val month = (index % 12) + 1
        val firstOfMonth = T.daysFromCivil(year, month, 1)
        return "${T.monthName(firstOfMonth)} $year"
    }

    fun shortMonthLabel(index: Long): String {
        val year = index / 12
        val month = (index % 12) + 1
        return T.monthName(T.daysFromCivil(year, month, 1))
    }

    /** Days remaining in the month containing [today], including today. */
    fun daysLeftInMonth(today: Long = T.today()): Int {
        val (year, month, _) = T.civilFromDays(today)
        val firstOfNext = if (month == 12L) {
            T.daysFromCivil(year + 1, 1, 1)
        } else {
            T.daysFromCivil(year, month + 1, 1)
        }
        return (firstOfNext - today).toInt()
    }

    // ----------------------------------------------------------------- areas

    fun pulse(store: Store, area: GrowthArea, today: Long = T.today()): GrowthPulse {
        val current = monthIndex(today)
        val checkins = store.growthCheckins.items.value.filter { it.areaId == area.id }
        val byMonth = checkins.associateBy { it.monthIndex }
        val history = (0 until HISTORY_MONTHS).map { back ->
            byMonth[current - (HISTORY_MONTHS - 1 - back)]?.rating
        }
        return GrowthPulse(
            area = area,
            currentRating = byMonth[current]?.rating,
            previousRating = byMonth[current - 1]?.rating,
            history = history,
            monthsRated = checkins.size
        )
    }

    fun allPulses(store: Store, today: Long = T.today()): List<GrowthPulse> =
        store.activeGrowthAreas().map { pulse(store, it, today) }

    /** Areas with no rating yet for the current month. */
    fun unratedThisMonth(store: Store, today: Long = T.today()): List<GrowthArea> =
        allPulses(store, today).filter { !it.ratedThisMonth }.map { it.area }

    /**
     * Whether to prompt for the monthly review.
     *
     * Only in the last five days of the month, and only while something is
     * unrated. Asking on the 3rd is asking someone to summarise a month that
     * has barely started.
     */
    fun monthlyReviewDue(store: Store, today: Long = T.today()): Boolean {
        if (store.activeGrowthAreas().isEmpty()) return false
        if (daysLeftInMonth(today) > 5) return false
        return unratedThisMonth(store, today).isNotEmpty()
    }

    // ----------------------------------------------------------------- deeds

    fun deedsInMonth(store: Store, month: Long = monthIndex()): List<GoodDeed> =
        store.deeds.items.value.filter { it.monthIndex == month }.sortedByDescending { it.epochDay }

    fun deedDoneThisMonth(store: Store, today: Long = T.today()): Boolean =
        deedsInMonth(store, monthIndex(today)).isNotEmpty()

    /**
     * How many of the last twelve months contain at least one deed.
     *
     * Deliberately a count and not a streak. A streak makes a kindness into a
     * number you're protecting, and something done to keep a counter alive
     * isn't the thing this is trying to build.
     */
    fun monthsWithDeeds(store: Store, today: Long = T.today(), months: Int = HISTORY_MONTHS): Int {
        val current = monthIndex(today)
        val seen = store.deeds.items.value.map { it.monthIndex }.toSet()
        return (0 until months).count { back -> seen.contains(current - back) }
    }

    /** The last twelve months as booleans, oldest first — feeds the year strip. */
    fun deedHistory(store: Store, today: Long = T.today(), months: Int = HISTORY_MONTHS): List<Boolean> {
        val current = monthIndex(today)
        val seen = store.deeds.items.value.map { it.monthIndex }.toSet()
        return (0 until months).map { back -> seen.contains(current - (months - 1 - back)) }
    }

    /**
     * The line shown at the top of the good-deeds card.
     *
     * Careful with tone. This should never congratulate someone for being a
     * good person on the strength of a database row, and it should never scold
     * them for an empty month — both would be the app claiming to know
     * something it can't. It states what's logged and what's left of the month.
     */
    fun deedStatus(store: Store, today: Long = T.today()): String {
        val month = monthIndex(today)
        val thisMonth = deedsInMonth(store, month)
        val daysLeft = daysLeftInMonth(today)
        return when {
            thisMonth.size > 1 -> "${thisMonth.size} logged in ${shortMonthLabel(month)}."
            thisMonth.size == 1 -> "${shortMonthLabel(month)} has one: ${thisMonth.first().title}."
            daysLeft <= 7 -> "Nothing logged this month, $daysLeft day(s) left in it."
            else -> "Nothing logged for ${shortMonthLabel(month)} yet."
        }
    }

    // -------------------------------------------------------------- insights

    /**
     * Observations about growth areas.
     *
     * Held to three months of ratings before saying anything about direction:
     * two data points is a mood, not a trend, and telling someone their
     * patience is declining on the strength of one bad month is both wrong and
     * unkind.
     */
    fun insights(store: Store, today: Long = T.today()): List<Insight> {
        val out = mutableListOf<Insight>()

        allPulses(store, today).forEach { p ->
            val rated = p.history.filterNotNull()
            if (rated.size < 3) return@forEach

            val recent = rated.takeLast(3)
            val earlier = rated.dropLast(3)
            val recentAvg = recent.sum().toDouble() / recent.size
            val earlierAvg = if (earlier.isEmpty()) null else earlier.sum().toDouble() / earlier.size

            when {
                earlierAvg != null && recentAvg - earlierAvg >= 0.8 -> out += Insight(
                    id = "growth-up-${p.area.id}",
                    severity = Severity.POSITIVE,
                    domain = "growth",
                    title = "${p.area.name} is moving",
                    detail = "Your last three months average ${fmt(recentAvg)} against ${fmt(earlierAvg)} before that, across ${rated.size} rated months.",
                    weight = 50
                )
                earlierAvg != null && earlierAvg - recentAvg >= 0.8 -> out += Insight(
                    id = "growth-down-${p.area.id}",
                    severity = Severity.INFO,
                    domain = "growth",
                    title = "${p.area.name} has slipped",
                    detail = "Down to ${fmt(recentAvg)} over three months from ${fmt(earlierAvg)}. Worth re-reading what you wrote you were aiming at.",
                    actionLabel = "Open growth",
                    actionTab = "GROWTH",
                    weight = 60
                )
                rated.size >= 6 && recentAvg <= 2.2 -> out += Insight(
                    id = "growth-stuck-${p.area.id}",
                    severity = Severity.WARNING,
                    domain = "growth",
                    title = "${p.area.name} has sat low for months",
                    detail = "Averaging ${fmt(recentAvg)} over the last three of ${rated.size} rated months. Either the practices you chose aren't the right ones, or this isn't the season for it — both are honest answers.",
                    actionLabel = "Open growth",
                    actionTab = "GROWTH",
                    weight = 70
                )
            }
        }

        val months = monthsWithDeeds(store, today)
        if (months >= 6) {
            out += Insight(
                id = "deeds-consistent",
                severity = Severity.POSITIVE,
                domain = "growth",
                title = "$months of the last 12 months have something in them",
                detail = "Logged after the fact, which is the only way this number means anything.",
                weight = 35
            )
        }

        return out.sortedByDescending { it.weight }
    }

    private fun fmt(value: Double): String {
        val rounded = (value * 10).toInt()
        return "${rounded / 10}.${rounded % 10}"
    }
}
