package tassic.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Planning, streak and month-arithmetic tests.
 *
 * These cover the judgements the app makes *on the user's behalf* — whether the
 * day fits, whether two things collide, whether a streak is alive, whether a
 * month is over. Each of them is a small piece of arithmetic that nobody would
 * check by hand, and each one is capable of being confidently wrong in a way
 * that looks perfectly reasonable on screen.
 *
 * Every test builds its own [Store] and erases it first: these run against real
 * localStorage in a browser, so leaking rows between tests would make failures
 * depend on execution order.
 */
class PlanningTest {

    private fun freshStore(): Store = Store().also { it.eraseAll() }

    private fun task(
        title: String,
        day: Long,
        timeMinutes: Int? = null,
        estimate: Int? = null
    ) = TodoItem(
        title = title,
        dueEpochDay = day,
        dueTimeMinutes = timeMinutes,
        estimateMinutes = estimate,
        createdAt = T.now()
    )

    // ------------------------------------------------------------- day plan

    @Test
    fun timedTasksLandOnTheTimelineAndUntimedOnesDoNot() {
        val store = freshStore()
        val today = T.today()
        store.addTodo(task("Standup", today, timeMinutes = 9 * 60, estimate = 30))
        store.addTodo(task("Write the brief", today, estimate = 60))

        val plan = Agenda.plan(store, today, nowMinutes = 0)
        assertEquals(1, plan.timed.size)
        assertEquals("Standup", plan.timed.first().title)
        assertEquals(1, plan.anytime.size)
        assertEquals(2, plan.totalCount)
        assertEquals(90, plan.remainingMinutes)
    }

    @Test
    fun overdueWorkFollowsYouToToday() {
        val store = freshStore()
        val today = T.today()
        store.addTodo(task("Chase the invoice", today - 3))

        val plan = Agenda.plan(store, today, nowMinutes = 0)
        // A plan that leaves missed work on the day it was missed is a
        // reprimand, not a plan.
        assertEquals(1, plan.allEntries.size)
        assertTrue(plan.allEntries.first().subtitle.contains("Overdue"))
    }

    @Test
    fun detectsOverlappingCommitments() {
        val store = freshStore()
        val today = T.today()
        store.addTodo(task("Client call", today, timeMinutes = 10 * 60, estimate = 60))
        store.addTodo(task("Dentist", today, timeMinutes = 10 * 60 + 30, estimate = 45))
        store.addTodo(task("Gym", today, timeMinutes = 18 * 60, estimate = 45))

        val plan = Agenda.plan(store, today, nowMinutes = 0)
        assertEquals(1, plan.clashes.size)
        val clash = plan.clashes.first()
        assertEquals("Client call", clash.first.title)
        assertEquals("Dentist", clash.second.title)
    }

    @Test
    fun freeSlotsSkipTheGapsTooSmallToUse() {
        val store = freshStore()
        val today = T.today()
        // 09:00–10:00 and 10:10–11:00 leave a ten-minute gap, which is not a
        // usable working slot and must not be offered as one.
        store.addTodo(task("Block A", today, timeMinutes = 9 * 60, estimate = 60))
        store.addTodo(task("Block B", today, timeMinutes = 10 * 60 + 10, estimate = 50))

        val plan = Agenda.plan(store, today, nowMinutes = 0)
        assertTrue(plan.freeSlots.none { it.startMinutes == 10 * 60 })
        assertTrue(plan.freeSlots.all { it.minutes >= 20 })
    }

    @Test
    fun anImpossibleDayIsReportedAsImpossible() {
        val store = freshStore()
        val today = T.today()
        // Far more work than the 07:00–22:00 window can hold.
        repeat(12) { index ->
            store.addTodo(task("Task $index", today, estimate = 120))
        }
        val plan = Agenda.plan(store, today, nowMinutes = 7 * 60)
        assertFalse(plan.fits)
        assertTrue(plan.remainingMinutes > plan.availableMinutes)
    }

    @Test
    fun placementSuggestionsNeverExceedTheGapsAvailable() {
        val store = freshStore()
        val today = T.today()
        store.addTodo(task("Meeting", today, timeMinutes = 12 * 60, estimate = 60))
        store.addTodo(task("Long job", today, estimate = 90))
        store.addTodo(task("Short job", today, estimate = 25))

        val plan = Agenda.plan(store, today, nowMinutes = 7 * 60)
        val placements = Agenda.suggestPlacements(plan)
        assertEquals(2, placements.size)
        // Longest first, and nothing placed inside the booked meeting.
        assertEquals("Long job", placements.maxByOrNull { it.first.durationMinutes }?.first?.title)
        placements.forEach { (entry, start) ->
            val end = start + entry.durationMinutes
            val meetingStart = 12 * 60
            val meetingEnd = meetingStart + 60
            assertTrue(end <= meetingStart || start >= meetingEnd)
        }
    }

    // --------------------------------------------------------------- habits

    @Test
    fun aDailyHabitStreakCountsBackwards() {
        val store = freshStore()
        val today = T.today()
        val habit = store.addHabit(Habit(name = "Read", cadence = "DAILY", createdAt = T.now()))

        // Log yesterday and the day before by hand, since tickHabit always
        // writes today.
        listOf(1L, 2L).forEach { back ->
            store.insert(
                store.activity,
                ActivityLog(
                    domain = "habit",
                    refId = habit.id,
                    event = "COMPLETE",
                    value = 1,
                    epochDay = today - back,
                    loggedAt = T.now()
                )
            )
        }
        // An unkept today must not break a streak that is still alive until
        // midnight.
        assertEquals(2, store.habitStreak(habit, today))

        store.tickHabit(habit)
        assertEquals(3, store.habitStreak(habit, today))
    }

    @Test
    fun aWeekdayHabitSurvivesTheWeekend() {
        val store = freshStore()
        val habit = store.addHabit(Habit(name = "Stretch", cadence = "WEEKDAYS", createdAt = T.now()))

        // Anchor on a known Monday so the test doesn't depend on the real date.
        val monday = T.daysFromCivil(2026, 9, 7)
        assertEquals(0, T.dowIndex(monday))
        assertTrue(store.habitDueOn(habit, monday))
        assertTrue(store.habitDueOn(habit, monday + 4))
        assertFalse(store.habitDueOn(habit, monday + 5))
        assertFalse(store.habitDueOn(habit, monday + 6))
    }

    @Test
    fun countedHabitsNeedTheWholeTarget() {
        val store = freshStore()
        val today = T.today()
        val habit = store.addHabit(
            Habit(name = "Water", cadence = "DAILY", targetPerDay = 3, unit = "glasses", createdAt = T.now())
        )
        store.tickHabit(habit)
        store.tickHabit(habit)
        assertEquals(2, store.habitCount(habit.id, today))
        assertFalse(store.habitDoneOn(habit, today))

        store.tickHabit(habit)
        assertTrue(store.habitDoneOn(habit, today))

        store.untickHabit(habit)
        assertEquals(2, store.habitCount(habit.id, today))
    }

    // ---------------------------------------------------------- goal linkage

    @Test
    fun goalProgressFollowsItsLinkedTasks() {
        val store = freshStore()
        val goal = store.addGoal(GoalItem(title = "Ship v3", autoProgress = true, createdAt = T.now()))
        val a = store.addTodo(task("Write it", T.today()).copy(goalId = goal.id))
        store.addTodo(task("Test it", T.today()).copy(goalId = goal.id))

        store.toggleTodo(store.todos.items.value.first { it.id == a.id })
        assertEquals(50, store.goals.items.value.first { it.id == goal.id }.progress)
    }

    @Test
    fun aHandSetGoalIsNeverOverwritten() {
        val store = freshStore()
        // autoProgress off: the number the user typed has to survive.
        val goal = store.addGoal(GoalItem(title = "Learn PostGIS", progress = 40, createdAt = T.now()))
        val todo = store.addTodo(task("Read the docs", T.today()).copy(goalId = goal.id))
        store.toggleTodo(store.todos.items.value.first { it.id == todo.id })
        assertEquals(40, store.goals.items.value.first { it.id == goal.id }.progress)
    }

    // ---------------------------------------------------------------- growth

    @Test
    fun monthIndexIsContinuousAcrossAYearBoundary() {
        val december = Growth.monthIndex(T.daysFromCivil(2026, 12, 15))
        val january = Growth.monthIndex(T.daysFromCivil(2027, 1, 3))
        assertEquals(1L, january - december)
    }

    @Test
    fun daysLeftInMonthIncludesToday() {
        assertEquals(1, Growth.daysLeftInMonth(T.daysFromCivil(2026, 9, 30)))
        assertEquals(31, Growth.daysLeftInMonth(T.daysFromCivil(2026, 10, 1)))
        // February in a leap year.
        assertEquals(29, Growth.daysLeftInMonth(T.daysFromCivil(2028, 2, 1)))
    }

    @Test
    fun ratingAMonthTwiceReplacesRatherThanAppends() {
        val store = freshStore()
        val area = store.addGrowthArea(GrowthArea(name = "Patience", createdAt = T.now()))
        val month = Growth.monthIndex()

        store.rateGrowthArea(area.id, month, 2, "rough")
        store.rateGrowthArea(area.id, month, 4, "better than I thought")

        val rows = store.growthCheckins.items.value.filter { it.areaId == area.id }
        assertEquals(1, rows.size)
        assertEquals(4, rows.first().rating)
        assertEquals("better than I thought", rows.first().note)
    }

    @Test
    fun goodDeedsAreCountedByFilledMonthsNotStreaks() {
        val store = freshStore()
        val today = T.today()
        val month = Growth.monthIndex(today)

        // This month and three months ago — a gap in between on purpose.
        store.addDeed(GoodDeed(title = "Paid a bill", monthIndex = month, epochDay = today, createdAt = T.now()))
        store.addDeed(GoodDeed(title = "Gave a lift", monthIndex = month - 3, epochDay = today - 90, createdAt = T.now()))

        assertEquals(2, Growth.monthsWithDeeds(store, today))
        assertTrue(Growth.deedDoneThisMonth(store, today))

        val history = Growth.deedHistory(store, today)
        assertEquals(12, history.size)
        assertTrue(history.last())          // this month
        assertFalse(history[history.size - 2]) // last month, the gap
    }
}
