package dev.shike

import dev.shike.data.Search
import dev.shike.domain.*
import dev.shike.security.Crypto
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    @Test
    fun monthlyRuleClampsToLeapMonthAndReturnsToOriginalDay() {
        val rule = RepeatRule(unit = "MONTH", anchor = "2024-01-31", zone = "UTC")
        assertTrue(Recurrence.occurs(rule, LocalDate.parse("2024-02-29")))
        assertFalse(Recurrence.occurs(rule, LocalDate.parse("2024-02-28")))
        assertTrue(Recurrence.occurs(rule, LocalDate.parse("2024-03-31")))
    }

    @Test
    fun biweeklyRuleUsesCalendarWeeks() {
        val rule =
            RepeatRule(unit = "WEEK", interval = 2, anchor = "2026-11-17", weekdays = listOf(2, 4))
        assertTrue(Recurrence.occurs(rule, LocalDate.parse("2026-11-19")))
        assertFalse(Recurrence.occurs(rule, LocalDate.parse("2026-11-24")))
        assertTrue(Recurrence.occurs(rule, LocalDate.parse("2026-12-01")))
    }

    @Test
    fun daylightSavingGapHasDeterministicInstant() {
        val rule = RepeatRule(zone = "America/New_York", time = "02:30")
        assertEquals(
            Instant.parse("2026-03-08T07:30:00Z").toEpochMilli(),
            Recurrence.instant(rule, LocalDate.parse("2026-03-08")),
        )
    }

    @Test
    fun layoutRejectsCyclesAndInvalidParents() {
        val group = Widget(id = "root", type = "GROUP")
        val child = Widget(id = "child", parent = "root")
        assertTrue(LayoutRules.validate(listOf(group, child)))
        assertFalse(
            LayoutRules.validate(listOf(group.copy(parent = "child"), child.copy(type = "GROUP")))
        )
        assertFalse(LayoutRules.validate(listOf(child)))
    }

    @Test
    fun backupAuthenticatesPasswordAndPayload() {
        val secret = "测试备份内容".toByteArray()
        val password = "ShikeTest928".toCharArray()
        val backup = Crypto.backup(secret, password)
        assertArrayEquals(secret, Crypto.restore(backup, password))
        assertThrows(javax.crypto.AEADBadTagException::class.java) {
            Crypto.restore(backup, "WrongPass123".toCharArray())
        }
        backup[backup.lastIndex] = (backup.last().toInt() xor 8).toByte()
        assertThrows(javax.crypto.AEADBadTagException::class.java) {
            Crypto.restore(backup, password)
        }
    }

    @Test
    fun searchSupportsPinyinInitialsAndMisspelling() {
        val note = dev.shike.data.Item(kind = "NOTE", title = "四级听力", body = "listening")
        assertTrue(Search.matches(note, "sijitingli", Appearance()))
        assertTrue(Search.matches(note, "sjtl", Appearance()))
        assertTrue(Search.matches(note, "listning", Appearance()))
        assertFalse(Search.matches(note, "计划", Appearance()))
    }
}
