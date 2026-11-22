package dev.shike.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.shike.MainActivity
import dev.shike.data.*
import dev.shike.domain.*
import dev.shike.security.*
import java.io.File
import kotlinx.coroutines.*

@Composable
fun SpacesPage(data: AppData, vm: AppViewModel, current: String?, open: (String) -> Unit) {
    var editing by remember { mutableStateOf<Workspace?>(null) }
    var deleting by remember { mutableStateOf<Workspace?>(null) }
    var create by remember { mutableStateOf(false) }
    val spaces = data.workspaces
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 95.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            FilledTonalButton(onClick = { create = true }) {
                Icon(ShikeIcons.Add, null)
                Text("新建工作空间")
            }
        }
        itemsIndexed(spaces, key = { _, s -> s.id }) { index, space ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(spaceIcon(space.icon), null, tint = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = { open(space.id) }, modifier = Modifier.weight(1f)) {
                            Text(space.name + if (current == space.id) " · 当前" else "")
                        }
                        IconButton(onClick = { editing = space }) {
                            Icon(ShikeIcons.Edit, "编辑工作空间")
                        }
                        IconButton(
                            enabled = index > 0,
                            onClick = {
                                vm.layouts(
                                    spaces.toMutableList().apply {
                                        removeAt(index)
                                        add(index - 1, space)
                                    }
                                )
                            },
                        ) {
                            Icon(ShikeIcons.ArrowUpward, "上移工作空间")
                        }
                        IconButton(
                            enabled = index < spaces.lastIndex,
                            onClick = {
                                vm.layouts(
                                    spaces.toMutableList().apply {
                                        removeAt(index)
                                        add(index + 1, space)
                                    }
                                )
                            },
                        ) {
                            Icon(ShikeIcons.ArrowDownward, "下移工作空间")
                        }
                    }
                    Row {
                        TextButton(
                            onClick = {
                                vm.layouts(
                                    spaces + space.copy(id = newId(), name = space.name + " 副本")
                                )
                            }
                        ) {
                            Text("复制")
                        }
                        TextButton(enabled = spaces.size > 1, onClick = { deleting = space }) {
                            Text(
                                "删除",
                                color =
                                    if (spaces.size > 1) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    if (create || editing != null) {
        val original = editing
        var name by remember(original?.id) { mutableStateOf(original?.name.orEmpty()) }
        var icon by remember(original?.id) { mutableStateOf(original?.icon ?: "Home") }
        var template by remember { mutableStateOf("default") }
        val close = {
            create = false
            editing = null
        }
        AlertDialog(
            onDismissRequest = close,
            title = { Text(if (create) "新建工作空间" else "编辑工作空间") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        name,
                        { name = it.take(35) },
                        label = { Text("名称") },
                        singleLine = true,
                    )
                    Choice(
                        "图标",
                        icon,
                        listOf("Home" to "日常", "Work" to "工作", "Study" to "学习", "Star" to "收藏"),
                        { icon = it },
                    )
                    if (create)
                        Choice(
                            "布局模板",
                            template,
                            listOf("default" to "日常生活", "blank" to "自由创建"),
                            { template = it },
                        )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (original == null)
                            vm.layouts(
                                spaces +
                                    Workspace(
                                        name = name.trim(),
                                        icon = icon,
                                        widgets =
                                            if (template == "blank") emptyList()
                                            else defaultWidgets(),
                                    )
                            )
                        else
                            vm.layouts(
                                spaces.map {
                                    if (it.id == original.id)
                                        it.copy(name = name.trim(), icon = icon)
                                    else it
                                }
                            )
                        close()
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text("保存")
                }
            },
            dismissButton = { TextButton(onClick = close) { Text("取消") } },
        )
    }
    deleting?.let { space ->
        Confirm("删除工作空间？", "工作空间布局和快照会移除。原有任务、习惯、笔记等仍可访问。", { deleting = null }) {
            vm.layouts(spaces.filter { it.id != space.id })
            deleting = null
        }
    }
}

@Composable
fun SettingsPage(data: AppData, vm: AppViewModel, page: (String) -> Unit, activity: MainActivity) {
    val s = data.settings
    val change: (Appearance) -> Unit = { value -> vm.run { vm.repo.saveSettings(value) } }
    var section by rememberSaveable { mutableStateOf("appearance") }
    var passwordMode by rememberSaveable { mutableStateOf("") }
    var pendingFile by remember { mutableStateOf<Uri?>(null) }
    var exporting by remember { mutableStateOf<ByteArray?>(null) }
    var restoreConfirm by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    var localImport by remember { mutableStateOf(false) }
    val createFile =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            val bytes = exporting
            exporting = null
            if (uri != null && bytes != null)
                vm.run("加密备份已导出") {
                    activity.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("无法写入备份")
                }
        }
    val selectFile =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                pendingFile = uri
                localImport = false
                restoreConfirm = true
            }
        }
    val notify =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) vm.run("通知权限已启用") { vm.repo.initialize() }
        }
    val sections =
        listOf(
            "appearance" to "外观",
            "access" to "无障碍",
            "rules" to "规则",
            "notification" to "提醒",
            "privacy" to "数据保护",
        )
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                sections.forEach { (key, label) ->
                    FilterChip(section == key, { section = key }, { Text(label) })
                }
            }
        }
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    when (section) {
                        "appearance" -> {
                            Choice(
                                "主题",
                                s.theme,
                                listOf(
                                    "system" to "跟随系统",
                                    "light" to "浅色",
                                    "dark" to "深色",
                                    "schedule" to "日夜自动切换",
                                ),
                                { change(s.copy(theme = it)) },
                            )
                            Text("浅色强调色")
                            ColorChoices(s.accent) {
                                change(s.copy(accent = if (it == 0L) Appearance().accent else it))
                            }
                            Text("深色强调色")
                            ColorChoices(s.darkAccent) {
                                change(
                                    s.copy(
                                        darkAccent = if (it == 0L) Appearance().darkAccent else it
                                    )
                                )
                            }
                            CustomColorsSetting(s, change)
                            Choice(
                                "字体",
                                s.font,
                                listOf("system" to "系统字体", "serif" to "衬线", "mono" to "等宽"),
                                { change(s.copy(font = it)) },
                            )
                            Choice(
                                "信息密度",
                                s.density,
                                listOf("compact" to "紧凑", "comfortable" to "舒适", "relaxed" to "宽松"),
                                { change(s.copy(density = it)) },
                            )
                            Choice(
                                "性能",
                                s.performance,
                                listOf("light" to "轻量", "balanced" to "均衡", "rich" to "体验"),
                                { change(s.copy(performance = it)) },
                            )
                            TextButton(onClick = { reset = true }) { Text("恢复外观默认值") }
                        }
                        "access" -> {
                            Choice(
                                "应用字号（叠加系统设置）",
                                s.fontScale.toString(),
                                listOf("0.9" to "小", "1.0" to "跟随系统", "1.15" to "大", "1.3" to "特大"),
                                { change(s.copy(fontScale = it.toFloat())) },
                            )
                            Choice(
                                "行间距",
                                s.lineHeight.toString(),
                                listOf("1.2" to "紧凑", "1.5" to "标准", "1.8" to "宽松"),
                                { change(s.copy(lineHeight = it.toFloat())) },
                            )
                            Choice(
                                "字符间距",
                                s.letterSpacing.toString(),
                                listOf("0.0" to "标准", "0.3" to "加宽", "0.6" to "更宽"),
                                { change(s.copy(letterSpacing = it.toFloat())) },
                            )
                            SettingSwitch(
                                "提高对比度",
                                checked = s.contrast,
                                onChange = { change(s.copy(contrast = it)) },
                            )
                            SettingSwitch(
                                "增大触控区域",
                                checked = s.largeTargets,
                                onChange = { change(s.copy(largeTargets = it)) },
                            )
                            SettingSwitch(
                                "减少动态效果",
                                checked = !s.motion,
                                onChange = { change(s.copy(motion = !it)) },
                            )
                            SettingSwitch(
                                "触觉反馈",
                                checked = s.haptic,
                                onChange = { change(s.copy(haptic = it)) },
                            )
                        }
                        "rules" -> {
                            SettingSwitch(
                                "子任务全部完成时自动完成父任务",
                                checked = s.autoParent,
                                onChange = { change(s.copy(autoParent = it)) },
                            )
                            Choice(
                                "日历一周起始日",
                                s.weekStart.toString(),
                                (1..7).map { it.toString() to "星期" + "一二三四五六日"[it - 1] },
                                { change(s.copy(weekStart = it.toInt())) },
                            )
                            SettingSwitch(
                                "拼音与首字母搜索",
                                checked = s.searchPinyin,
                                onChange = { change(s.copy(searchPinyin = it)) },
                            )
                            SettingSwitch(
                                "拼写容错",
                                checked = s.searchFuzzy,
                                onChange = { change(s.copy(searchFuzzy = it)) },
                            )
                            var synonyms by remember(s.synonyms) { mutableStateOf(s.synonyms) }
                            OutlinedTextField(
                                synonyms,
                                { synonyms = it },
                                label = { Text("同义词，每组用逗号分隔") },
                                supportingText = { Text("如：学习=读书,采购=买东西") },
                            )
                            if (synonyms != s.synonyms)
                                TextButton(onClick = { change(s.copy(synonyms = synonyms)) }) {
                                    Text("保存同义词")
                                }
                            TextButton(onClick = { page("spaces") }) { Text("管理工作空间") }
                        }
                        "notification" -> {
                            SettingSwitch(
                                "本地通知",
                                checked = s.notifications,
                                onChange = { change(s.copy(notifications = it)) },
                            )
                            SettingSwitch(
                                "习惯提醒",
                                checked = s.habitReminders,
                                onChange = { change(s.copy(habitReminders = it)) },
                            )
                            SettingSwitch(
                                "每日回顾提醒",
                                "每天 20:00",
                                s.reviewReminders,
                                { change(s.copy(reviewReminders = it)) },
                            )
                            Choice(
                                "免打扰开始",
                                s.quietStart.toString(),
                                (0..23).map { it.toString() to "%02d:00".format(it) },
                                { change(s.copy(quietStart = it.toInt())) },
                            )
                            Choice(
                                "免打扰结束",
                                s.quietEnd.toString(),
                                (0..23).map { it.toString() to "%02d:00".format(it) },
                                { change(s.copy(quietEnd = it.toInt())) },
                            )
                            if (Build.VERSION.SDK_INT >= 33)
                                TextButton(
                                    onClick = {
                                        notify.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                ) {
                                    Text("申请通知权限")
                                }
                            val manager = activity.getSystemService(AlarmManager::class.java)
                            Text(
                                if (manager.canScheduleExactAlarms()) "精确提醒可用" else "目前使用普通提醒",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (!manager.canScheduleExactAlarms())
                                TextButton(
                                    onClick = {
                                        activity.startActivity(
                                            Intent(
                                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                                Uri.parse("package:" + activity.packageName),
                                            )
                                        )
                                    }
                                ) {
                                    Text("打开精确提醒设置")
                                }
                        }
                        else -> {
                            Text("数据库加密始终开启", style = MaterialTheme.typography.titleMedium)
                            NumberSetting("回收站保留天数（0 表示手动清理）", s.trashDays, 36500) {
                                change(s.copy(trashDays = it))
                            }
                            NumberSetting("笔记保留版本数（0 表示不保存新版本）", s.historyLimit, 1000) {
                                change(s.copy(historyLimit = it))
                            }
                            TextButton(onClick = { page("trash") }) { Text("查看回收站") }
                            HorizontalDivider()
                            Text("加密备份", style = MaterialTheme.typography.titleMedium)
                            Text("跨设备恢复需要备份密码。请单独保存密码。", style = MaterialTheme.typography.bodySmall)
                            Row {
                                TextButton(onClick = { passwordMode = "export" }) { Text("导出备份") }
                                TextButton(onClick = { selectFile.launch(arrayOf("*/*")) }) {
                                    Text("导入恢复")
                                }
                            }
                            SettingSwitch(
                                "允许系统备份",
                                "仅包含独立密码加密的备份文件",
                                s.backupEnabled,
                                { enabled ->
                                    if (enabled) passwordMode = "system"
                                    else
                                        vm.run {
                                            KeyVault(activity).clearBackupPassword()
                                            File(activity.filesDir, "portable/latest.shike")
                                                .delete()
                                            vm.repo.saveSettings(s.copy(backupEnabled = false))
                                        }
                                },
                            )
                            if (File(activity.filesDir, "portable/latest.shike").exists())
                                TextButton(
                                    onClick = {
                                        localImport = true
                                        restoreConfirm = true
                                    }
                                ) {
                                    Text("恢复系统备份文件")
                                }
                            HorizontalDivider()
                            SettingSwitch(
                                "应用锁",
                                "使用 4 至 12 位数字密码",
                                s.lockHash.isNotBlank(),
                                { if (it) passwordMode = "pin" else passwordMode = "disablePin" },
                            )
                            if (s.lockHash.isNotBlank())
                                SettingSwitch(
                                    "允许生物识别",
                                    checked = s.biometric,
                                    onChange = { change(s.copy(biometric = it)) },
                                )
                        }
                    }
                }
            }
        }
        item {
            Text(
                "拾刻 · ${dev.shike.BuildConfig.VERSION_NAME.substringBefore("-")}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (reset)
        Confirm("恢复默认外观？", "主题、字体和密度将恢复默认。", { reset = false }) {
            reset = false
            val base = Appearance()
            change(
                s.copy(
                    theme = base.theme,
                    accent = base.accent,
                    darkAccent = base.darkAccent,
                    background = 0,
                    surface = 0,
                    text = 0,
                    darkBackground = 0,
                    darkSurface = 0,
                    darkText = 0,
                    font = base.font,
                    density = base.density,
                    performance = base.performance,
                )
            )
        }
    if (restoreConfirm)
        Confirm(
            "用备份恢复数据？",
            "验证密码和完整性后，当前设备内的记录和工作空间会被备份内容替换。",
            {
                restoreConfirm = false
                pendingFile = null
            },
        ) {
            restoreConfirm = false
            passwordMode = "restore"
        }
    if (passwordMode.isNotBlank()) {
        val mode = passwordMode
        var password by remember(mode) { mutableStateOf("") }
        var confirmation by remember(mode) { mutableStateOf("") }
        var busy by remember(mode) { mutableStateOf(false) }
        var error by remember(mode) { mutableStateOf("") }
        val pin = mode in listOf("pin", "disablePin")
        val verify = mode in listOf("pin", "export", "system")
        val close = { if (!busy) passwordMode = "" }
        AlertDialog(
            onDismissRequest = close,
            title = {
                Text(
                    when (mode) {
                        "export" -> "设置备份密码"
                        "restore" -> "输入备份密码"
                        "system" -> "设置系统备份密码"
                        "pin" -> "设置应用锁"
                        else -> "关闭应用锁"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        password,
                        { password = if (pin) it.filter(Char::isDigit).take(12) else it.take(128) },
                        label = {
                            Text(
                                if (mode == "disablePin") "当前数字密码"
                                else if (pin) "4 至 12 位数字" else "至少 8 位"
                            )
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    if (pin) KeyboardType.NumberPassword else KeyboardType.Password
                            ),
                    )
                    if (verify)
                        OutlinedTextField(
                            confirmation,
                            { confirmation = it.take(128) },
                            label = { Text("再输入一次") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        if (
                            password.length < (if (pin) 4 else 8) ||
                                verify && password != confirmation
                        ) {
                            error = "请检查密码长度和两次输入"
                            return@Button
                        }
                        busy = true
                        vm.run {
                            val chars = password.toCharArray()
                            try {
                                when (mode) {
                                    "pin" -> {
                                        vm.repo.saveSettings(
                                            s.copy(lockHash = Crypto.passwordHash(password))
                                        )
                                        withContext(Dispatchers.Main) { activity.locked = false }
                                    }
                                    "disablePin" -> {
                                        require(Crypto.verify(password, s.lockHash)) { "密码不正确" }
                                        vm.repo.saveSettings(
                                            s.copy(lockHash = "", biometric = false)
                                        )
                                    }
                                    "system" -> vm.repo.configureSystemBackup(chars)
                                    "restore" -> {
                                        val input =
                                            if (localImport)
                                                File(activity.filesDir, "portable/latest.shike")
                                                    .inputStream()
                                            else
                                                pendingFile?.let {
                                                    activity.contentResolver.openInputStream(it)
                                                } ?: error("无法打开文件")
                                        val bytes = input.use { it.readBackupBytes() }
                                        vm.repo.import(bytes, chars)
                                    }
                                    "export" -> {
                                        val bytes = vm.repo.export(chars)
                                        withContext(Dispatchers.Main) {
                                            exporting = bytes
                                            createFile.launch(
                                                "拾刻-${java.time.LocalDate.now()}.shike"
                                            )
                                        }
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    busy = false
                                    passwordMode = ""
                                    pendingFile = null
                                }
                                if (mode != "export")
                                    vm.notices.emit(
                                        Notice(if (mode == "restore") "数据已恢复" else "设置已保存")
                                    )
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    busy = false
                                    error =
                                        if (e is javax.crypto.AEADBadTagException) "密码错误或备份损坏"
                                        else e.message ?: "操作失败"
                                }
                            } finally {
                                chars.fill('\u0000')
                            }
                        }
                    },
                ) {
                    Text(if (busy) "处理中…" else "确认")
                }
            },
            dismissButton = { TextButton(onClick = close, enabled = !busy) { Text("取消") } },
        )
    }
}

@Composable
private fun NumberSetting(label: String, original: Int, maximum: Int, save: (Int) -> Unit) {
    var value by remember(original) { mutableStateOf(original.toString()) }
    OutlinedTextField(
        value,
        { value = it.filter(Char::isDigit).take(5) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            if (value.toIntOrNull() in 0..maximum && value != original.toString())
                IconButton(onClick = { save(value.toInt()) }) { Icon(ShikeIcons.Check, "保存$label") }
        },
    )
}

@Composable
private fun CustomColorsSetting(s: Appearance, change: (Appearance) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val drafts = rememberSaveableStateHolder()
    val colors = MaterialTheme.colorScheme
    val customCount = listOf(s.background, s.surface, s.text, s.darkBackground, s.darkSurface, s.darkText)
        .count { it != 0L }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .semantics { stateDescription = if (expanded) "已展开" else "已折叠" }
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("自定义颜色", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (customCount == 0) "浅色与深色 · 使用默认配色" else "已自定义 $customCount / 6 项",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Icon(if (expanded) ShikeIcons.ExpandLess else ShikeIcons.ExpandMore, null, tint = colors.onSurfaceVariant)
            }
            if (expanded) {
                HorizontalDivider(color = colors.outlineVariant)
                drafts.SaveableStateProvider("custom_colors") {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("浅色模式", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                        HexColorField("浅色背景", s.background) { change(s.copy(background = it)) }
                        HexColorField("浅色卡片", s.surface) { change(s.copy(surface = it)) }
                        HexColorField("浅色文字", s.text) { change(s.copy(text = it)) }
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider(color = colors.outlineVariant)
                        Text("深色模式", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                        HexColorField("深色背景", s.darkBackground) { change(s.copy(darkBackground = it)) }
                        HexColorField("深色卡片", s.darkSurface) { change(s.copy(darkSurface = it)) }
                        HexColorField("深色文字", s.darkText) { change(s.copy(darkText = it)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HexColorField(label: String, original: Long, save: (Long) -> Unit) {
    val initial = if (original == 0L) "" else "%06X".format(original and 0xFFFFFF)
    var value by rememberSaveable(original) { mutableStateOf(initial) }
    OutlinedTextField(
        value,
        { value = it.filter { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' }.take(6) },
        label = { Text(label) },
        placeholder = { Text("留空使用默认值") },
        prefix = { Text("#") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            if (value != initial && (value.isEmpty() || value.length == 6))
                IconButton(
                    onClick = {
                        save(if (value.isBlank()) 0L else (0xFF000000 or value.toLong(16)))
                    }
                ) {
                    Icon(ShikeIcons.Check, "保存$label")
                }
        },
    )
}

private fun java.io.InputStream.readBackupBytes(): ByteArray {
    val limit = 52_428_800
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        require(output.size() + read <= limit) { "备份文件超过 50 MB" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
