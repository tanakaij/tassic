package tassic.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Capture parser tests.
 *
 * This grammar is the highest-risk pure code in the app: it runs on every
 * capture, it silently changes what gets saved, and its failures are invisible
 * — a dropped date looks exactly like a user who forgot to type one. It also
 * shipped with a genuine bug, where trimming sentence punctuation ate the "!"
 * off "!high" and disabled the entire priority grammar, which is precisely the
 * class of mistake a handful of assertions catches for free.
 *
 * A fixed [TODAY] is used rather than the real clock so the expectations don't
 * drift with the calendar. 20328 is a Thursday.
 */
class NlpTest {

    private val TODAY = 20328L

    private fun parse(text: String) = Nlp.parse(text, today = TODAY, defaultKind = CaptureKind.TASK)

    @Test
    fun parsesTheFullKitchenSink() {
        val c = parse("gym tomorrow 7am every weekday ~45m !high #health")
        assertEquals("Gym", c.title)
        assertEquals(TODAY + 1, c.dueEpochDay)
        assertEquals(7 * 60, c.timeMinutes)
        assertEquals("WEEKDAYS", c.recurrence)
        assertEquals(45, c.estimateMinutes)
        assertEquals(Priority.HIGH, c.priority)
        assertEquals(listOf("health"), c.tags)
    }

    @Test
    fun priorityMarkerSurvivesPunctuationStripping() {
        // The regression that motivated this file.
        assertEquals(Priority.HIGH, parse("write the report !high").priority)
        assertEquals(Priority.URGENT, parse("call back !!!").priority)
        assertEquals(Priority.LOW, parse("tidy desk !low").priority)
        assertEquals("Write the report", parse("write the report !high").title)
    }

    @Test
    fun readsWeekdaysAsTheNextOccurrence() {
        // TODAY is a Thursday, so "friday" is tomorrow and "monday" is +4.
        assertEquals(TODAY + 1, parse("call the bank friday").dueEpochDay)
        assertEquals(TODAY + 4, parse("invoice monday").dueEpochDay)
        // "next monday" resolves the same way here because the nearest Monday
        // is already in the following week; the distinction only bites when
        // the named day is today.
        assertEquals(TODAY + 4, parse("invoice next monday").dueEpochDay)
    }

    @Test
    fun readsRelativeAndAbsoluteDates() {
        assertEquals(TODAY, parse("pay rent today").dueEpochDay)
        assertEquals(TODAY + 1, parse("pack tomorrow").dueEpochDay)
        assertEquals(TODAY + 3, parse("submit report in 3 days").dueEpochDay)
        assertEquals(TODAY + 7, parse("review next week").dueEpochDay)
        assertNull(parse("take out the bins").dueEpochDay)
    }

    @Test
    fun readsClockTimes() {
        assertEquals(19 * 60, parse("dinner at 7pm").timeMinutes)
        assertEquals(14 * 60 + 30, parse("team sync at 14:30").timeMinutes)
        assertEquals(7 * 60 + 30, parse("standup 7:30am").timeMinutes)
        assertEquals(12 * 60, parse("lunch at noon").timeMinutes)
        assertEquals(20 * 60, parse("read tonight").timeMinutes)
    }

    @Test
    fun bareNumbersAreNotTimes() {
        // "read 20 pages" must not become 20:00, and the count stays in the title.
        val c = parse("read 20 pages")
        assertNull(c.timeMinutes)
        assertEquals("Read 20 pages", c.title)
    }

    @Test
    fun readsEstimatesInBothForms() {
        assertEquals(30, parse("practice modes ~30m").estimateMinutes)
        assertEquals(45, parse("review notes for 45 minutes").estimateMinutes)
        assertEquals(120, parse("deep work ~2h").estimateMinutes)
        assertEquals("Review notes", parse("review notes for 45 minutes").title)
    }

    @Test
    fun readsRecurrenceAndGivesItSomethingToRollFrom() {
        val weekly = parse("water plants every week")
        assertEquals("WEEKLY", weekly.recurrence)
        // A repeating task with no date can never roll forward, so the parser
        // anchors it to today rather than leaving it inert.
        assertEquals(TODAY, weekly.dueEpochDay)
        assertEquals("MONTHLY", parse("pay rent monthly").recurrence)
        assertEquals("DAILY", parse("stretch every day").recurrence)
        assertEquals("FORTNIGHTLY", parse("bins every 2 weeks").recurrence)
    }

    @Test
    fun routesByPrefix() {
        assertEquals(CaptureKind.NOTE, parse("note: the E shape works better").kind)
        assertEquals(CaptureKind.GOAL, parse("goal: learn PostGIS").kind)
        assertEquals(CaptureKind.PRAYER, parse("pray: provision").kind)
        assertEquals(CaptureKind.WISH, parse("buy audio interface").kind)
    }

    @Test
    fun readsPricesForWishlistItems() {
        val c = parse("buy audio interface \$149 !high")
        assertEquals(CaptureKind.WISH, c.kind)
        assertEquals(149.0, c.price, 0.001)
        assertEquals("Audio interface", c.title)
    }

    @Test
    fun aTimedTaskGetsAnAlertByDefault() {
        assertEquals(0, parse("dentist tomorrow at 9am").reminderMinutesBefore)
        assertNull(parse("tidy the garage").reminderMinutesBefore)
    }

    @Test
    fun unrecognisedTextIsNeverDropped() {
        // The trust-critical property: anything the parser doesn't understand
        // has to survive into the title rather than vanishing.
        val c = parse("email zanele about the borehole quote")
        assertEquals("Email zanele about the borehole quote", c.title)
        assertTrue(c.chips.isEmpty())
    }

    @Test
    fun emptyInputIsEmpty() {
        assertTrue(Nlp.parse("   ", today = TODAY).isEmpty)
    }

    @Test
    fun smartOffSavesLiterally() {
        val c = Nlp.parse("gym tomorrow 7am !high", today = TODAY, smart = false)
        assertEquals("Gym tomorrow 7am !high", c.title)
        assertNull(c.dueEpochDay)
    }
}
