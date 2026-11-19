package dev.shike.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.shike.data.*
import dev.shike.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val widgetTypes =
    listOf(
        "CAPTURE" to "快速记录",
        "TASK" to "任务",
        "HABIT" to "习惯",
        "EVENT" to "日程",
        "NOTE" to "笔记",
        "PLAN" to "计划",
        "calendar" to "日历",
        "statistics" to "统计",
        "review" to "回顾",
        "GROUP" to "组合",
    )
private val palette =
    listOf(0L, 0xFF527361, 0xFF48698A, 0xFF866780, 0xFF915C49, 0xFF88742F, 0xFF4F7376)

@Composable
fun ColorChoices(color: Long, select: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        palette.forEachIndexed { i, c ->
            FilterChip(
                selected = color == c,
                onClick = { select(c) },
                label = { Text(listOf("默认", "松绿", "雾蓝", "淡紫", "陶红", "麦黄", "湖青")[i]) },
                leadingIcon = {
                    if (c != 0L)
                        Icon(ShikeIcons.Circle, null, Modifier.size(13.dp), tint = Color(c))
                },
            )
        }
    }
}

@Composable
fun Dashboard(
    data: AppData,
    space: Workspace,
    editing: Boolean,
    setEditing: (Boolean) -> Unit,
    save: (Workspace) -> Unit,
    vm: AppViewModel,
    open: (Item) -> Unit,
    toggle: (Item) -> Unit,
    add: (String) -> Unit,
    page: (String) -> Unit,
) {
    var config by remember(space.id) { mutableStateOf<Widget?>(null) }
    var newWidget by remember { mutableStateOf(false) }
    var snapshots by remember { mutableStateOf(false) }
    val list = space.widgets
    fun replace(widget: Widget) {
        val next =
            if (list.any { it.id == widget.id }) list.map { if (it.id == widget.id) widget else it }
            else list + widget
        if (LayoutRules.validate(next)) save(space.copy(widgets = next))
        else vm.run("布局最多支持八层嵌套") {}
    }
    fun move(id: String, delta: Int) {
        val at = list.indexOfFirst { it.id == id }
        if (at < 0 || list[at].locked) return
        val siblings = list.indices.filter { list[it].parent == list[at].parent }
        val to = siblings.getOrNull(siblings.indexOf(at) + delta) ?: return
        val next =
            list.toMutableList().apply {
                val other = this[to]
                this[to] = this[at]
                this[at] = other
            }
        save(space.copy(widgets = next))
    }
    fun duplicate(widget: Widget) {
        val result = mutableListOf<Widget>()
        fun copy(source: Widget, parent: String?) {
            val next = source.copy(id = newId(), parent = parent)
            result += next
            list.filter { it.parent == source.id }.forEach { copy(it, next.id) }
        }
        copy(widget, widget.parent)
        val next = list + result
        if (LayoutRules.validate(next)) save(space.copy(widgets = next)) else vm.run("积木数量已达上限") {}
    }
    val scroll = remember(space.id) { ScrollState(0) }
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp)
            .padding(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    LocalDate.now()
                        .format(
                            DateTimeFormatter.ofPattern("M月d日  EEEE", Locale.SIMPLIFIED_CHINESE)
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (editing) "编辑布局" else "今天",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            FilledTonalIconButton(onClick = { setEditing(!editing) }) {
                Icon(
                    if (editing) ShikeIcons.Check else ShikeIcons.Tune,
                    if (editing) "完成布局编辑" else "编辑布局",
                )
            }
        }
        if (editing) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                FilledTonalButton(onClick = { newWidget = true }) {
                    Icon(ShikeIcons.Add, null)
                    Text("积木")
                }
                IconButton(onClick = vm::undo, enabled = vm.undoLayouts.isNotEmpty()) {
                    Icon(ShikeIcons.Undo, "撤销布局操作")
                }
                IconButton(onClick = vm::redo, enabled = vm.redoLayouts.isNotEmpty()) {
                    Icon(ShikeIcons.Redo, "重做布局操作")
                }
                TextButton(onClick = { snapshots = true }) { Text("布局快照") }
            }
            Text(
                "长按拖动积木，或用菜单调整位置与尺寸",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        WidgetGrid(
            null,
            list,
            data,
            editing,
            open,
            toggle,
            add,
            page,
            { config = it },
            ::move,
            ::replace,
            ::duplicate,
            { widget ->
                val next =
                    list
                        .filter { it.id != widget.id }
                        .map { if (it.parent == widget.id) it.copy(parent = widget.parent) else it }
                save(space.copy(widgets = next))
            },
        )
        if (list.isEmpty()) Button(onClick = { newWidget = true }) { Text("添加积木") }
    }
    if (newWidget)
        AlertDialog(
            onDismissRequest = { newWidget = false },
            title = { Text("添加积木") },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    widgetTypes.forEach { (type, label) ->
                        TextButton(
                            onClick = {
                                newWidget = false
                                replace(
                                    Widget(
                                        type = type,
                                        title = label,
                                        filter = if (type == "TASK") "today" else "all",
                                    )
                                )
                            }
                        ) {
                            Icon(kindIcon(type), null)
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { newWidget = false }) { Text("关闭") } },
        )
    config?.let { w ->
        WidgetConfig(
            w,
            list,
            { config = null },
            {
                replace(it)
                config = null
            },
        )
    }
    if (snapshots) SnapshotDialog(space, { snapshots = false }, save)
}

@Composable
private fun WidgetGrid(
    parent: String?,
    list: List<Widget>,
    data: AppData,
    editing: Boolean,
    open: (Item) -> Unit,
    toggle: (Item) -> Unit,
    add: (String) -> Unit,
    page: (String) -> Unit,
    config: (Widget) -> Unit,
    move: (String, Int) -> Unit,
    update: (Widget) -> Unit,
    copy: (Widget) -> Unit,
    delete: (Widget) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val twoColumns = maxWidth >= 345.dp && !data.settings.largeTargets
        val children = list.filter { it.parent == parent }
        val rows = mutableListOf<List<Widget>>()
        var i = 0
        while (i < children.size) {
            val current = children[i++]
            val next = children.getOrNull(i)
            if (twoColumns && current.span == 1 && next?.span == 1) {
                rows += listOf(current, next)
                i++
            } else rows += listOf(current)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { widget ->
                        key(widget.id) {
                            WidgetCard(
                                widget,
                                data,
                                editing,
                                Modifier.weight(1f),
                                open,
                                toggle,
                                add,
                                page,
                                config,
                                move,
                                update,
                                copy,
                                delete,
                            ) {
                                if (widget.type == "GROUP")
                                    WidgetGrid(
                                        widget.id,
                                        list,
                                        data,
                                        editing,
                                        open,
                                        toggle,
                                        add,
                                        page,
                                        config,
                                        move,
                                        update,
                                        copy,
                                        delete,
                                    )
                            }
                        }
                    }
                    if (twoColumns && row.size == 1 && row.first().span == 1)
                        Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WidgetCard(
    w: Widget,
    data: AppData,
    editing: Boolean,
    modifier: Modifier,
    open: (Item) -> Unit,
    toggle: (Item) -> Unit,
    add: (String) -> Unit,
    page: (String) -> Unit,
    config: (Widget) -> Unit,
    move: (String, Int) -> Unit,
    update: (Widget) -> Unit,
    copy: (Widget) -> Unit,
    delete: (Widget) -> Unit,
    children: @Composable () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    val currentMove by rememberUpdatedState(move)
    val a = MaterialTheme.colorScheme
    val tint = if (w.color == 0L) a.primary else Color(w.color)
    val density = if (w.density == "global") data.settings.density else w.density
    val padding =
        when (density) {
            "compact" -> 12.dp
            "relaxed" -> 20.dp
            else -> 16.dp
        }
    val dragModifier =
        if (editing && !w.locked)
            Modifier.pointerInput(w.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        if (kotlin.math.abs(drag) > 30f) currentMove(w.id, if (drag > 0) 1 else -1)
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                ) { change, amount ->
                    change.consume()
                    drag +=
                        if (kotlin.math.abs(amount.y) >= kotlin.math.abs(amount.x)) amount.y
                        else amount.x
                }
            }
        else Modifier
    Surface(
        modifier.then(dragModifier),
        shape = RoundedCornerShape(12.dp),
        color = a.surface,
        border = BorderStroke(1.dp, if (editing) tint.copy(alpha = .5f) else a.outlineVariant),
    ) {
        Column(Modifier.padding(padding)) {
            Row(
                Modifier.fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (w.collapsed) update(w.copy(collapsed = false))
                            else if (w.type in listOf("calendar", "statistics", "review"))
                                page(w.type)
                        },
                        onLongClick = { menu = true },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (w.icon == "Star") ShikeIcons.StarOutline
                    else if (w.icon == "Home") ShikeIcons.Home else kindIcon(w.type),
                    null,
                    Modifier.size(19.dp),
                    tint = tint,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    w.title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (editing || w.type != "CAPTURE")
                    Box {
                        IconButton(
                            onClick = { menu = true },
                            modifier =
                                Modifier.size(if (data.settings.largeTargets) 48.dp else 38.dp),
                        ) {
                            Icon(
                                if (w.locked) ShikeIcons.Lock else ShikeIcons.MoreHoriz,
                                "积木菜单",
                            )
                        }
                        DropdownMenu(menu, { menu = false }) {
                            if (!w.locked) {
                                DropdownMenuItem(
                                    text = { Text("配置积木") },
                                    onClick = {
                                        menu = false
                                        config(w)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("向前移动") },
                                    onClick = {
                                        menu = false
                                        move(w.id, -1)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("向后移动") },
                                    onClick = {
                                        menu = false
                                        move(w.id, 1)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (w.span == 1) "宽布局" else "窄布局") },
                                    onClick = {
                                        menu = false
                                        update(w.copy(span = 3 - w.span))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("复制积木") },
                                    onClick = {
                                        menu = false
                                        copy(w)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (w.collapsed) "展开" else "折叠") },
                                    onClick = {
                                        menu = false
                                        update(w.copy(collapsed = !w.collapsed))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("移除积木") },
                                    onClick = {
                                        menu = false
                                        delete(w)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (w.locked) "解锁" else "锁定") },
                                onClick = {
                                    menu = false
                                    update(w.copy(locked = !w.locked))
                                },
                            )
                        }
                    }
            }
            if (!w.collapsed) {
                Spacer(Modifier.height(10.dp))
                when (w.type) {
                    "CAPTURE" ->
                        FilledTonalButton(
                            onClick = { add("CAPTURE") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(ShikeIcons.Add, null)
                            Spacer(Modifier.width(5.dp))
                            Text("新建快速记录")
                        }
                    "GROUP" -> children()
                    "calendar",
                    "statistics",
                    "review" ->
                        TextButton(onClick = { page(w.type) }) {
                            Text("打开" + pageTitle(w.type))
                            Icon(ShikeIcons.ChevronRight, null)
                        }
                    else -> {
                        val today = LocalDate.now()
                        val records =
                            data.active
                                .filter { item ->
                                    item.kind == w.type &&
                                        when {
                                            w.filter.startsWith("tag:") ->
                                                w.filter.substringAfter(":") in item.tags.split(",")
                                            w.filter.startsWith("category:") ->
                                                item.categoryId == w.filter.substringAfter(":")
                                            w.filter == "today" ->
                                                item.due == null ||
                                                    localDate(item.due, item.zone) == today ||
                                                    w.type == "TASK" &&
                                                        item.state in listOf("TODO", "DOING") &&
                                                        localDate(item.due, item.zone)
                                                            ?.isBefore(today) == true
                                            w.filter == "open" ->
                                                item.state in listOf("TODO", "DOING", "PAUSED")
                                            else -> true
                                        } &&
                                        when (w.type) {
                                            "EVENT" ->
                                                localDate(item.due ?: item.start, item.zone)?.let {
                                                    it >= today
                                                } ?: false
                                            "HABIT" ->
                                                runCatching {
                                                        Recurrence.due(
                                                            json.decodeFromString<RepeatRule>(
                                                                item.rule
                                                            ),
                                                            today,
                                                        )
                                                    }
                                                    .getOrDefault(true)
                                            else -> true
                                        }
                                }
                                .let {
                                    when (w.sort) {
                                        "name" -> it.sortedBy { v -> v.title }
                                        "priority" -> it.sortedByDescending { v -> v.priority }
                                        "newest" -> it.sortedByDescending { v -> v.created }
                                        else -> it.sortedBy { v -> v.due ?: Long.MAX_VALUE }
                                    }
                                }
                        if (records.isEmpty()) EmptyState(w.type) { add(w.type) }
                        else
                            records.take(w.limit).forEach { item ->
                                ItemRow(item, data, open, toggle, w.showDetails)
                                HorizontalDivider()
                            }
                        if (records.isNotEmpty())
                            TextButton(onClick = { page(w.type) }) { Text("查看全部") }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetConfig(
    widget: Widget,
    all: List<Widget>,
    close: () -> Unit,
    save: (Widget) -> Unit,
) {
    var value by remember { mutableStateOf(widget) }
    val parents =
        all.filter { it.type == "GROUP" && it.id != widget.id }
            .filter { candidate ->
                LayoutRules.validate(
                    all.map { if (it.id == widget.id) it.copy(parent = candidate.id) else it }
                )
            }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("配置积木") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value.title,
                    { value = value.copy(title = it.take(60)) },
                    label = { Text("名称") },
                )
                Choice(
                    "数据来源",
                    value.type,
                    if (value.type == "GROUP") widgetTypes.filter { it.first == "GROUP" }
                    else widgetTypes.filter { it.first != "GROUP" },
                    { value = value.copy(type = it) },
                )
                Choice(
                    "图标",
                    value.icon,
                    listOf("" to "自动", "Star" to "星标", "Home" to "首页"),
                    { value = value.copy(icon = it) },
                )
                Choice(
                    "大小",
                    value.span.toString(),
                    listOf("1" to "窄", "2" to "宽"),
                    { value = value.copy(span = it.toInt()) },
                )
                Choice(
                    "归入组合",
                    value.parent.orEmpty(),
                    listOf("" to "顶层") + parents.map { it.id to it.title },
                    { value = value.copy(parent = it.ifBlank { null }) },
                )
                Choice(
                    "筛选",
                    value.filter,
                    listOf("all" to "全部", "today" to "今天", "open" to "待处理"),
                    { value = value.copy(filter = it) },
                )
                Choice(
                    "排序",
                    value.sort,
                    listOf("due" to "时间", "name" to "名称", "priority" to "优先级", "newest" to "最近创建"),
                    { value = value.copy(sort = it) },
                )
                Choice(
                    "信息密度",
                    value.density,
                    listOf(
                        "global" to "跟随全局",
                        "compact" to "紧凑",
                        "comfortable" to "舒适",
                        "relaxed" to "宽松",
                    ),
                    { value = value.copy(density = it) },
                )
                OutlinedTextField(
                    value.limit.toString(),
                    { value = value.copy(limit = it.toIntOrNull()?.coerceIn(1, 100) ?: 1) },
                    label = { Text("最多显示条数") },
                )
                SettingSwitch(
                    "显示详细信息",
                    checked = value.showDetails,
                    onChange = { value = value.copy(showDetails = it) },
                )
                ColorChoices(value.color) { value = value.copy(color = it) }
            }
        },
        confirmButton = {
            Button(onClick = { save(value) }, enabled = value.title.isNotBlank()) { Text("保存配置") }
        },
        dismissButton = { TextButton(onClick = close) { Text("取消") } },
    )
}

@Composable
private fun SnapshotDialog(space: Workspace, close: () -> Unit, save: (Workspace) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("布局快照") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it.take(60) }, label = { Text("新快照名称") })
                space.snapshots.forEach { s ->
                    Column(Modifier.padding(vertical = 7.dp)) {
                        Text(s.name, fontWeight = FontWeight.Medium)
                        Row {
                            TextButton(
                                onClick = {
                                    save(space.copy(widgets = s.widgets))
                                    close()
                                }
                            ) {
                                Text("恢复")
                            }
                            TextButton(
                                onClick = {
                                    save(
                                        space.copy(
                                            snapshots =
                                                space.snapshots +
                                                    s.copy(id = newId(), name = s.name + " 副本")
                                        )
                                    )
                                    close()
                                }
                            ) {
                                Text("复制")
                            }
                            TextButton(
                                onClick = {
                                    save(
                                        space.copy(
                                            snapshots = space.snapshots.filter { it.id != s.id }
                                        )
                                    )
                                    close()
                                }
                            ) {
                                Text("删除")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    save(
                        space.copy(
                            snapshots =
                                space.snapshots +
                                    LayoutSnapshot(name = name.trim(), widgets = space.widgets)
                        )
                    )
                    close()
                },
                enabled = name.isNotBlank(),
            ) {
                Text("保存快照")
            }
        },
        dismissButton = { TextButton(onClick = close) { Text("关闭") } },
    )
}
