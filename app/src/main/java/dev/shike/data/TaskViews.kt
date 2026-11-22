package dev.shike.data

import dev.shike.domain.RepeatRule
import dev.shike.domain.TaskState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** UI projections of saved records. History and the underlying database are never modified. */
object TaskViews {
    private fun zone(item: Item) = ZoneId.of(item.zone)

    fun date(item: Item, time: Long?): LocalDate? =
        time?.let { Instant.ofEpochMilli(it).atZone(zone(item)).toLocalDate() }

    fun isPending(item: Item): Boolean =
        item.kind == "TASK" && item.deletedAt == null &&
            item.state in setOf(TaskState.TODO.name, TaskState.DOING.name, TaskState.PAUSED.name)

    fun isOverdue(item: Item, now: Instant = Instant.now()): Boolean {
        val due = item.due ?: return false
        if (!isPending(item)) return false
        return if (item.allDay) {
            date(item, due)!! < now.atZone(zone(item)).toLocalDate()
        } else due < now.toEpochMilli()
    }

    fun isToday(item: Item, now: Instant = Instant.now()): Boolean {
        if (!isPending(item)) return false
        val today = now.atZone(zone(item)).toLocalDate()
        val start = date(item, item.start)
        return (start == null || start <= today) &&
            (item.due == null || date(item, item.due)!! <= today || start != null)
    }

    fun seriesEnabled(series: Item?): Boolean =
        series != null && series.kind == "SERIES" && series.deletedAt == null &&
            series.state !in setOf("DONE", "CANCELLED", "ARCHIVED") &&
            runCatching { !json.decodeFromString<RepeatRule>(series.rule).paused }.getOrDefault(false)

    fun visibleOccurrence(item: Item, series: Item?, now: Instant = Instant.now()): Boolean {
        if (item.seriesId == null || seriesEnabled(series)) return true
        if (item.updated != item.created || !isPending(item)) return true
        // A stopped series must not expose cached future occurrences. Its past remains accessible.
        val today = now.atZone(zone(item)).toLocalDate()
        return date(item, item.due)?.isBefore(today) == true
    }

    fun queue(items: List<Item>, now: Instant = Instant.now()): List<Item> {
        val series = items.filter { it.kind == "SERIES" }.associateBy { it.id }
        val tasks = items.filter { it.kind == "TASK" && it.deletedAt == null }
        val single = tasks.filter { it.seriesId == null && isPending(it) }
        val groups = tasks.filter { it.seriesId != null }.groupBy { it.seriesId }
        val repeated = groups.mapNotNull { (id, group) ->
            val template = series[id]
            val active = group.filter(::isPending)
            if (!seriesEnabled(template)) {
                // An independently edited occurrence remains a real task when its series is paused.
                return@mapNotNull active.filter { it.updated != it.created }.minByOrNull { it.due ?: Long.MAX_VALUE }
            }
            if (active.isEmpty()) return@mapNotNull null
            val today = now.atZone(zone(template!!)).toLocalDate()
            fun day(item: Item) = date(item, item.due) ?: runCatching { LocalDate.parse(item.occurrence) }.getOrNull()
            val current = active.filter { day(it) == today }.minByOrNull { it.due ?: Long.MAX_VALUE }
            if (current != null) return@mapNotNull current
            val lastPast = group.filter { day(it)?.let { d -> d <= today } == true }
                .maxWithOrNull(compareBy<Item> { day(it) }.thenBy { it.updated })
            // Closing today's occurrence suppresses earlier unfinished occurrences from today's queue.
            if (lastPast != null && isPending(lastPast)) return@mapNotNull lastPast
            active.filter { day(it)?.isAfter(today) == true }.minByOrNull { it.due ?: Long.MAX_VALUE }
                ?: active.filter { day(it) == null }.firstOrNull()
        }
        return single + repeated
    }

    fun collection(items: List<Item>, filter: String, now: Instant = Instant.now()): List<Item> =
        when (filter) {
            "open", "pending" -> queue(items, now)
            "today" -> queue(items, now).filter { isToday(it, now) }
            "TODO", "DOING", "PAUSED" -> queue(items, now).filter { it.state == filter }
            "all" -> items.filter { it.kind == "TASK" && it.deletedAt == null }
                .filter { it.seriesId == null || !isPending(it) || visibleOccurrence(it, items.find { parent -> parent.id == it.seriesId }, now) }
            else -> items.filter { it.kind == "TASK" && it.deletedAt == null && it.state == filter }
        }
}
