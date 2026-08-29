package tassic.data

/**
 * Keeping in touch.
 *
 * Relationships decay quietly. Nobody notices the month they stopped calling
 * their brother, which is exactly the kind of slow drift a tracker is good at
 * catching and a person is bad at catching unaided.
 *
 * The design constraint here is tone. A contact list that nags reads as
 * transactional — nobody wants an app scoring their friendships — so this
 * states facts ("seven weeks since you last spoke") and stops there, rather
 * than issuing instructions or grading anyone.
 */

data class ContactStatus(
    val person: Person,
    /** Days since last contact, or null if it's never been logged. */
    val daysSince: Int?,
    /** Days until the next birthday, or null when no birthday is recorded. */
    val daysToBirthday: Int?,
    val overdue: Boolean,
    /** How far past the preferred cadence, in days. Zero when not overdue. */
    val overdueBy: Int
) {
    val hasCadence: Boolean get() = person.cadenceDays > 0
}

object People {

    /** Birthdays inside this window are worth surfacing on the plan. */
    const val BIRTHDAY_HORIZON = 14

    fun status(person: Person, today: Long = T.today()): ContactStatus {
        val daysSince = if (person.lastContactEpochDay <= 0) {
            null
        } else {
            (today - person.lastContactEpochDay).toInt().coerceAtLeast(0)
        }
        val cadence = person.cadenceDays
        val overdueBy = if (cadence > 0 && daysSince != null && daysSince > cadence) {
            daysSince - cadence
        } else {
            0
        }
        return ContactStatus(
            person = person,
            daysSince = daysSince,
            daysToBirthday = daysToBirthday(person, today),
            // Someone with a cadence and no contact ever logged counts as
            // overdue: "never" is not the same as "recently".
            overdue = cadence > 0 && (daysSince == null || daysSince > cadence),
            overdueBy = overdueBy
        )
    }

    fun all(store: Store, today: Long = T.today()): List<ContactStatus> =
        store.people.items.value
            .filter { !it.archived }
            .map { status(it, today) }
            .sortedWith(
                compareByDescending<ContactStatus> { it.person.pinned }
                    .thenByDescending { it.overdue }
                    .thenByDescending { it.overdueBy }
                    .thenBy { it.person.name.lowercase() }
            )

    /** The next occurrence of a person's birthday, as an epoch day. */
    fun nextBirthday(person: Person, today: Long = T.today()): Long? {
        if (person.birthdayMonth !in 1..12 || person.birthdayDay !in 1..31) return null
        val (year, _, _) = T.civilFromDays(today)
        val thisYear = T.daysFromCivil(year, person.birthdayMonth.toLong(), person.birthdayDay.toLong())
        return if (thisYear >= today) {
            thisYear
        } else {
            T.daysFromCivil(year + 1, person.birthdayMonth.toLong(), person.birthdayDay.toLong())
        }
    }

    fun daysToBirthday(person: Person, today: Long = T.today()): Int? =
        nextBirthday(person, today)?.let { (it - today).toInt() }

    /** People whose birthday falls on [day] — used to place them on the plan. */
    fun birthdaysOn(store: Store, day: Long): List<Person> =
        store.people.items.value.filter { person ->
            !person.archived && nextBirthday(person, day) == day
        }

    fun upcomingBirthdays(store: Store, today: Long = T.today(), horizon: Int = BIRTHDAY_HORIZON): List<ContactStatus> =
        all(store, today)
            .filter { (it.daysToBirthday ?: Int.MAX_VALUE) <= horizon }
            .sortedBy { it.daysToBirthday ?: Int.MAX_VALUE }

    /** People past their preferred cadence, longest overdue first. */
    fun overdue(store: Store, today: Long = T.today()): List<ContactStatus> =
        all(store, today).filter { it.overdue }.sortedByDescending { it.overdueBy }

    /** A plain, non-judgemental description of where a relationship stands. */
    fun caption(status: ContactStatus): String {
        val bits = mutableListOf<String>()
        bits += when (val days = status.daysSince) {
            null -> "No contact logged yet"
            0 -> "Spoke today"
            1 -> "Spoke yesterday"
            in 2..13 -> "$days days ago"
            in 14..60 -> "${days / 7} weeks ago"
            else -> "${days / 30} months ago"
        }
        if (status.hasCadence) bits += "every ${cadenceLabel(status.person.cadenceDays)}"
        status.daysToBirthday?.let { days ->
            if (days <= BIRTHDAY_HORIZON) {
                bits += when (days) {
                    0 -> "birthday today"
                    1 -> "birthday tomorrow"
                    else -> "birthday in $days days"
                }
            }
        }
        return bits.joinToString(" · ")
    }

    fun cadenceLabel(days: Int): String = when {
        days <= 0 -> "no schedule"
        days == 1 -> "day"
        days == 7 -> "week"
        days == 14 -> "2 weeks"
        days in 28..31 -> "month"
        days in 88..92 -> "3 months"
        days in 180..186 -> "6 months"
        days >= 360 -> "year"
        else -> "$days days"
    }

    val CADENCE_CHOICES = listOf(0, 7, 14, 30, 90, 180, 365)
    val RELATIONSHIPS = listOf("Family", "Friend", "Mentor", "Colleague", "Church", "Other")
}
