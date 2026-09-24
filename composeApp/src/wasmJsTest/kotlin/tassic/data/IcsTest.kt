package tassic.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Calendar import tests.
 *
 * A parser fed other people's files is the classic place for silent corruption:
 * a folded line truncates a title, an all-day event draws a 24-hour block
 * across the timeline, a weekly rule lands on the wrong days. None of those
 * throw — they just make the plan quietly wrong, which is worse than a crash
 * because nobody investigates a plan that merely looks odd.
 */
class IcsTest {

    private fun sample(body: String) = "BEGIN:VCALENDAR\nVERSION:2.0\n$body\nEND:VCALENDAR"

    @Test
    fun readsATimedEvent() {
        val result = Ics.parse(
            sample(
                """
                BEGIN:VEVENT
                UID:abc-123
                SUMMARY:Site visit
                LOCATION:Frog Lake
                DTSTART:20260901T090000
                DTEND:20260901T103000
                END:VEVENT
                """.trimIndent()
            )
        )
        assertEquals(1, result.events.size)
        val event = result.events.first()
        assertEquals("Site visit", event.title)
        assertEquals("Frog Lake", event.location)
        assertEquals(9 * 60, event.startMinutes)
        assertEquals(90, event.durationMinutes)
        assertFalse(event.allDay)
        assertEquals(T.daysFromCivil(2026, 9, 1), event.startEpochDay)
    }

    @Test
    fun allDayEventsDoNotBlockTheWholeDay() {
        // DTEND on an all-day event is exclusive, so a naive reader turns a
        // one-day event into a 1440-minute block sitting over the timeline.
        val result = Ics.parse(
            sample(
                """
                BEGIN:VEVENT
                UID:allday
                SUMMARY:Public holiday
                DTSTART;VALUE=DATE:20260916
                DTEND;VALUE=DATE:20260917
                END:VEVENT
                """.trimIndent()
            )
        )
        val event = result.events.first()
        assertTrue(event.allDay)
        assertEquals(0, event.durationMinutes)
        assertEquals(null, event.startMinutes)
    }

    @Test
    fun unfoldsContinuationLines() {
        val result = Ics.parse(
            sample(
                "BEGIN:VEVENT\n" +
                    "UID:folded\n" +
                    "SUMMARY:Quarterly review with the asset\n management working group\n" +
                    "DTSTART:20260902T140000\n" +
                    "END:VEVENT"
            )
        )
        assertEquals(
            "Quarterly review with the asset management working group",
            result.events.first().title
        )
    }

    @Test
    fun readsWeeklyRulesWithByDay() {
        val result = Ics.parse(
            sample(
                """
                BEGIN:VEVENT
                UID:standup
                SUMMARY:Standup
                DTSTART:20260907T083000
                DTEND:20260907T084500
                RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR
                END:VEVENT
                """.trimIndent()
            )
        )
        val event = result.events.first()
        assertEquals("WEEKLY", event.freq)
        assertEquals(listOf(0, 2, 4), event.byDay)

        val monday = T.daysFromCivil(2026, 9, 7)
        assertTrue(Ics.occursOn(event, monday))
        assertTrue(Ics.occursOn(event, monday + 2))
        assertTrue(Ics.occursOn(event, monday + 4))
        assertFalse(Ics.occursOn(event, monday + 1))
        assertFalse(Ics.occursOn(event, monday + 5))
        // And still runs the following week.
        assertTrue(Ics.occursOn(event, monday + 7))
    }

    @Test
    fun honoursIntervalAndUntil() {
        val result = Ics.parse(
            sample(
                """
                BEGIN:VEVENT
                UID:fortnightly
                SUMMARY:Payroll
                DTSTART:20260901T090000
                RRULE:FREQ=WEEKLY;INTERVAL=2;UNTIL=20260930T000000Z
                END:VEVENT
                """.trimIndent()
            )
        )
        val event = result.events.first()
        val start = T.daysFromCivil(2026, 9, 1)
        assertTrue(Ics.occursOn(event, start))
        assertFalse(Ics.occursOn(event, start + 7))
        assertTrue(Ics.occursOn(event, start + 14))
        // Past UNTIL.
        assertFalse(Ics.occursOn(event, start + 42))
    }

    @Test
    fun aNonRecurringEventHappensExactlyOnce() {
        val result = Ics.parse(
            sample(
                """
                BEGIN:VEVENT
                UID:once
                SUMMARY:Dentist
                DTSTART:20260903T110000
                END:VEVENT
                """.trimIndent()
            )
        )
        val event = result.events.first()
        val day = T.daysFromCivil(2026, 9, 3)
        assertTrue(Ics.occursOn(event, day))
        assertFalse(Ics.occursOn(event, day + 1))
        assertFalse(Ics.occursOn(event, day - 1))
    }

    @Test
    fun unescapesTextValues() {
        val result = Ics.parse(
            sample(
                """
                BEGIN:VEVENT
                UID:escaped
                SUMMARY:Review\, then sign
                DTSTART:20260904T100000
                END:VEVENT
                """.trimIndent()
            )
        )
        assertEquals("Review, then sign", result.events.first().title)
    }

    @Test
    fun rejectsNonCalendarText() {
        val result = Ics.parse("this is just a text file")
        assertTrue(result.events.isEmpty())
    }
}
