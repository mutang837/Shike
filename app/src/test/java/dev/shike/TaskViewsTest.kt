package dev.shike

import dev.shike.data.Item
import dev.shike.data.TaskViews
import dev.shike.data.json
import dev.shike.domain.Recurrence
import dev.shike.domain.RepeatRule
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TaskViewsTest {
    private val now = Instant.parse("2026-11-22T08:00:00Z")
    private val today = LocalDate.parse("2026-11-22")
    private val rule = RepeatRule(anchor = "2026-11-01", zone = "Asia/Shanghai")
    private val series = Item(id = "series", kind = "SERIES", title = "每日阅读", zone = rule.zone,
        rule = json.encodeToString(RepeatRule.serializer(), rule))

    private fun task(day: LocalDate, state: String = "TODO", allDay: Boolean = true) =
        Item(id = day.toString(), kind = "TASK", title = "每日阅读", state = state,
            zone = rule.zone, due = Recurrence.instant(rule, day), allDay = allDay,
            seriesId = series.id, occurrence = day.toString(), created = 1, updated = 1)

    @Test fun allDayDeadlineUsesLocalDateNotClockTime() {
        assertFalse(TaskViews.isOverdue(task(today), now))
        assertTrue(TaskViews.isOverdue(task(today.minusDays(1)), now))
        assertTrue(TaskViews.isOverdue(task(today, allDay = false), now))
        assertFalse(TaskViews.isOverdue(task(today.plusDays(1), allDay = false), now))
        assertFalse(TaskViews.isOverdue(task(today.minusDays(1), "DONE"), now))
    }

    @Test fun cachedRepeatInstancesDoNotFloodNormalList() {
        val cached = (-3L..35L).map { task(today.plusDays(it)) }
        val independent = Item(kind = "TASK", title = "普通待办")
        val results = TaskViews.queue(listOf(series, independent) + cached, now)
        assertEquals(setOf(independent.id, today.toString()), results.map { it.id }.toSet())
        assertEquals(39, TaskViews.collection(listOf(series) + cached, "all", now).size)
    }

    @Test fun completionHidesCurrentAndOldBacklogButKeepsHistory() {
        val items = listOf(series, task(today.minusDays(1)), task(today, "DONE"), task(today.plusDays(1)))
        assertEquals(listOf(today.plusDays(1).toString()), TaskViews.queue(items, now).map { it.id })
        assertTrue(TaskViews.collection(items, "today", now).isEmpty())
        assertEquals(listOf(today.toString()), TaskViews.collection(items, "DONE", now).map { it.id })
    }

    @Test fun pausedSeriesDoesNotExposeUneditedUpcomingRecords() {
        val paused = series.copy(rule = json.encodeToString(RepeatRule.serializer(), rule.copy(paused = true)))
        assertTrue(TaskViews.queue(listOf(paused, task(today), task(today.plusDays(1))), now).isEmpty())
    }

    @Test fun longRepeatIntervalsAndFarDates() {
        val monthly = RepeatRule(unit = "MONTH", interval = 3, anchor = "2024-01-31")
        assertEquals(LocalDate.parse("2030-04-30"), Recurrence.nextOnOrAfter(monthly, LocalDate.parse("2030-02-01")))
        assertNull(Recurrence.nextOnOrAfter(monthly.copy(paused = true), today))
    }
}
