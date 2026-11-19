package dev.shike.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.shike.data.*
import dev.shike.domain.*
import java.time.*

val modules =
    listOf(
        "TASK",
        "SERIES",
        "HABIT",
        "NOTE",
        "PLAN",
        "calendar",
        "EVENT",
        "statistics",
        "review",
        "FOLDER",
        "TAG",
        "CATEGORY",
        "trash",
        "spaces",
    )

@Composable
fun Overview(data: AppData, page: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("综合", "管理记录、计划和工作空间") }
        items(modules.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { k ->
                    Surface(
                        onClick = { page(k) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Icon(
                                when (k) {
                                    "calendar" -> kindIcon("EVENT")
                                    "statistics" -> ShikeIcons.BarChart
                                    "review" -> kindIcon("REVIEW")
                                    "trash" -> ShikeIcons.DeleteOutline
                                    "spaces" -> ShikeIcons.Widgets
                                    else -> kindIcon(k)
                                },
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(pageTitle(k), style = MaterialTheme.typography.titleSmall)
                            val count = data.active.count { it.kind == k }
                            if (count > 0)
                                Text(
                                    "${count} 项",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecordPage(data: AppData, open: (Item) -> Unit, capture: () -> Unit, add: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("CAPTURE") }
    val records = data.active.filter { it.kind == filter }
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionTitle("记录", "随手留下想法，有空再整理") {
                FilledTonalIconButton(onClick = capture) { Icon(ShikeIcons.Add, "新建记录") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("CAPTURE" to "随手记", "NOTE" to "笔记").forEach { (k, label) ->
                    FilterChip(filter == k, { filter = k }, { Text(label) })
                }
            }
        }
        if (records.isEmpty())
            item {
                Surface(shape = RoundedCornerShape(12.dp)) { EmptyState(filter) { add(filter) } }
            }
        items(records, key = { it.id }) { record ->
            Surface(
                onClick = { open(record) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        formatDate(record.created),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(record.title, style = MaterialTheme.typography.titleMedium)
                    if (record.body.isNotBlank() && record.body != record.title) {
                        Spacer(Modifier.height(5.dp))
                        Text(record.body, maxLines = 3, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (data.linked(record.id).isNotEmpty())
                        Text(
                            "已关联 ${data.linked(record.id).size} 项",
                            style = MaterialTheme.typography.labelSmall,
                        )
                }
            }
        }
    }
}

@Composable
fun CollectionPage(
    kind: String,
    data: AppData,
    open: (Item) -> Unit,
    toggle: (Item) -> Unit,
    add: (String) -> Unit,
) {
    var filter by rememberSaveable(kind) { mutableStateOf("all") }
    var sort by rememberSaveable(kind) { mutableStateOf("newest") }
    var organizer by rememberSaveable(kind) { mutableStateOf("") }
    var tag by rememberSaveable(kind) { mutableStateOf("") }
    var options by rememberSaveable(kind) { mutableStateOf(false) }
    val visible =
        data.active
            .filter { item ->
                item.kind == kind &&
                    (filter == "all" ||
                        filter == "open" && item.state in listOf("TODO", "DOING", "PAUSED") ||
                        filter == item.state) &&
                    (organizer.isBlank() ||
                        organizer == "none" && data.active.none { it.id == item.folderId } ||
                        organizer == item.folderId ||
                        organizer == item.categoryId) &&
                    (tag.isBlank() || tag in item.tags.split(","))
            }
            .let { list ->
                when (sort) {
                    "title" -> list.sortedBy { it.title }
                    "priority" -> list.sortedByDescending { it.priority }
                    "due" -> list.sortedBy { it.due ?: Long.MAX_VALUE }
                    else -> list.sortedByDescending { it.updated }
                }
            }
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { add(kind) }) {
                    Icon(ShikeIcons.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("创建" + pageTitle(kind))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { options = !options }) {
                    Icon(ShikeIcons.FilterList, "筛选与排序")
                }
            }
        }
        if (options)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (kind == "TASK")
                        Choice(
                            "状态",
                            filter,
                            listOf("all" to "全部", "open" to "待处理") +
                                TaskState.entries.map { it.name to it.label },
                            { filter = it },
                        )
                    Choice(
                        "排序",
                        sort,
                        listOf(
                            "newest" to "最近修改",
                            "title" to "名称",
                            "due" to "时间",
                            "priority" to "优先级",
                        ),
                        { sort = it },
                    )
                    val nodes =
                        if (kind == "NOTE") data.active.filter { it.kind == "FOLDER" }
                        else data.active.filter { it.kind == "CATEGORY" }
                    Choice(
                        if (kind == "NOTE") "文件夹" else "分类",
                        organizer,
                        listOf("" to "全部") +
                            (if (kind == "NOTE") listOf("none" to "未分类") else emptyList()) +
                            nodes.map { it.id to it.title },
                        { organizer = it },
                    )
                    Choice(
                        "标签",
                        tag,
                        listOf("" to "全部") +
                            data.active.filter { it.kind == "TAG" }.map { it.id to it.title },
                        { tag = it },
                    )
                }
            }
        if (visible.isEmpty())
            item { Surface(shape = RoundedCornerShape(12.dp)) { EmptyState(kind) { add(kind) } } }
        items(visible, key = { it.id }) { entry ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    entry.parentId?.let { parent ->
                        data.active
                            .find { it.id == parent }
                            ?.let {
                                Text(
                                    "↳ " + it.title,
                                    Modifier.padding(top = 8.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                    }
                    ItemRow(entry, data, open, toggle)
                }
            }
        }
    }
}

@Composable
fun SearchDialog(data: AppData, vm: AppViewModel, close: () -> Unit, open: (Item) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("") }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Item>>(emptyList()) }
    val recent =
        data.config
            .find { it.key == "searchHistory" }
            ?.let { runCatching { json.decodeFromString<List<String>>(it.value) }.getOrNull() }
            .orEmpty()
    LaunchedEffect(query, kind, data.active, data.settings) {
        results =
            if (query.isBlank()) emptyList()
            else
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    data.active
                        .asSequence()
                        .filter { kind.isBlank() || it.kind == kind }
                        .filter { item ->
                            Search.matches(
                                item.copy(
                                    tags =
                                        item.tags
                                            .split(",")
                                            .mapNotNull { id ->
                                                data.active.find { it.id == id }?.title
                                            }
                                            .joinToString(" ")
                                ),
                                query,
                                data.settings,
                            ) ||
                                data.history.any {
                                    it.itemId == item.id &&
                                        it.payload.contains(query, ignoreCase = true)
                                }
                        }
                        .sortedWith(
                            compareByDescending<Item> { it.title.equals(query, true) }
                                .thenByDescending { it.title.contains(query, true) }
                                .thenByDescending { it.updated }
                        )
                        .take(100)
                        .toList()
                }
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = close,
        properties =
            androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = !advanced),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            modifier =
                if (advanced) Modifier.fillMaxSize().padding(12.dp) else Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("全局搜索", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { advanced = !advanced }) {
                        Icon(ShikeIcons.FilterList, "高级筛选")
                    }
                    IconButton(onClick = close) { Icon(ShikeIcons.Close, "关闭搜索") }
                }
                OutlinedTextField(
                    query,
                    { query = it.take(120) },
                    placeholder = { Text("搜索标题、正文、拼音…") },
                    leadingIcon = { Icon(ShikeIcons.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (advanced)
                    Choice(
                        "对象类型",
                        kind,
                        listOf("" to "全部") + Kind.entries.map { it.name to it.label },
                        { kind = it },
                    )
                if (query.isBlank() && recent.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("最近搜索", Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                vm.run { vm.repo.dao.putConfig(Config("searchHistory", "[]")) }
                            }
                        ) {
                            Text("清除")
                        }
                    }
                    recent.take(8).forEach { TextButton(onClick = { query = it }) { Text(it) } }
                }
                if (query.isNotBlank()) {
                    if (results.isEmpty()) Text("没有找到匹配内容", Modifier.padding(vertical = 12.dp))
                    else
                        LazyColumn(Modifier.heightIn(max = if (advanced) 900.dp else 350.dp)) {
                            items(results, key = { it.id }) { entry ->
                                ItemRow(
                                    entry,
                                    data,
                                    {
                                        vm.run {
                                            vm.repo.dao.putConfig(
                                                Config(
                                                    "searchHistory",
                                                    json.encodeToString(
                                                        (listOf(query.trim()) + recent)
                                                            .distinct()
                                                            .take(15)
                                                    ),
                                                )
                                            )
                                        }
                                        open(it)
                                    },
                                    open,
                                )
                                HorizontalDivider()
                            }
                        }
                }
            }
        }
    }
}

@Composable
fun CalendarPage(
    data: AppData,
    open: (Item) -> Unit,
    toggle: (Item) -> Unit,
    add: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    var rawDay by rememberSaveable { mutableStateOf(today.toString()) }
    var mode by rememberSaveable { mutableStateOf("month") }
    var filter by rememberSaveable { mutableStateOf("") }
    val day = LocalDate.parse(rawDay)
    val month = YearMonth.from(day)
    val firstWeekday = DayOfWeek.of(data.settings.weekStart.coerceIn(1, 7))
    val shift = { amount: Long ->
        rawDay =
            when (mode) {
                "month" -> day.plusMonths(amount)
                "week" -> day.plusWeeks(amount)
                else -> day.plusDays(amount)
            }.toString()
    }
    fun entries(date: LocalDate): List<Item> =
        data.active
            .filter { item ->
                if (
                    item.kind !in listOf("TASK", "EVENT", "HABIT") ||
                        filter.isNotBlank() && item.kind != filter
                )
                    return@filter false
                if (item.kind == "HABIT")
                    return@filter data.checkins.any {
                        it.habitId == item.id && it.date == date.toString()
                    } ||
                        runCatching {
                                Recurrence.occurs(
                                    json.decodeFromString<RepeatRule>(item.rule),
                                    date,
                                )
                            }
                            .getOrDefault(false)
                val start =
                    localDate(
                        item.start ?: item.due,
                        if (item.allDay) item.zone else ZoneId.systemDefault().id,
                    )
                val end =
                    localDate(
                        item.due ?: item.start,
                        if (item.allDay) item.zone else ZoneId.systemDefault().id,
                    )
                start != null && end != null && date >= start && date <= end
            }
            .sortedBy { it.start ?: it.due ?: 0 }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("month" to "月", "week" to "周", "day" to "日").forEach { (k, n) ->
                    FilterChip(mode == k, { mode = k }, { Text(n) })
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { rawDay = today.toString() }) { Text("今天") }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { shift(-1) }) { Icon(ShikeIcons.ChevronLeft, "上一周期") }
                Text(
                    if (mode == "month") "${month.year} 年 ${month.monthValue} 月"
                    else day.toString(),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = { shift(1) }) { Icon(ShikeIcons.ChevronRight, "下一周期") }
            }
        }
        item {
            Choice(
                "显示类型",
                filter,
                listOf("" to "全部", "TASK" to "任务", "EVENT" to "日程", "HABIT" to "习惯"),
                { filter = it },
            )
        }
        if (mode != "day") {
            val from =
                (if (mode == "month") month.atDay(1) else day).with(
                    java.time.temporal.TemporalAdjusters.previousOrSame(firstWeekday)
                )
            val days = (0L until if (mode == "month") 42L else 7L).map { from.plusDays(it) }
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(6.dp)) {
                        Row {
                            (0L..6L).forEach { n ->
                                val d = from.plusDays(n)
                                Box(
                                    Modifier.weight(1f).height(28.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "一二三四五六日"[d.dayOfWeek.value - 1].toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                        days.chunked(7).forEach { week ->
                            Row {
                                week.forEach { date ->
                                    val count = entries(date).size
                                    Surface(
                                        onClick = { rawDay = date.toString() },
                                        selected = date == day,
                                        modifier = Modifier.weight(1f).padding(2.dp),
                                        color =
                                            if (date == day)
                                                MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Column(
                                            Modifier.heightIn(min = 45.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            Text(
                                                date.dayOfMonth.toString(),
                                                color =
                                                    if (date.month == month.month)
                                                        MaterialTheme.colorScheme.onSurface
                                                    else
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                            .copy(alpha = .65f),
                                                fontWeight =
                                                    if (date == today) FontWeight.Bold
                                                    else FontWeight.Normal,
                                            )
                                            if (count > 0)
                                                Text(
                                                    "$count 项",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionTitle("${day.monthValue} 月 ${day.dayOfMonth} 日") {
                FilledTonalIconButton(onClick = { add(day) }) {
                    Icon(ShikeIcons.Add, "为所选日期创建日程")
                }
            }
        }
        val records = entries(day)
        if (records.isEmpty()) item { EmptyState("EVENT") { add(day) } }
        items(records, key = { it.id }) { e ->
            Surface(shape = RoundedCornerShape(12.dp)) {
                Box(Modifier.padding(horizontal = 10.dp)) { ItemRow(e, data, open, toggle) }
            }
        }
    }
}

@Composable
fun InsightsPage(review: Boolean, data: AppData, vm: AppViewModel, open: (Item) -> Unit) {
    var period by rememberSaveable { mutableStateOf("week") }
    var days by rememberSaveable { mutableStateOf("14") }
    var dimension by rememberSaveable { mutableStateOf("all") }
    var reflection by rememberSaveable { mutableStateOf("") }
    val today = LocalDate.now()
    val from =
        when (period) {
            "day" -> today
            "month" -> today.withDayOfMonth(1)
            "custom" -> today.minusDays((days.toLongOrNull() ?: 14).coerceIn(1, 365) - 1)
            else ->
                today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }
    val done =
        data.history.filter {
            it.action == "state" && it.payload.endsWith("→ DONE") && localDate(it.created)!! >= from
        }
    val completedIds = done.map { it.itemId }.toSet()
    val logs =
        data.checkins
            .filter { LocalDate.parse(it.date) >= from }
            .groupBy { it.habitId to it.period }
            .values
            .mapNotNull { it.maxByOrNull { c -> c.created } }
    val logCount = logs.count { it.state == "COMPLETE" }
    val logTotal = logs.count { it.state != "REST" }
    val plans = data.active.filter { it.kind == "PLAN" }
    val notes = data.active.filter { it.kind == "NOTE" && localDate(it.updated)!! >= from }
    val hours = logs.filter { it.unit == "分钟" }.sumOf { it.value }
    val summary =
        "完成任务 ${completedIds.size} 项\n习惯完成 $logCount / $logTotal 次\n更新笔记 ${notes.size} 篇\n记录时长 ${hours.toInt()} 分钟"
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Choice(
                "统计周期",
                period,
                listOf("day" to "今天", "week" to "本周", "month" to "本月", "custom" to "自定义"),
                { period = it },
            )
        }
        if (period == "custom")
            item {
                OutlinedTextField(
                    days,
                    { days = it.filter(Char::isDigit).take(3) },
                    label = { Text("最近多少天") },
                )
            }
        if (!review)
            item {
                Choice(
                    "统计维度",
                    dimension,
                    listOf("all" to "全部", "task" to "任务", "habit" to "习惯与时长", "plan" to "计划"),
                    { dimension = it },
                )
            }
        item {
            Surface(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "$from 至 $today",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (dimension in listOf("all", "task"))
                        Text(
                            "完成任务 ${completedIds.size} 项",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    if (dimension in listOf("all", "habit")) {
                        Text("习惯完成 $logCount / $logTotal 次")
                        LinearProgressIndicator(
                            progress = { if (logTotal > 0) logCount.toFloat() / logTotal else 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("记录时长 ${hours.toInt()} 分钟")
                    }
                    if (dimension in listOf("all", "plan"))
                        plans.forEach { p ->
                            TextButton(onClick = { open(p) }) {
                                Text("${p.title} · ${data.progress(p)}%")
                            }
                        }
                }
            }
        }
        if (!review && dimension in listOf("all", "task"))
            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("近期完成趋势", style = MaterialTheme.typography.titleMedium)
                    val countDays =
                        minOf(7L, java.time.temporal.ChronoUnit.DAYS.between(from, today) + 1)
                            .toInt()
                    val counts =
                        (0 until countDays)
                            .map { today.minusDays(it.toLong()) }
                            .reversed()
                            .map { d -> d to done.count { localDate(it.created) == d } }
                    val max = counts.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                    counts.forEach { (d, n) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("${d.monthValue}/${d.dayOfMonth}", Modifier.width(49.dp))
                            LinearProgressIndicator(
                                progress = { n.toFloat() / max },
                                modifier = Modifier.weight(1f),
                            )
                            Text("$n 项")
                        }
                    }
                }
            }
        if (review) {
            item {
                OutlinedTextField(
                    reflection,
                    { reflection = it },
                    minLines = 3,
                    label = { Text("写下这段时间的反思") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(
                    onClick = {
                        val item =
                            Item(
                                kind = "REVIEW",
                                title = "$from — $today 回顾",
                                body = "$summary\n\n$reflection",
                            )
                        vm.save(
                            item,
                            (completedIds + notes.map { it.id } + logs.map { it.habitId }).toList(),
                        )
                        reflection = ""
                    }
                ) {
                    Text("保存回顾")
                }
            }
            item { Text("历史回顾", style = MaterialTheme.typography.titleMedium) }
            items(data.active.filter { it.kind == "REVIEW" }, key = { it.id }) { entry ->
                ItemRow(entry, data, open, open)
            }
        }
    }
}

@Composable
fun TrashPage(data: AppData, vm: AppViewModel) {
    var target by remember { mutableStateOf<Item?>(null) }
    var verify by remember { mutableStateOf(false) }
    val trashed = data.items.filter { it.deletedAt != null }
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionTitle(
                "已删除项目",
                if (data.settings.trashDays == 0) "保留至手动清理" else "保留 ${data.settings.trashDays} 天",
            )
        }
        if (trashed.isEmpty()) item { Text("回收站为空", Modifier.padding(16.dp)) }
        items(trashed, key = { it.id }) { item ->
            Surface(shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${Kind.valueOf(item.kind).label} · ${formatDate(item.deletedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row {
                        TextButton(onClick = { vm.run("已恢复") { vm.repo.restore(item) } }) {
                            Text("恢复")
                        }
                        TextButton(
                            onClick = {
                                target = item
                                verify = false
                            }
                        ) {
                            Text("永久删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    target?.let { item ->
        Confirm(
            if (verify) "最后确认" else "永久删除？",
            if (verify) "「${item.title}」及其历史将无法恢复。" else "永久删除会移除这个项目。",
            { target = null },
        ) {
            if (verify) {
                target = null
                vm.run("已永久删除") { vm.repo.erase(item) }
            } else verify = true
        }
    }
}
