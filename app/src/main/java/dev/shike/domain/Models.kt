package dev.shike.domain

import java.time.*
import java.util.UUID
import kotlinx.serialization.Serializable

fun newId() = UUID.randomUUID().toString()

enum class Kind(val label: String) {
    TASK("任务"),
    SERIES("重复任务"),
    HABIT("习惯"),
    NOTE("笔记"),
    PLAN("计划"),
    EVENT("日程"),
    CAPTURE("快速记录"),
    FOLDER("文件夹"),
    TAG("标签"),
    CATEGORY("分类"),
    REVIEW("回顾"),
}

enum class TaskState(val label: String) {
    TODO("待办"),
    DOING("进行中"),
    DONE("已完成"),
    PAUSED("已搁置"),
    CANCELLED("已取消"),
    ARCHIVED("已归档");

    fun canTransition(to: TaskState) = this != to

    val active
        get() = this in listOf(TODO, DOING, PAUSED)
}

enum class CheckState(val label: String) {
    COMPLETE("完成"),
    PARTIAL("部分完成"),
    MISSED("未完成"),
    REST("休息日"),
}

@Serializable
data class RepeatRule(
    val unit: String = "DAY",
    val interval: Int = 1,
    val anchor: String = LocalDate.now().toString(),
    val zone: String = ZoneId.systemDefault().id,
    val time: String = "09:00",
    val weekdays: List<Int> = emptyList(),
    val paused: Boolean = false,
)

object Recurrence {
    fun occurs(rule: RepeatRule, date: LocalDate): Boolean {
        if (rule.paused || rule.interval !in 1..366) return false
        val anchor = LocalDate.parse(rule.anchor)
        if (date < anchor) return false
        val days = java.time.temporal.ChronoUnit.DAYS.between(anchor, date)
        return when (rule.unit) {
            "WEEK" ->
                java.time.temporal.ChronoUnit.WEEKS.between(
                    anchor.with(
                        java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
                    ),
                    date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                ) % rule.interval == 0L &&
                    date.dayOfWeek.value in rule.weekdays.ifEmpty { listOf(anchor.dayOfWeek.value) }
            "MONTH" ->
                java.time.temporal.ChronoUnit.MONTHS.between(
                    YearMonth.from(anchor),
                    YearMonth.from(date),
                ) % rule.interval == 0L &&
                    date.dayOfMonth == minOf(anchor.dayOfMonth, date.lengthOfMonth())
            else -> days % rule.interval == 0L
        }
    }

    fun instant(rule: RepeatRule, date: LocalDate): Long =
        LocalDateTime.of(date, LocalTime.parse(rule.time))
            .atZone(ZoneId.of(rule.zone))
            .toInstant()
            .toEpochMilli()

    fun instanceId(series: String, date: LocalDate): String =
        UUID.nameUUIDFromBytes("$series:$date".toByteArray()).toString()

    fun period(rule: RepeatRule, date: LocalDate): String =
        when (rule.unit) {
            "WEEK" ->
                date
                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .toString()
            "MONTH" -> YearMonth.from(date).toString()
            else -> date.toString()
        }

    fun due(rule: RepeatRule, date: LocalDate): Boolean =
        when (rule.unit) {
            "WEEK" -> occurs(rule.copy(weekdays = (1..7).toList()), date)
            "MONTH" ->
                !rule.paused &&
                    date >= LocalDate.parse(rule.anchor) &&
                    java.time.temporal.ChronoUnit.MONTHS.between(
                        YearMonth.parse(rule.anchor.take(7)),
                        YearMonth.from(date),
                    ) % rule.interval.coerceAtLeast(1) == 0L
            else -> occurs(rule, date)
        }
}

@Serializable
data class Widget(
    val id: String = newId(),
    val type: String = "TASK",
    val title: String = "今日任务",
    val parent: String? = null,
    val span: Int = 2,
    val locked: Boolean = false,
    val collapsed: Boolean = false,
    val color: Long = 0,
    val icon: String = "",
    val filter: String = "today",
    val sort: String = "due",
    val limit: Int = 8,
    val density: String = "global",
    val showDetails: Boolean = true,
)

@Serializable
data class Workspace(
    val id: String = newId(),
    val name: String = "日常生活",
    val icon: String = "Home",
    val widgets: List<Widget> = defaultWidgets(),
    val snapshots: List<LayoutSnapshot> = emptyList(),
)

@Serializable
data class LayoutSnapshot(
    val id: String = newId(),
    val name: String,
    val created: Long = System.currentTimeMillis(),
    val widgets: List<Widget>,
)

fun defaultWidgets() =
    listOf(
        Widget(type = "CAPTURE", title = "随手记下", filter = "all"),
        Widget(type = "TASK", title = "今日任务"),
        Widget(type = "HABIT", title = "习惯打卡", span = 1),
        Widget(type = "EVENT", title = "近期日程", span = 1),
    )

object LayoutRules {
    fun validate(widgets: List<Widget>): Boolean {
        if (widgets.size > 120 || widgets.map { it.id }.toSet().size != widgets.size) return false
        val ids = widgets.map { it.id }.toSet()
        return widgets.all { w ->
            var p = w.parent
            val visited = mutableSetOf(w.id)
            var depth = 0
            if (w.span !in 1..2 || w.limit !in 1..100) return@all false
            while (p != null) {
                if (p !in ids || !visited.add(p) || ++depth > 8) return@all false
                val parent = widgets.first { it.id == p }
                if (parent.type != "GROUP") return@all false
                p = parent.parent
            }
            true
        }
    }
}

@Serializable
data class Appearance(
    val theme: String = "system",
    val accent: Long = 0xFF527361,
    val background: Long = 0,
    val surface: Long = 0,
    val text: Long = 0,
    val darkAccent: Long = 0xFFAECDB4,
    val darkBackground: Long = 0,
    val darkSurface: Long = 0,
    val darkText: Long = 0,
    val fontScale: Float = 1f,
    val density: String = "comfortable",
    val motion: Boolean = true,
    val contrast: Boolean = false,
    val largeTargets: Boolean = false,
    val haptic: Boolean = true,
    val font: String = "system",
    val lineHeight: Float = 1.5f,
    val letterSpacing: Float = 0f,
    val performance: String = "balanced",
    val weekStart: Int = 1,
    val historyLimit: Int = 20,
    val trashDays: Int = 30,
    val autoParent: Boolean = true,
    val notifications: Boolean = true,
    val habitReminders: Boolean = true,
    val reviewReminders: Boolean = true,
    val quietStart: Int = 22,
    val quietEnd: Int = 8,
    val searchPinyin: Boolean = true,
    val searchFuzzy: Boolean = true,
    val synonyms: String = "学习=读书,阅读=读书",
    val lockHash: String = "",
    val biometric: Boolean = false,
    val backupEnabled: Boolean = false,
)
