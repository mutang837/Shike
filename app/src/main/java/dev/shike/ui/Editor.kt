package dev.shike.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.shike.data.*
import dev.shike.domain.*
import java.time.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ItemEditor(
    request: EditRequest,
    data: AppData,
    vm: AppViewModel,
    close: () -> Unit,
    open: (Item) -> Unit,
    addChild: (String, String) -> Unit,
    convert: (String, Item) -> Unit,
) {
    val original = request.item
    val originalKind = Kind.valueOf(request.kind)
    val source = request.source
    var kind by rememberSaveable { mutableStateOf(request.kind) }
    val id = rememberSaveable { original?.id ?: newId() }
    var title by rememberSaveable { mutableStateOf(original?.title ?: source?.title.orEmpty()) }
    var body by rememberSaveable { mutableStateOf(original?.body ?: source?.body.orEmpty()) }
    var state by rememberSaveable { mutableStateOf(original?.state ?: "TODO") }
    var priority by rememberSaveable { mutableIntStateOf(original?.priority ?: 1) }
    var start by rememberSaveable { mutableStateOf(original?.start) }
    var due by rememberSaveable {
        mutableStateOf(
            original?.due
                ?: request.day?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        )
    }
    var zone by rememberSaveable { mutableStateOf(original?.zone ?: ZoneId.systemDefault().id) }
    var allDay by rememberSaveable { mutableStateOf(original?.allDay ?: true) }
    var remind by rememberSaveable { mutableStateOf(original?.remind ?: false) }
    var early by rememberSaveable { mutableStateOf((original?.reminderMinutes ?: 0).toString()) }
    var folder by rememberSaveable { mutableStateOf(original?.folderId.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(original?.categoryId.orEmpty()) }
    var parent by rememberSaveable {
        mutableStateOf(request.parent ?: original?.parentId.orEmpty())
    }
    var target by rememberSaveable {
        mutableStateOf((original?.target ?: 1.0).toString().removeSuffix(".0"))
    }
    var habitType by rememberSaveable { mutableStateOf(original?.habitType ?: "完成型") }
    var unit by rememberSaveable { mutableStateOf(original?.unit ?: "次") }
    var progress by rememberSaveable { mutableIntStateOf(original?.progress ?: 0) }
    var autoProgress by rememberSaveable { mutableStateOf(original?.autoProgress ?: true) }
    var tags by remember {
        mutableStateOf(original?.tags?.split(",")?.filter { it.isNotBlank() }?.toSet().orEmpty())
    }
    var color by rememberSaveable { mutableLongStateOf(original?.color ?: 0L) }
    var related by remember {
        mutableStateOf(
            original
                ?.let { item ->
                    data.links
                        .filter {
                            it.type == "related" && (it.fromId == item.id || it.toId == item.id)
                        }
                        .map { if (it.fromId == item.id) it.toId else it.fromId }
                        .toSet()
                }
                .orEmpty()
        )
    }
    val initialRule = remember {
        original
            ?.rule
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<RepeatRule>(it) }.getOrNull() }
            ?: RepeatRule()
    }
    var rule by remember { mutableStateOf(initialRule) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var editingSeries by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    // Generated occurrences must not monopolize the association picker.
    val available = (data.active.filter { it.kind != "TASK" || it.id in related || it.parentId == id || it.id == parent } +
        TaskViews.queue(data.items)).distinctBy { it.id }
    val task = kind == "TASK"
    val recurring = kind == "SERIES" || kind == "HABIT"
    val timed = kind in listOf("TASK", "EVENT", "PLAN")
    val hierarchical = kind in listOf("TASK", "PLAN", "FOLDER", "CATEGORY")
    val label = Kind.valueOf(kind).label
    val context = LocalContext.current
    val save = {
        val amount = if (kind == "HABIT") target.toDoubleOrNull() else 1.0
        when {
            title.isBlank() -> error = "请输入标题"
            start != null && due != null && start!! > due!! -> error = "结束时间不能早于开始时间"
            amount == null || amount <= 0 || !amount.isFinite() -> error = "请输入有效的习惯目标"
            else -> {
                val next =
                    (original ?: Item(id = id, kind = kind, title = title)).copy(
                        kind = kind,
                        title = title.trim(),
                        body = body,
                        state = state,
                        priority = priority,
                        start = start,
                        due = due,
                        allDay = allDay,
                        zone = zone,
                        remind = remind,
                        reminderMinutes = early.toIntOrNull()?.coerceIn(0, 10080) ?: 0,
                        parentId = parent.ifBlank { null },
                        folderId = folder.ifBlank { null },
                        categoryId = category.ifBlank { null },
                        tags = tags.joinToString(","),
                        color = color,
                        rule =
                            if (recurring)
                                json.encodeToString(RepeatRule.serializer(), rule.copy(zone = zone))
                            else "",
                        target = amount,
                        unit = unit.ifBlank { "次" },
                        habitType = habitType,
                        progress = progress,
                        autoProgress = autoProgress,
                        sourceId = source?.id ?: original?.sourceId,
                    )
                saving = true
                vm.run {
                    try {
                        if (original?.kind == "SERIES")
                            vm.repo.updateSeriesFuture(next, related.toList())
                        else vm.repo.save(next, related.toList())
                        withContext(Dispatchers.Main) { close() }
                        vm.notices.emit(Notice("已保存"))
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            saving = false
                            error = e.message ?: "保存失败"
                        }
                    }
                }
            }
        }
    }
    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            Modifier.widthIn(max = 680.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 22.dp)
                .imePadding(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.heightIn(max = 740.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(kindIcon(kind), null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (original == null) "创建$label" else "编辑${originalKind.label}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (original != null)
                        IconButton(onClick = { deleting = true }) {
                            Icon(ShikeIcons.DeleteOutline, "移入回收站")
                        }
                    IconButton(onClick = close) { Icon(ShikeIcons.Close, "关闭编辑") }
                }
                HorizontalDivider()
                Column(
                    Modifier.weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (original == null && request.kind == "TASK")
                        Choice(
                            "任务类型",
                            kind,
                            listOf("TASK" to "单次任务", "SERIES" to "重复任务"),
                            { kind = it },
                        )
                    OutlinedTextField(
                        title,
                        { title = it.take(500) },
                        label = { Text("标题") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (kind !in listOf("FOLDER", "TAG", "CATEGORY")) {
                        OutlinedTextField(
                            body,
                            { body = it },
                            label = { Text(if (kind == "NOTE") "正文 · Markdown" else "详细描述") },
                            minLines = if (kind == "NOTE") 6 else 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (kind == "NOTE")
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("标题" to "## ", "清单" to "- [ ] ", "引用" to "> ").forEach {
                                    (name, prefix) ->
                                    AssistChip(
                                        onClick = { body += "\n$prefix" },
                                        label = { Text(name) },
                                    )
                                }
                            }
                    }
                    if (task) {
                        Choice(
                            "状态",
                            state,
                            TaskState.entries.map { it.name to it.label },
                            { state = it },
                        )
                        Choice(
                            "优先级",
                            priority.toString(),
                            listOf("0" to "低", "1" to "普通", "3" to "高"),
                            { priority = it.toInt() },
                        )
                        if (original?.seriesId != null)
                            TextButton(onClick = { editingSeries = true }) {
                                Icon(ShikeIcons.Repeat, null)
                                Spacer(Modifier.width(8.dp))
                                Text("重复系列选项")
                            }
                    }
                    if (kind == "CAPTURE" && original != null) {
                        Text("整理为", style = MaterialTheme.typography.titleSmall)
                        listOf("TASK", "NOTE", "HABIT", "PLAN", "EVENT").chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { targetKind ->
                                    AssistChip(onClick = { convert(targetKind, original) }, label = { Text(Kind.valueOf(targetKind).label) })
                                }
                            }
                        }
                    }
                    if (timed) {
                        SettingSwitch("全天", checked = allDay, onChange = { allDay = it })
                        DateField("开始时间", start, zone, allDay, { start = it })
                        DateField(
                            if (kind == "EVENT") "结束时间" else "截止时间",
                            due,
                            zone,
                            allDay,
                            { due = it },
                        )
                    }
                    if (recurring) {
                        SettingSwitch(
                            if (kind == "HABIT") "暂停习惯" else "暂停重复",
                            checked = rule.paused,
                            onChange = { rule = rule.copy(paused = it) },
                        )
                        Choice(
                            "重复周期",
                            rule.unit,
                            listOf(
                                "DAY" to "每日",
                                "WEEK" to "每周",
                                "MONTH" to "每月",
                                "CUSTOM" to "自定义天数",
                            ),
                            { rule = rule.copy(unit = it, interval = if (it == "DAY") 1 else rule.interval) },
                        )
                        if (rule.unit in listOf("CUSTOM", "WEEK", "MONTH"))
                            OutlinedTextField(
                                if (rule.interval == 0) "" else rule.interval.toString(),
                                {
                                    rule =
                                        rule.copy(
                                            interval =
                                                it.filter(Char::isDigit).take(3).toIntOrNull() ?: 0
                                        )
                                },
                                label = { Text(when (rule.unit) { "WEEK" -> "每隔多少周"; "MONTH" -> "每隔多少月"; else -> "每隔多少天" }) },
                                keyboardOptions =
                                    KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        val anchor = LocalDate.parse(rule.anchor)
                        OutlinedButton(
                            onClick = {
                                DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            rule =
                                                rule.copy(
                                                    anchor = LocalDate.of(y, m + 1, d).toString()
                                                )
                                        },
                                        anchor.year,
                                        anchor.monthValue - 1,
                                        anchor.dayOfMonth,
                                    )
                                    .show()
                            }
                        ) {
                            Text("起始日 · ${rule.anchor}")
                        }
                        if (rule.unit == "WEEK" && kind == "SERIES") {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                (1..7).forEach { n ->
                                    val days = rule.weekdays.ifEmpty { listOf(anchor.dayOfWeek.value) }
                                    val selected = n in days
                                    FilterChip(
                                        selected,
                                        {
                                            rule =
                                                rule.copy(
                                                    weekdays =
                                                        if (selected && days.size > 1) days - n
                                                        else if (selected) days else days + n
                                                )
                                        },
                                        { Text("一二三四五六日"[n - 1].toString()) },
                                    )
                                }
                            }
                        }
                        if (kind == "SERIES") {
                            SettingSwitch("全天任务", "当天结束后才标记逾期", allDay, { allDay = it })
                            Choice("优先级", priority.toString(), listOf("0" to "低", "1" to "普通", "3" to "高"), { priority = it.toInt() })
                        }
                        if (kind == "SERIES" && !allDay) TimeField("截止时间", rule.time) { rule = rule.copy(time = it) }
                        else if (remind) TimeField("提醒时间", rule.time) { rule = rule.copy(time = it) }
                    }
                    if (kind == "HABIT") {
                        Choice(
                            "习惯类型",
                            habitType,
                            listOf("完成型", "时长型", "数量型", "自定义型").map { it to it },
                            {
                                habitType = it
                                if (it == "时长型") unit = "分钟" else if (it == "完成型") unit = "次"
                            },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                target,
                                { target = it },
                                label = { Text("目标值") },
                                keyboardOptions =
                                    KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                unit,
                                { unit = it },
                                label = { Text("单位") },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (kind == "PLAN") {
                        SettingSwitch(
                            "自动计算进度",
                            "按关联任务、习惯和子计划计算",
                            autoProgress,
                            { autoProgress = it },
                        )
                        if (!autoProgress) {
                            Text("进度 $progress%")
                            Slider(
                                progress.toFloat(),
                                { progress = it.toInt() },
                                valueRange = 0f..100f,
                            )
                        } else if (original != null) Text("当前进度 ${data.progress(original)}%")
                    }
                    if (kind in listOf("TASK", "SERIES", "HABIT", "EVENT")) {
                        SettingSwitch(
                            "提醒",
                            if (kind in listOf("TASK", "EVENT") && due == null) "请先设置截止或结束时间"
                            else null,
                            remind,
                            { remind = it },
                        )
                        if (remind && kind in listOf("TASK", "SERIES", "EVENT"))
                            OutlinedTextField(
                                early,
                                { early = it.filter(Char::isDigit).take(5) },
                                label = { Text("提前多少分钟") },
                                keyboardOptions =
                                    KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                    }
                    if (hierarchical) {
                        val choices = available.filter { it.kind == kind && it.id != id }
                        if (choices.isNotEmpty())
                            Choice(
                                "父${label}",
                                parent,
                                listOf("" to "无") + choices.map { it.id to it.title },
                                { parent = it },
                            )
                        if (original != null && kind in listOf("TASK", "PLAN")) {
                            val children = available.filter { it.parentId == id && it.kind == kind }
                            children.forEach { child ->
                                TextButton(onClick = { open(child) }) { Text("↳ " + child.title) }
                            }
                            TextButton(onClick = { addChild(kind, id) }) { Text("＋ 添加子$label") }
                        }
                    }
                    if (kind == "NOTE") {
                        Choice(
                            "文件夹",
                            folder,
                            listOf("" to "未分类") +
                                available.filter { it.kind == "FOLDER" }.map { it.id to it.title },
                            { folder = it },
                        )
                    }
                    TextButton(onClick = { advanced = !advanced }) {
                        Icon(
                            if (advanced) ShikeIcons.ExpandLess else ShikeIcons.ExpandMore,
                            null,
                        )
                        Text(if (advanced) "收起更多设置" else "更多设置")
                    }
                    if (advanced) {
                        if (timed || recurring) {
                            val zones = remember {
                                listOf(
                                        ZoneId.systemDefault().id,
                                        "Asia/Shanghai",
                                        "UTC",
                                        "Asia/Tokyo",
                                        "Europe/London",
                                        "America/New_York",
                                        "America/Los_Angeles",
                                    )
                                    .distinct()
                            }
                            Choice("时区", zone, zones.map { it to it }, { zone = it })
                        }
                        if (kind == "TAG") ColorChoices(color, { color = it })
                        if (kind !in listOf("TAG", "CATEGORY", "FOLDER", "CAPTURE")) {
                            Choice(
                                "分类",
                                category,
                                listOf("" to "无") +
                                    available
                                        .filter {
                                            it.kind == "CATEGORY" &&
                                                (it.body.isBlank() || it.body == kind)
                                        }
                                        .map { it.id to it.title },
                                { category = it },
                            )
                            val allTags = available.filter { it.kind == "TAG" }
                            if (allTags.isNotEmpty()) {
                                Text("标签")
                                Column {
                                    allTags.forEach { tag ->
                                        FilterChip(
                                            tag.id in tags,
                                            {
                                                tags =
                                                    if (tag.id in tags) tags - tag.id
                                                    else tags + tag.id
                                            },
                                            { Text(tag.title) },
                                        )
                                    }
                                }
                            }
                            Text("关联对象")
                            available
                                .filter {
                                    it.id != id &&
                                        it.kind in
                                            listOf(
                                                "NOTE",
                                                "TASK",
                                                "HABIT",
                                                "PLAN",
                                                "SERIES",
                                                "CAPTURE",
                                            )
                                }
                                .take(80)
                                .forEach { o ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            o.id in related,
                                            { if (it) related += o.id else related -= o.id },
                                        )
                                        Text(o.title, Modifier.weight(1f))
                                        if (o.id in related)
                                            IconButton(onClick = { open(o) }) {
                                                Icon(ShikeIcons.OpenInNew, "打开 ${o.title}")
                                            }
                                    }
                                }
                        }
                        if (kind == "CATEGORY")
                            Choice(
                                "适用对象",
                                body,
                                listOf("" to "所有对象") +
                                    Kind.entries
                                        .filter {
                                            it.name in
                                                listOf(
                                                    "TASK",
                                                    "HABIT",
                                                    "NOTE",
                                                    "PLAN",
                                                    "EVENT",
                                                    "SERIES",
                                                )
                                        }
                                        .map { it.name to it.label },
                                { body = it },
                            )
                    }
                    if (original != null) {
                        TextButton(onClick = { historyOpen = !historyOpen }) {
                            Icon(ShikeIcons.History, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (historyOpen) "收起历史" else "查看历史")
                        }
                        if (historyOpen) {
                            if (kind == "SERIES") {
                                val today = LocalDate.now(ZoneId.of(original.zone))
                                val instances = data.active.filter { it.seriesId == id }
                                val historyItems = instances.filter {
                                    it.occurrence <= today.toString() || it.updated != it.created || it.state != "TODO"
                                }.sortedByDescending { it.occurrence }
                                val next = instances.filter { TaskViews.isPending(it) && it.occurrence > today.toString() }.minByOrNull { it.occurrence }
                                Text("历史实例 · ${historyItems.size} 项", style = MaterialTheme.typography.titleSmall)
                                next?.let { e -> TextButton(onClick = { open(e) }) { Text("下一次 · ${e.occurrence}") } }
                                var displayed by remember(id) { mutableIntStateOf(30) }
                                historyItems.take(displayed).forEach { entry ->
                                    TextButton(onClick = { open(entry) }) {
                                        Text("${entry.occurrence} · ${TaskState.valueOf(entry.state).label}")
                                    }
                                }
                                if (displayed < historyItems.size) TextButton(onClick = { displayed += 30 }) { Text("显示更多历史") }
                            }
                            if (kind == "HABIT")
                                Text(
                                    "连续完成 ${data.streak(original)} 个周期",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            if (kind == "HABIT")
                                data.checkins
                                    .filter { it.habitId == id }
                                    .take(60)
                                    .forEach {
                                        Text(
                                            "${it.date} · ${CheckState.valueOf(it.state).label} · ${it.value}/${it.target} ${it.unit}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                            data.history
                                .filter { it.itemId == id }
                                .take(60)
                                .forEach { entry ->
                                    if (entry.action == "note_version") {
                                        val version =
                                            runCatching {
                                                    json.decodeFromString<Item>(entry.payload)
                                                }
                                                .getOrNull()
                                        if (version != null)
                                            TextButton(
                                                onClick = {
                                                    body = version.body
                                                    title = version.title
                                                }
                                            ) {
                                                Text("${formatDate(entry.created)} · 载入此版本")
                                            }
                                    } else
                                        Text(
                                            "${formatDate(entry.created)} · ${if(entry.action=="state")entry.payload.split(" → ").joinToString(" → "){v->runCatching{TaskState.valueOf(v).label}.getOrDefault(v)}else if(entry.action=="created")"创建"else entry.payload}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                }
                        }
                    }
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                }
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = close) { Text("取消") }
                    Button(onClick = save, enabled = !saving) { Text(if (saving) "保存中…" else "保存") }
                }
            }
        }
    }
    if (deleting && original != null)
        Confirm("移入回收站？", "可以从回收站恢复「${original.title}」。关联的其他数据会保留。", { deleting = false }) {
            vm.trash(original)
            close()
        }
    if (editingSeries && original != null)
        AlertDialog(
            onDismissRequest = { editingSeries = false },
            title = { Text("重复任务") },
            text = { Text("可以保留此实例的独立修改、跳过这一次，或编辑未来的重复规则。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        editingSeries = false
                        data.active.find { it.id == original.seriesId }?.let(open)
                    }
                ) {
                    Text("编辑系列")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        vm.run("已跳过本次") { vm.repo.transition(original, TaskState.CANCELLED) }
                        close()
                    }
                ) {
                    Text("跳过本次")
                }
            },
        )
}

@Composable
fun DateField(
    label: String,
    value: Long?,
    zone: String,
    allDay: Boolean,
    onChange: (Long?) -> Unit,
) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = {
                val tz = ZoneId.of(zone)
                val existing =
                    value?.let { Instant.ofEpochMilli(it).atZone(tz) } ?: ZonedDateTime.now(tz)
                DatePickerDialog(
                        context,
                        { _, y, m, d ->
                            val day = LocalDate.of(y, m + 1, d)
                            if (allDay) onChange(day.atStartOfDay(tz).toInstant().toEpochMilli())
                            else
                                TimePickerDialog(
                                        context,
                                        { _, h, min ->
                                            onChange(
                                                day.atTime(h, min)
                                                    .atZone(tz)
                                                    .toInstant()
                                                    .toEpochMilli()
                                            )
                                        },
                                        existing.hour,
                                        existing.minute,
                                        true,
                                    )
                                    .show()
                        },
                        existing.year,
                        existing.monthValue - 1,
                        existing.dayOfMonth,
                    )
                    .show()
            },
        ) {
            Icon(ShikeIcons.CalendarToday, null, Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("$label · ${formatDate(value,zone,allDay)}")
        }
        if (value != null)
            IconButton(onClick = { onChange(null) }) { Icon(ShikeIcons.Close, "清除$label") }
    }
}

@Composable
fun TimeField(label: String, time: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val at = LocalTime.parse(time)
    OutlinedButton(
        onClick = {
            TimePickerDialog(
                    context,
                    { _, h, m -> onChange(LocalTime.of(h, m).toString()) },
                    at.hour,
                    at.minute,
                    true,
                )
                .show()
        }
    ) {
        Text("$label · $time")
    }
}

@Composable
fun CheckinDialog(
    item: Item,
    data: AppData,
    close: () -> Unit,
    initialDate: LocalDate = LocalDate.now(ZoneId.of(item.zone)),
    save: (Double, CheckState, LocalDate) -> Unit,
) {
    var date by remember(item.id, initialDate) { mutableStateOf(initialDate) }
    val log = data.effective(item, date)
    var value by
        remember(date) { mutableStateOf((log?.value ?: item.target).toString().removeSuffix(".0")) }
    var status by remember(date) { mutableStateOf(log?.state ?: CheckState.COMPLETE.name) }
    var error by remember { mutableStateOf("") }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = close,
        title = { Text("打卡 · ${item.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("目标 ${item.target} ${item.unit}")
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(
                                context,
                                { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                                date.year,
                                date.monthValue - 1,
                                date.dayOfMonth,
                            )
                            .apply { datePicker.maxDate = System.currentTimeMillis() }
                            .show()
                    }
                ) {
                    Text(date.toString())
                }
                Choice(
                    "打卡状态",
                    status,
                    CheckState.entries.map { it.name to it.label },
                    { status = it },
                )
                if (
                    status in listOf("COMPLETE", "PARTIAL") && item.habitType != "完成型" ||
                        status == "PARTIAL"
                )
                    OutlinedTextField(
                        value,
                        { value = it },
                        label = { Text("实际完成量 · ${item.unit}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val actual =
                        when (status) {
                            "REST",
                            "MISSED" -> 0.0
                            "COMPLETE" ->
                                if (item.habitType == "完成型") item.target else value.toDoubleOrNull()
                            else -> value.toDoubleOrNull()
                        }
                    val rule = runCatching { json.decodeFromString<RepeatRule>(item.rule) }.getOrNull()
                    if (date > LocalDate.now(ZoneId.of(item.zone))) error = "不能提前打卡"
                    else if (rule == null || !Recurrence.due(rule.copy(paused = false), date)) error = "所选日期不在习惯周期内"
                    else if (actual == null || !actual.isFinite() || actual < 0) error = "请输入有效数值"
                    else if (status == "PARTIAL" && (actual <= 0 || actual >= item.target))
                        error = "部分完成量须大于 0 且低于目标"
                    else if (status == "COMPLETE" && actual < item.target)
                        error = "实际完成量低于目标，请选择部分完成"
                    else save(actual, CheckState.valueOf(status), date)
                }
            ) {
                Text("保存打卡")
            }
        },
        dismissButton = { TextButton(onClick = close) { Text("取消") } },
    )
}
