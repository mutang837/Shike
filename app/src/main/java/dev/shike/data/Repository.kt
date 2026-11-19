package dev.shike.data

import android.content.Context
import androidx.room.withTransaction
import dev.shike.domain.*
import dev.shike.notification.Reminders
import dev.shike.security.*
import java.io.File
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import net.sourceforge.pinyin4j.PinyinHelper

val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
data class DataBundle(
    val format: Int = 1,
    val items: List<Item>,
    val links: List<Link>,
    val history: List<History>,
    val checkins: List<Checkin>,
    val config: List<Config>,
)

data class AppData(
    val items: List<Item> = emptyList(),
    val links: List<Link> = emptyList(),
    val history: List<History> = emptyList(),
    val checkins: List<Checkin> = emptyList(),
    val config: List<Config> = emptyList(),
    val ready: Boolean = false,
) {
    val active
        get() = items.filter { it.deletedAt == null }

    val settings
        get() =
            config
                .firstOrNull { it.key == "appearance" }
                ?.let { runCatching { json.decodeFromString<Appearance>(it.value) }.getOrNull() }
                ?: Appearance()

    val workspaces
        get() =
            config
                .firstOrNull { it.key == "workspaces" }
                ?.let {
                    runCatching { json.decodeFromString<List<Workspace>>(it.value) }.getOrNull()
                }
                .orEmpty()

    fun linked(id: String) =
        active.filter { item ->
            links.any {
                (it.fromId == id && it.toId == item.id) || (it.toId == id && it.fromId == item.id)
            }
        }

    fun effective(habit: Item, date: LocalDate = LocalDate.now()): Checkin? {
        val rule =
            runCatching { json.decodeFromString<RepeatRule>(habit.rule) }.getOrDefault(RepeatRule())
        val period = Recurrence.period(rule, date)
        return checkins
            .filter { it.habitId == habit.id && it.period == period }
            .maxByOrNull { it.created }
    }

    fun progress(plan: Item): Int {
        if (!plan.autoProgress) return plan.progress
        val records = active
        val taskIds = mutableSetOf<String>()
        val planIds = mutableSetOf<String>()
        val habits = mutableSetOf<String>()
        val manual = mutableListOf<Int>()
        val pending = ArrayDeque<Item>().apply { add(plan) }
        while (pending.isNotEmpty()) {
            val next = pending.removeFirst()
            if (!planIds.add(next.id)) continue
            if (!next.autoProgress) {
                manual += next.progress
                continue
            }
            val related = linked(next.id)
            related
                .filter { it.kind == "TASK" && it.state !in listOf("CANCELLED", "ARCHIVED") }
                .forEach { taskIds += it.id }
            related.filter { it.kind == "HABIT" }.forEach { habits += it.id }
            records.filter { it.kind == "PLAN" && it.parentId == next.id }.forEach(pending::addLast)
        }
        val scores =
            manual +
                records.filter { it.id in taskIds }.map { if (it.state == "DONE") 100 else 0 } +
                records
                    .filter { it.id in habits }
                    .map { item ->
                        effective(item)?.let {
                            if (it.state == "COMPLETE") 100
                            else if (it.state == "PARTIAL")
                                (100 * it.value / it.target).toInt().coerceIn(0, 100)
                            else 0
                        } ?: 0
                    }
        return if (scores.isEmpty()) 0 else scores.average().toInt()
    }

    fun streak(habit: Item, date: LocalDate = LocalDate.now()): Int {
        val rule =
            runCatching { json.decodeFromString<RepeatRule>(habit.rule) }.getOrNull() ?: return 0
        val logs =
            checkins
                .asSequence()
                .filter { it.habitId == habit.id }
                .groupBy { it.period }
                .mapValues { it.value.maxByOrNull { log -> log.created } }
        var current = date
        var count = 0
        var first = true
        val anchor = LocalDate.parse(rule.anchor)
        while (current >= anchor) {
            val entry = logs[Recurrence.period(rule, current)]
            if (Recurrence.due(rule.copy(paused = false), current)) {
                if (entry?.state == "COMPLETE") count++
                else if (entry?.state != "REST" && !(first && entry == null)) break
                first = false
            }
            current =
                when (rule.unit) {
                    "WEEK" ->
                        current
                            .with(
                                java.time.temporal.TemporalAdjusters.previousOrSame(
                                    DayOfWeek.MONDAY
                                )
                            )
                            .minusDays(1)
                    "MONTH" -> current.withDayOfMonth(1).minusDays(1)
                    else -> current.minusDays(1)
                }
        }
        return count
    }
}

class Repository(val context: Context, val db: ShikeDb, private val scope: CoroutineScope) {
    val dao = db.dao()
    val data =
        combine(dao.items(), dao.links(), dao.history(), dao.checkins(), dao.config()) {
                a,
                b,
                c,
                d,
                e ->
                AppData(a, b, c, d, e, true)
            }
            .stateIn(scope, SharingStarted.Eagerly, AppData())

    suspend fun initialize() {
        if (dao.allConfig().none { it.key == "workspaces" })
            dao.putConfig(Config("workspaces", json.encodeToString(listOf(Workspace()))))
        generateInstances()
        Reminders.scheduleAll(context, dao.allItems(), readSettings())
    }

    suspend fun readSettings() =
        dao.allConfig()
            .firstOrNull { it.key == "appearance" }
            ?.let { json.decodeFromString<Appearance>(it.value) } ?: Appearance()

    suspend fun save(item: Item, related: List<String>? = null) {
        require(item.title.isNotBlank() && item.title.length <= 500) { "标题不能为空，且不能超过 500 字" }
        require(item.body.length <= 1_000_000) { "内容过长" }
        require(item.target > 0 && item.target.isFinite()) { "习惯目标必须大于 0" }
        require(item.start == null || item.due == null || item.start <= item.due) { "结束时间不能早于开始时间" }
        ZoneId.of(item.zone)
        val old = dao.allItems().find { it.id == item.id }
        val all = dao.allItems()
        item.parentId?.let { pid ->
            require(pid != item.id) { "不能将自己设为父项" }
            val visited = mutableSetOf(item.id)
            var p: String? = pid
            while (p != null) {
                require(visited.add(p)) { "不能循环嵌套" }
                p = all.find { it.id == p }?.parentId
            }
        }
        if (item.rule.isNotEmpty()) {
            val rule = json.decodeFromString<RepeatRule>(item.rule)
            require(rule.interval in 1..366)
            LocalDate.parse(rule.anchor)
            LocalTime.parse(rule.time)
            ZoneId.of(rule.zone)
        }
        val settings = readSettings()
        db.withTransaction {
            if (old != null) {
                if (
                    item.kind == "NOTE" &&
                        (old.body != item.body || old.title != item.title) &&
                        settings.historyLimit > 0
                ) {
                    dao.history(
                        History(
                            itemId = item.id,
                            action = "note_version",
                            payload = json.encodeToString(old),
                        )
                    )
                    dao.pruneVersions(item.id, settings.historyLimit)
                }
                if (old.state != item.state)
                    dao.history(
                        History(
                            itemId = item.id,
                            action = "state",
                            payload = "${old.state} → ${item.state}",
                        )
                    )
            } else {
                dao.history(History(itemId = item.id, action = "created", payload = item.kind))
                if (item.state != "TODO")
                    dao.history(
                        History(
                            itemId = item.id,
                            action = "state",
                            payload = "TODO → ${item.state}",
                        )
                    )
            }
            dao.put(item.copy(updated = System.currentTimeMillis()))
            if (related != null) {
                dao.clearLinks(item.id)
                related
                    .distinct()
                    .filter { it != item.id && all.any { a -> a.id == it } }
                    .forEach { dao.link(Link(item.id, it)) }
            }
            dao.clearParentLink(item.id)
            item.parentId
                ?.takeIf { pid -> all.any { it.id == pid } }
                ?.let { dao.link(Link(item.id, it, "parent")) }
            item.sourceId?.let { dao.link(Link(item.id, it, "source")) }
        }
        Reminders.schedule(context, item, settings)
        if (item.kind == "SERIES") {
            generateInstances()
            Reminders.scheduleAll(context, dao.allItems(), settings)
        }
        if (
            settings.autoParent &&
                item.kind == "TASK" &&
                item.parentId != null &&
                item.state == "DONE"
        ) {
            val after = dao.allItems()
            val parent = after.find { it.id == item.parentId && it.deletedAt == null }
            val children =
                after.filter {
                    it.parentId == item.parentId && it.kind == "TASK" && it.deletedAt == null
                }
            if (
                parent != null &&
                    TaskState.valueOf(parent.state).active &&
                    children.isNotEmpty() &&
                    children.all { it.state == "DONE" }
            )
                save(parent.copy(state = "DONE"))
        }
        refreshPortableBackup()
    }

    suspend fun transition(item: Item, to: TaskState) {
        val from = TaskState.valueOf(item.state)
        require(from.canTransition(to))
        save(item.copy(state = to.name))
    }

    suspend fun trash(item: Item) {
        dao.put(
            item.copy(deletedAt = System.currentTimeMillis(), updated = System.currentTimeMillis())
        )
        dao.history(History(itemId = item.id, action = "trash", payload = "移入回收站"))
        Reminders.cancel(context, item.id)
        refreshPortableBackup()
    }

    suspend fun restore(item: Item) {
        val all = dao.allItems()
        val folder = all.find { it.id == item.folderId && it.deletedAt == null }
        save(item.copy(deletedAt = null, folderId = folder?.id))
    }

    suspend fun erase(item: Item) {
        db.withTransaction {
            dao.eraseItem(item.id)
            dao.eraseLinks(item.id)
            dao.eraseHistory(item.id)
            dao.eraseCheckins(item.id)
            dao.allItems()
                .filter {
                    it.parentId == item.id || it.folderId == item.id || it.categoryId == item.id
                }
                .forEach {
                    dao.put(
                        it.copy(
                            parentId = it.parentId.takeUnless { p -> p == item.id },
                            folderId = it.folderId.takeUnless { p -> p == item.id },
                            categoryId = it.categoryId.takeUnless { p -> p == item.id },
                        )
                    )
                }
        }
        Reminders.cancel(context, item.id)
        refreshPortableBackup()
    }

    suspend fun checkin(
        habit: Item,
        value: Double,
        state: CheckState,
        date: LocalDate = LocalDate.now(),
    ) {
        require(date <= LocalDate.now()) { "不能提前打卡" }
        require(value >= 0 && value.isFinite())
        val rule = json.decodeFromString<RepeatRule>(habit.rule)
        dao.checkin(
            Checkin(
                habitId = habit.id,
                period = Recurrence.period(rule, date),
                date = date.toString(),
                state = state.name,
                value = value,
                target = habit.target,
                unit = habit.unit,
            )
        )
        refreshPortableBackup()
    }

    suspend fun saveSettings(settings: Appearance) {
        require(settings.historyLimit in 0..1000 && settings.trashDays in 0..36500)
        dao.putConfig(Config("appearance", json.encodeToString(settings)))
        Reminders.scheduleAll(context, dao.allItems(), settings)
        refreshPortableBackup()
    }

    suspend fun saveWorkspaces(workspaces: List<Workspace>) {
        require(
            workspaces.isNotEmpty() &&
                workspaces.size <= 30 &&
                workspaces.all { LayoutRules.validate(it.widgets) }
        ) {
            "工作空间布局无效或数量超出限制"
        }
        dao.putConfig(Config("workspaces", json.encodeToString(workspaces)))
        refreshPortableBackup()
    }

    suspend fun generateInstances(through: LocalDate = LocalDate.now().plusDays(35)) {
        val all = dao.allItems()
        val ids = all.mapTo(hashSetOf()) { it.id }
        val today = LocalDate.now()
        db.withTransaction {
            for (series in all.filter { it.kind == "SERIES" && it.deletedAt == null }) {
                val rule =
                    runCatching { json.decodeFromString<RepeatRule>(series.rule) }.getOrNull()
                        ?: continue
                if (rule.paused) continue
                val linked =
                    dao.allLinks().filter {
                        it.type == "related" && (it.fromId == series.id || it.toId == series.id)
                    }
                var date = today
                while (date <= through) {
                    if (Recurrence.occurs(rule, date)) {
                        val id = Recurrence.instanceId(series.id, date)
                        if (ids.add(id)) {
                            val now = System.currentTimeMillis()
                            dao.put(
                                series.copy(
                                    id = id,
                                    kind = "TASK",
                                    state = "TODO",
                                    start = null,
                                    due = Recurrence.instant(rule, date),
                                    created = now,
                                    updated = now,
                                    seriesId = series.id,
                                    occurrence = date.toString(),
                                    rule = "",
                                )
                            )
                            linked.forEach { l ->
                                dao.link(Link(id, if (l.fromId == series.id) l.toId else l.fromId))
                            }
                        }
                    }
                    date = date.plusDays(1)
                }
            }
        }
    }

    suspend fun updateSeriesFuture(series: Item, related: List<String>? = null) {
        val old =
            dao.allItems().filter {
                it.seriesId == series.id &&
                    it.occurrence >= LocalDate.now().toString() &&
                    it.state == "TODO" &&
                    it.deletedAt == null &&
                    it.updated == it.created
            }
        db.withTransaction {
            old.forEach {
                dao.eraseLinks(it.id)
                dao.eraseItem(it.id)
            }
            save(series, related)
        }
        old.forEach { item ->
            if (dao.allItems().none { it.id == item.id }) Reminders.cancel(context, item.id)
        }
    }

    suspend fun bundle(): DataBundle =
        db.withTransaction {
            DataBundle(
                items = dao.allItems(),
                links = dao.allLinks(),
                history = dao.allHistory(),
                checkins = dao.allCheckins(),
                config = dao.allConfig(),
            )
        }

    suspend fun export(password: CharArray): ByteArray =
        withContext(Dispatchers.Default) {
            Crypto.backup(json.encodeToString(bundle()).toByteArray(), password)
        }

    suspend fun import(bytes: ByteArray, password: CharArray) {
        val payload =
            withContext(Dispatchers.Default) {
                json.decodeFromString<DataBundle>(String(Crypto.restore(bytes, password)))
            }
        require(
            payload.format == 1 &&
                payload.items.size <= 100000 &&
                payload.history.size <= 500000 &&
                payload.checkins.size <= 500000
        ) {
            "备份版本不支持或内容过多"
        }
        require(payload.items.map { it.id }.distinct().size == payload.items.size) { "备份存在重复标识" }
        val ids = payload.items.map { it.id }.toSet()
        require(
            payload.items.all {
                runCatching {
                        Kind.valueOf(it.kind)
                        TaskState.valueOf(it.state)
                        ZoneId.of(it.zone)
                        if (it.rule.isNotBlank()) json.decodeFromString<RepeatRule>(it.rule)
                        true
                    }
                    .getOrDefault(false)
            }
        ) {
            "备份对象格式无效"
        }
        require(
            payload.links.all { it.fromId in ids && it.toId in ids } &&
                payload.checkins.all {
                    it.habitId in ids &&
                        runCatching {
                                LocalDate.parse(it.date)
                                CheckState.valueOf(it.state)
                                it.target > 0 && it.value >= 0
                            }
                            .getOrDefault(false)
                }
        ) {
            "备份关联不完整"
        }
        val ws =
            payload.config
                .find { it.key == "workspaces" }
                ?.let { json.decodeFromString<List<Workspace>>(it.value) } ?: error("备份缺少工作空间")
        require(ws.isNotEmpty() && ws.all { LayoutRules.validate(it.widgets) }) { "备份布局无效" }
        // Validate every record before touching live data; the replacement is a single transaction.
        val previous = dao.allItems()
        val restoredSettings =
            payload.config
                .find { it.key == "appearance" }
                ?.let { json.decodeFromString<Appearance>(it.value) } ?: Appearance()
        require(
            restoredSettings.weekStart in 1..7 &&
                restoredSettings.quietStart in 0..23 &&
                restoredSettings.quietEnd in 0..23
        ) {
            "备份设置无效"
        }
        db.withTransaction {
            dao.clearAllLinks()
            dao.clearCheckins()
            dao.clearHistory()
            dao.clearItems()
            dao.clearConfig()
            dao.putAll(payload.items)
            payload.links.forEach { dao.link(it) }
            payload.history.forEach { dao.history(it) }
            payload.checkins.forEach { dao.checkin(it) }
            payload.config.filter { it.key != "appearance" }.forEach { dao.putConfig(it) }
            dao.putConfig(
                Config(
                    "appearance",
                    json.encodeToString(
                        restoredSettings.copy(
                            lockHash = "",
                            biometric = false,
                            backupEnabled = false,
                        )
                    ),
                )
            )
        }
        previous.forEach { Reminders.cancel(context, it.id) }
        KeyVault(context).clearBackupPassword()
        Reminders.scheduleAll(context, payload.items, readSettings())
    }

    suspend fun configureSystemBackup(password: CharArray) {
        KeyVault(context).saveBackupPassword(password)
        saveSettings(readSettings().copy(backupEnabled = true))
    }

    suspend fun refreshPortableBackup() {
        val password = KeyVault(context).backupPassword() ?: return
        try {
            val bytes = export(password)
            KeyVault.atomicWrite(File(context.filesDir, "portable/latest.shike"), bytes)
            android.app.backup.BackupManager(context).dataChanged()
        } finally {
            password.fill('\u0000')
        }
    }
}

object Search {
    private val cache =
        object : LinkedHashMap<String, String>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) =
                size > 2000
        }

    @Synchronized
    fun normalize(value: String, pinyin: Boolean = true): String {
        if (!pinyin) return value.lowercase()
        cache[value]?.let {
            return it
        }
        val full = StringBuilder()
        val initials = StringBuilder()
        value.forEach { c ->
            val py =
                PinyinHelper.toHanyuPinyinStringArray(c)?.firstOrNull()?.filter { !it.isDigit() }
            full.append(py ?: c)
            initials.append(py?.firstOrNull() ?: c)
        }
        return (value + " " + full + " " + initials).lowercase().also { cache[value] = it }
    }

    fun matches(item: Item, query: String, settings: Appearance): Boolean {
        val words = query.trim().lowercase().split(Regex("\\s+"))
        val text = normalize("${item.title} ${item.body} ${item.tags}", settings.searchPinyin)
        return words.all { word ->
            val synonyms =
                settings.synonyms
                    .split(',', '\n')
                    .map { it.split('=') }
                    .firstOrNull { word in it }
                    .orEmpty()
            text.contains(word) ||
                synonyms.any { text.contains(it) } ||
                (settings.searchFuzzy &&
                    word.length >= 3 &&
                    text.split(Regex("[\\s,，。]+")).any { distance(it, word) <= 1 })
        }
    }

    fun distance(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 1) return 2
        var prev = IntArray(b.length + 1) { it }
        a.forEachIndexed { i, c ->
            val cur = IntArray(b.length + 1)
            cur[0] = i + 1
            b.forEachIndexed { j, d ->
                cur[j + 1] = minOf(prev[j + 1] + 1, cur[j] + 1, prev[j] + if (c == d) 0 else 1)
            }
            prev = cur
        }
        return prev.last()
    }
}
