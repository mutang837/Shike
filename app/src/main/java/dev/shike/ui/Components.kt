package dev.shike.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.shike.data.*
import dev.shike.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

fun kindIcon(kind: String): ImageVector =
    when (kind) {
        "TASK" -> ShikeIcons.CheckCircle
        "SERIES" -> ShikeIcons.Repeat
        "HABIT" -> ShikeIcons.Spa
        "NOTE" -> ShikeIcons.Description
        "PLAN" -> ShikeIcons.Flag
        "EVENT" -> ShikeIcons.CalendarToday
        "FOLDER" -> ShikeIcons.Folder
        "TAG" -> ShikeIcons.Tag
        "CATEGORY" -> ShikeIcons.Category
        "REVIEW" -> ShikeIcons.AutoStories
        "GROUP" -> ShikeIcons.Widgets
        else -> ShikeIcons.EditNote
    }

fun formatDate(
    time: Long?,
    zone: String = ZoneId.systemDefault().id,
    allDay: Boolean = false,
): String =
    time?.let {
        Instant.ofEpochMilli(it)
            .atZone(ZoneId.of(zone))
            .format(DateTimeFormatter.ofPattern(if (allDay) "M月d日" else "M月d日 HH:mm"))
    } ?: "未设置时间"

fun localDate(time: Long?, zone: String = ZoneId.systemDefault().id): LocalDate? =
    time?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of(zone)).toLocalDate() }

@Composable
fun SectionTitle(
    title: String,
    subtitle: String? = null,
    action: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        action()
    }
}

@Composable
fun EmptyState(kind: String, onAdd: (() -> Unit)? = null) {
    val label = runCatching { Kind.valueOf(kind).label }.getOrDefault("内容")
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(kindIcon(kind), null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            when (kind) {
                "TASK" -> "给今天留一点方向"
                "HABIT" -> "小小的坚持，从这里开始"
                "NOTE" -> "让想法有处可放"
                "EVENT" -> "近期暂无日程"
                "CAPTURE" -> "有想法，就先记下来"
                else -> "这里还没有$label"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (onAdd != null) TextButton(onClick = onAdd) { Text("创建$label") }
    }
}

@Composable
fun Choice(
    label: String,
    value: String,
    choices: List<Pair<String, String>>,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    choices.find { it.first == value }?.second ?: value,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(ShikeIcons.ExpandMore, null)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (key, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        onChange(key)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun SettingSwitch(
    title: String,
    description: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked, onCheckedChange = onChange)
    }
}

@Composable
fun ItemRow(
    item: Item,
    data: AppData,
    onOpen: (Item) -> Unit,
    onToggle: (Item) -> Unit,
    showDetails: Boolean = true,
) {
    val done = item.state == "DONE"
    val isTask = item.kind == "TASK"
    val habit = item.kind == "HABIT"
    val log = if (habit) data.effective(item) else null
    Row(
        Modifier.fillMaxWidth()
            .clickable { onOpen(item) }
            .padding(vertical = if (data.settings.density == "compact") 4.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isTask) Checkbox(done, onCheckedChange = { onToggle(item) })
        else if (habit)
            IconButton(onClick = { onToggle(item) }) {
                Icon(
                    if (log?.state == "COMPLETE") ShikeIcons.CheckCircle
                    else ShikeIcons.RadioButtonUnchecked,
                    if (log?.state == "COMPLETE") "已打卡，修改" else "打卡",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        else
            Box(
                Modifier.padding(end = 12.dp)
                    .size(38.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainer,
                        RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    kindIcon(item.kind),
                    null,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color =
                    if (done) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                textDecoration =
                    if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
            )
            if (showDetails) {
                val label =
                    when {
                        habit ->
                            if (log != null)
                                "${CheckState.valueOf(log.state).label} · ${log.value}/${log.target} ${log.unit}"
                            else "目标 ${item.target} ${item.unit}"
                        item.kind == "PLAN" ->
                            "${data.progress(item)}% · ${formatDate(item.due,allDay=true)}"
                        item.kind == "NOTE" || item.kind == "CAPTURE" ->
                            item.body.take(65).replace('\n', ' ')
                        else ->
                            listOfNotNull(
                                    item.due?.let { formatDate(it, item.zone, item.allDay) },
                                    if (item.state != "TODO")
                                        runCatching { TaskState.valueOf(item.state).label }
                                            .getOrNull()
                                    else null,
                                    if (item.seriesId != null) "重复" else null,
                                )
                                .joinToString(" · ")
                    }
                if (label.isNotEmpty())
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (
                                item.due != null &&
                                    item.due < System.currentTimeMillis() &&
                                    isTask &&
                                    !done &&
                                    TaskState.valueOf(item.state).active
                            )
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
            }
        }
        if (item.priority == 3)
            Text(
                "高",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(6.dp),
            )
        if (item.kind == "PLAN")
            CircularProgressIndicator(
                progress = { data.progress(item) / 100f },
                Modifier.padding(8.dp).size(28.dp),
                strokeWidth = 3.dp,
            )
        if (habit && log?.state == "PARTIAL")
            Text(
                "${(log.value/log.target*100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
    }
}

@Composable
fun Confirm(title: String, body: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("确认", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
