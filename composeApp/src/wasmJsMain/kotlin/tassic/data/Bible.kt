package tassic.data

/**
 * Scripture reading, without the scripture.
 *
 * Two deliberate limits, and it's worth being plain about both because they
 * shape everything this file can do.
 *
 * **No Bible text is bundled.** Every modern translation — NIV, ESV, NLT, NKJV
 * — is under copyright, and shipping their text inside an app is not something
 * to do casually. Public-domain translations exist, but bundling a megabyte of
 * KJV into a Wasm PWA to duplicate what YouVersion and Bible Gateway already do
 * far better would be a poor trade. So Tassic holds *references* and hands you
 * off to whichever reader you already use.
 *
 * **The memory feature asks you to type the verse yourself.** That is not a
 * workaround for the above — writing a verse out by hand is a better first pass
 * at memorising it than reading one, so the constraint and the right design
 * happen to agree.
 *
 * What's here is the canon's structure, which is plain factual data: sixty-six
 * books and their chapter counts. From that, any chapter-based plan can be
 * generated on the device without a server or a downloaded plan file.
 */

/** One book: display name, short form used in references, and chapter count. */
data class BibleBook(
    val name: String,
    val abbrev: String,
    val chapters: Int,
    /** OT | NT */
    val testament: String,
    /** LAW | HISTORY | WISDOM | PROPHETS | GOSPELS | ACTS | EPISTLES | APOCALYPSE */
    val section: String
)

object Bible {

    val BOOKS: List<BibleBook> = listOf(
        BibleBook("Genesis", "Gen", 50, "OT", "LAW"),
        BibleBook("Exodus", "Exo", 40, "OT", "LAW"),
        BibleBook("Leviticus", "Lev", 27, "OT", "LAW"),
        BibleBook("Numbers", "Num", 36, "OT", "LAW"),
        BibleBook("Deuteronomy", "Deu", 34, "OT", "LAW"),
        BibleBook("Joshua", "Jos", 24, "OT", "HISTORY"),
        BibleBook("Judges", "Jdg", 21, "OT", "HISTORY"),
        BibleBook("Ruth", "Rut", 4, "OT", "HISTORY"),
        BibleBook("1 Samuel", "1Sa", 31, "OT", "HISTORY"),
        BibleBook("2 Samuel", "2Sa", 24, "OT", "HISTORY"),
        BibleBook("1 Kings", "1Ki", 22, "OT", "HISTORY"),
        BibleBook("2 Kings", "2Ki", 25, "OT", "HISTORY"),
        BibleBook("1 Chronicles", "1Ch", 29, "OT", "HISTORY"),
        BibleBook("2 Chronicles", "2Ch", 36, "OT", "HISTORY"),
        BibleBook("Ezra", "Ezr", 10, "OT", "HISTORY"),
        BibleBook("Nehemiah", "Neh", 13, "OT", "HISTORY"),
        BibleBook("Esther", "Est", 10, "OT", "HISTORY"),
        BibleBook("Job", "Job", 42, "OT", "WISDOM"),
        BibleBook("Psalms", "Psa", 150, "OT", "WISDOM"),
        BibleBook("Proverbs", "Pro", 31, "OT", "WISDOM"),
        BibleBook("Ecclesiastes", "Ecc", 12, "OT", "WISDOM"),
        BibleBook("Song of Songs", "Sng", 8, "OT", "WISDOM"),
        BibleBook("Isaiah", "Isa", 66, "OT", "PROPHETS"),
        BibleBook("Jeremiah", "Jer", 52, "OT", "PROPHETS"),
        BibleBook("Lamentations", "Lam", 5, "OT", "PROPHETS"),
        BibleBook("Ezekiel", "Eze", 48, "OT", "PROPHETS"),
        BibleBook("Daniel", "Dan", 12, "OT", "PROPHETS"),
        BibleBook("Hosea", "Hos", 14, "OT", "PROPHETS"),
        BibleBook("Joel", "Joe", 3, "OT", "PROPHETS"),
        BibleBook("Amos", "Amo", 9, "OT", "PROPHETS"),
        BibleBook("Obadiah", "Oba", 1, "OT", "PROPHETS"),
        BibleBook("Jonah", "Jon", 4, "OT", "PROPHETS"),
        BibleBook("Micah", "Mic", 7, "OT", "PROPHETS"),
        BibleBook("Nahum", "Nah", 3, "OT", "PROPHETS"),
        BibleBook("Habakkuk", "Hab", 3, "OT", "PROPHETS"),
        BibleBook("Zephaniah", "Zep", 3, "OT", "PROPHETS"),
        BibleBook("Haggai", "Hag", 2, "OT", "PROPHETS"),
        BibleBook("Zechariah", "Zec", 14, "OT", "PROPHETS"),
        BibleBook("Malachi", "Mal", 4, "OT", "PROPHETS"),
        BibleBook("Matthew", "Mat", 28, "NT", "GOSPELS"),
        BibleBook("Mark", "Mrk", 16, "NT", "GOSPELS"),
        BibleBook("Luke", "Luk", 24, "NT", "GOSPELS"),
        BibleBook("John", "Jhn", 21, "NT", "GOSPELS"),
        BibleBook("Acts", "Act", 28, "NT", "ACTS"),
        BibleBook("Romans", "Rom", 16, "NT", "EPISTLES"),
        BibleBook("1 Corinthians", "1Co", 16, "NT", "EPISTLES"),
        BibleBook("2 Corinthians", "2Co", 13, "NT", "EPISTLES"),
        BibleBook("Galatians", "Gal", 6, "NT", "EPISTLES"),
        BibleBook("Ephesians", "Eph", 6, "NT", "EPISTLES"),
        BibleBook("Philippians", "Php", 4, "NT", "EPISTLES"),
        BibleBook("Colossians", "Col", 4, "NT", "EPISTLES"),
        BibleBook("1 Thessalonians", "1Th", 5, "NT", "EPISTLES"),
        BibleBook("2 Thessalonians", "2Th", 3, "NT", "EPISTLES"),
        BibleBook("1 Timothy", "1Ti", 6, "NT", "EPISTLES"),
        BibleBook("2 Timothy", "2Ti", 4, "NT", "EPISTLES"),
        BibleBook("Titus", "Tit", 3, "NT", "EPISTLES"),
        BibleBook("Philemon", "Phm", 1, "NT", "EPISTLES"),
        BibleBook("Hebrews", "Heb", 13, "NT", "EPISTLES"),
        BibleBook("James", "Jas", 5, "NT", "EPISTLES"),
        BibleBook("1 Peter", "1Pe", 5, "NT", "EPISTLES"),
        BibleBook("2 Peter", "2Pe", 3, "NT", "EPISTLES"),
        BibleBook("1 John", "1Jn", 5, "NT", "EPISTLES"),
        BibleBook("2 John", "2Jn", 1, "NT", "EPISTLES"),
        BibleBook("3 John", "3Jn", 1, "NT", "EPISTLES"),
        BibleBook("Jude", "Jud", 1, "NT", "EPISTLES"),
        BibleBook("Revelation", "Rev", 22, "NT", "APOCALYPSE")
    )

    val TOTAL_CHAPTERS: Int = BOOKS.sumOf { it.chapters }

    fun book(name: String): BibleBook? =
        BOOKS.firstOrNull { it.name.equals(name, ignoreCase = true) || it.abbrev.equals(name, ignoreCase = true) }

    // ------------------------------------------------------------------ plans

    /** A plan template: a name, a description, the books it covers and how long it runs. */
    data class PlanTemplate(
        val key: String,
        val name: String,
        val blurb: String,
        val days: Int,
        /** Book names in reading order. */
        val books: List<String>,
        /** A second track read alongside the first, e.g. a psalm a day. */
        val companion: List<String> = emptyList()
    )

    private val GOSPELS = listOf("Matthew", "Mark", "Luke", "John")
    private val NEW_TESTAMENT = BOOKS.filter { it.testament == "NT" }.map { it.name }
    private val WHOLE_BIBLE = BOOKS.map { it.name }

    val TEMPLATES: List<PlanTemplate> = listOf(
        PlanTemplate(
            key = "GOSPELS_40",
            name = "The Gospels in 40 days",
            blurb = "Matthew through John, about three chapters a day. A good first plan.",
            days = 40,
            books = GOSPELS
        ),
        PlanTemplate(
            key = "NT_90",
            name = "New Testament in 90 days",
            blurb = "Matthew to Revelation at roughly three chapters a day.",
            days = 90,
            books = NEW_TESTAMENT
        ),
        PlanTemplate(
            key = "PSALMS_PROVERBS",
            name = "Psalms & Proverbs in 60 days",
            blurb = "Two or three psalms a day with a proverb alongside.",
            days = 60,
            books = listOf("Psalms"),
            companion = listOf("Proverbs")
        ),
        PlanTemplate(
            key = "WISDOM_30",
            name = "A proverb a day",
            blurb = "Thirty-one chapters, one a day, straight through the month.",
            days = 31,
            books = listOf("Proverbs")
        ),
        PlanTemplate(
            key = "JOHN_21",
            name = "John in three weeks",
            blurb = "One chapter a day. Slow enough to sit with.",
            days = 21,
            books = listOf("John")
        ),
        PlanTemplate(
            key = "BIBLE_365",
            name = "The whole Bible in a year",
            blurb = "Genesis to Revelation, three to four chapters a day.",
            days = 365,
            books = WHOLE_BIBLE
        ),
        PlanTemplate(
            key = "BIBLE_180",
            name = "The whole Bible in six months",
            blurb = "Demanding — six or seven chapters a day. Know that going in.",
            days = 180,
            books = WHOLE_BIBLE
        )
    )

    /**
     * Turns a template into a concrete list of daily readings.
     *
     * Chapters are distributed evenly rather than by verse count or estimated
     * reading time. A verse-weighted plan is marginally more even and needs a
     * table of 31,000 verse counts to produce; the difference between "3
     * chapters" and "3 chapters" is not worth that.
     *
     * Where a day would span two books the reference names both, because
     * "Genesis 49 – Exodus 2" is a readable instruction and "chapters 49–52" is
     * not.
     */
    fun buildPlan(template: PlanTemplate): List<String> {
        val primary = expand(template.books)
        val companion = expand(template.companion)
        val days = template.days.coerceAtLeast(1)

        val mainSlices = distribute(primary, days)
        val companionSlices = if (companion.isEmpty()) emptyList() else distribute(companion, days)

        return (0 until days).map { index ->
            val parts = mutableListOf<String>()
            mainSlices.getOrNull(index)?.takeIf { it.isNotEmpty() }?.let { parts += describe(it) }
            companionSlices.getOrNull(index)?.takeIf { it.isNotEmpty() }?.let { parts += describe(it) }
            parts.joinToString(" · ")
        }
    }

    /** Every chapter in the given books, in order, as (book, chapter) pairs. */
    private fun expand(bookNames: List<String>): List<Pair<BibleBook, Int>> =
        bookNames.mapNotNull { book(it) }.flatMap { b -> (1..b.chapters).map { b to it } }

    /**
     * Splits [chapters] into [days] slices as evenly as possible.
     *
     * The remainder is spread across the earliest days rather than dumped on
     * the last one — a plan that ends with a nine-chapter day is a plan people
     * abandon on day 364.
     */
    private fun distribute(
        chapters: List<Pair<BibleBook, Int>>,
        days: Int
    ): List<List<Pair<BibleBook, Int>>> {
        if (chapters.isEmpty()) return List(days) { emptyList() }
        val base = chapters.size / days
        val extra = chapters.size % days
        val out = mutableListOf<List<Pair<BibleBook, Int>>>()
        var cursor = 0
        repeat(days) { index ->
            val take = base + if (index < extra) 1 else 0
            val end = (cursor + take).coerceAtMost(chapters.size)
            out += if (cursor < end) chapters.subList(cursor, end).toList() else emptyList()
            cursor = end
        }
        return out
    }

    /** "Genesis 1–3", "Genesis 49 – Exodus 2", "John 3". */
    private fun describe(slice: List<Pair<BibleBook, Int>>): String {
        if (slice.isEmpty()) return ""
        val (firstBook, firstChapter) = slice.first()
        val (lastBook, lastChapter) = slice.last()
        return when {
            slice.size == 1 -> "${firstBook.name} $firstChapter"
            firstBook.name == lastBook.name -> "${firstBook.name} $firstChapter\u2013$lastChapter"
            else -> "${firstBook.name} $firstChapter \u2013 ${lastBook.name} $lastChapter"
        }
    }

    /**
     * A link to read the passage somewhere that actually holds the text.
     *
     * Bible Gateway takes a plain reference in the query string and works
     * without an account, which makes it the least presumptuous default. The
     * translation is left to the site's own setting rather than forced here.
     */
    fun readerUrl(reference: String): String {
        val query = reference
            .substringBefore(" · ")
            .replace("\u2013", "-")
            .replace(" ", "+")
        return "https://www.biblegateway.com/passage/?search=$query"
    }
}
