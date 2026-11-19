package dev.shike.ui

import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.shike.MainActivity
import dev.shike.data.*
import dev.shike.domain.*
import dev.shike.security.Crypto
import java.time.LocalDate
import kotlinx.coroutines.*

data class EditRequest(
    val kind: String,
    val item: Item? = null,
    val parent: String? = null,
    val source: Item? = null,
    val day: LocalDate? = null,
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShikeApp(repo: Repository, activity: MainActivity, initialItem: String?) {
    val vm: AppViewModel = viewModel { AppViewModel(repo) }
    val data by vm.data.collectAsStateWithLifecycle()
    val settings = data.settings
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var page by rememberSaveable { mutableStateOf("home") }
    var spaceId by rememberSaveable { mutableStateOf("") }
    var preferredTab by rememberSaveable { mutableIntStateOf(1) }
    val spaceTabs = remember { mutableStateMapOf<String, Int>() }
    var switcher by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf(false) }
    var quick by remember { mutableStateOf(false) }
    var newMenu by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<EditRequest?>(null) }
    var check by remember { mutableStateOf<Item?>(null) }
    var savedCapture by remember { mutableStateOf<Item?>(null) }
    var editingLayout by rememberSaveable { mutableStateOf(false) }
    var bootFailure by remember { mutableStateOf("") }
    val workspace = data.workspaces.find { it.id == spaceId } ?: data.workspaces.firstOrNull()
    val pages = listOf("记录", "今日", "综合")
    val pager = rememberPagerState(initialPage = preferredTab.coerceIn(0, 2)) { 3 }
    LaunchedEffect(pager.currentPage, workspace?.id) {
        preferredTab = pager.currentPage
        workspace?.id?.let { spaceTabs[it] = pager.currentPage }
    }
    LaunchedEffect(vm) {
        vm.notices.collect { notice ->
            if (!data.ready) bootFailure = notice.message
            launch {
                if (
                    snackbar.showSnackbar(
                        notice.message,
                        notice.undo?.let { "撤销" },
                        duration = SnackbarDuration.Short,
                    ) == SnackbarResult.ActionPerformed
                ) {
                    notice.undo?.let { item -> vm.run("已恢复") { repo.restore(item) } }
                }
            }
        }
    }
    LaunchedEffect(data.ready, initialItem) {
        if (data.ready && initialItem != null) {
            if (initialItem.startsWith("review")) page = "review"
            else data.active.find { it.id == initialItem }?.let { edit = EditRequest(it.kind, it) }
        }
    }
    BackHandler(page != "home" || editingLayout) {
        if (editingLayout) editingLayout = false else page = "home"
    }
    ShikeTheme(settings) {
        val haptic = LocalHapticFeedback.current
        val open: (Item) -> Unit = { edit = EditRequest(it.kind, it) }
        val toggle: (Item) -> Unit = { item ->
            when (item.kind) {
                "HABIT" -> check = item
                "TASK" -> {
                    if (settings.haptic) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.run {
                        repo.transition(
                            item,
                            if (item.state == "DONE") TaskState.TODO else TaskState.DONE,
                        )
                    }
                }
                else -> open(item)
            }
        }
        val add: (String) -> Unit = {
            if (it == "CAPTURE") quick = true else edit = EditRequest(it)
        }
        if (!data.ready) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                if (bootFailure.isBlank()) CircularProgressIndicator()
                else Text("无法打开本地数据\n$bootFailure", Modifier.padding(24.dp))
            }
        } else if (activity.locked && settings.lockHash.isNotEmpty()) {
            LockScreen(activity, settings) { activity.locked = false }
        } else {
            BoxWithConstraints(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            ) {
                val wide = maxWidth >= 760.dp
                Row(Modifier.fillMaxSize()) {
                    if (wide)
                        SideNav(
                            data,
                            workspace,
                            page,
                            {
                                page = it
                                editingLayout = false
                            },
                            { id ->
                                spaceId = id
                                page = "home"
                                editingLayout = false
                                scope.launch { pager.scrollToPage(spaceTabs[id] ?: 1) }
                            },
                        )
                    Scaffold(
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.background,
                        snackbarHost = { SnackbarHost(snackbar) },
                        topBar = {
                            TopAppBar(
                                title = {
                                    if (page == "home")
                                        Box {
                                            TextButton(
                                                onClick = { switcher = true },
                                                contentPadding = PaddingValues(0.dp),
                                            ) {
                                                Icon(
                                                    ShikeIcons.Inventory2,
                                                    null,
                                                    Modifier.size(22.dp),
                                                )
                                                Spacer(Modifier.width(9.dp))
                                                Text(
                                                    workspace?.name ?: "拾刻",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                Icon(
                                                    ShikeIcons.ExpandMore,
                                                    "切换工作空间",
                                                    Modifier.size(18.dp),
                                                )
                                            }
                                            DropdownMenu(switcher, { switcher = false }) {
                                                data.workspaces.forEach { w ->
                                                    DropdownMenuItem(
                                                        text = { Text(w.name) },
                                                        leadingIcon = {
                                                            Icon(spaceIcon(w.icon), null)
                                                        },
                                                        trailingIcon = {
                                                            if (w.id == workspace?.id)
                                                                Icon(ShikeIcons.Check, null)
                                                        },
                                                        onClick = {
                                                            spaceId = w.id
                                                            switcher = false
                                                            editingLayout = false
                                                            scope.launch {
                                                                pager.scrollToPage(
                                                                    spaceTabs[w.id] ?: 1
                                                                )
                                                            }
                                                        },
                                                    )
                                                }
                                                HorizontalDivider()
                                                DropdownMenuItem(
                                                    text = { Text("管理工作空间") },
                                                    leadingIcon = {
                                                        Icon(ShikeIcons.Widgets, null)
                                                    },
                                                    onClick = {
                                                        switcher = false
                                                        page = "spaces"
                                                    },
                                                )
                                            }
                                        }
                                    else Text(pageTitle(page), fontWeight = FontWeight.SemiBold)
                                },
                                navigationIcon = {
                                    if (page != "home")
                                        IconButton(onClick = { page = "home" }) {
                                            Icon(ShikeIcons.ArrowBack, "返回工作空间")
                                        }
                                },
                                actions = {
                                    IconButton(onClick = { search = true }) {
                                        Icon(ShikeIcons.Search, "全局搜索")
                                    }
                                    if (page == "home")
                                        IconButton(onClick = { page = "settings" }) {
                                            Icon(ShikeIcons.Settings, "设置")
                                        }
                                },
                                colors =
                                    TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.background
                                    ),
                            )
                        },
                        floatingActionButton = {
                            if (page != "settings" && page != "trash") {
                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        shadowElevation = 3.dp,
                                        modifier =
                                            Modifier.size(
                                                    if (settings.largeTargets) 68.dp else 56.dp
                                                )
                                                .combinedClickable(
                                                    onClickLabel = "快速记录",
                                                    onLongClickLabel = "快捷创建菜单",
                                                    onClick = { quick = true },
                                                    onLongClick = { newMenu = true },
                                                ),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(ShikeIcons.Add, "快速记录")
                                        }
                                    }
                                    DropdownMenu(newMenu, { newMenu = false }) {
                                        listOf("TASK", "NOTE", "HABIT", "PLAN", "EVENT").forEach { k
                                            ->
                                            DropdownMenuItem(
                                                text = { Text("创建" + Kind.valueOf(k).label) },
                                                leadingIcon = { Icon(kindIcon(k), null) },
                                                onClick = {
                                                    newMenu = false
                                                    add(k)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    ) { pads ->
                        Column(Modifier.fillMaxSize().padding(pads)) {
                            when (page) {
                                "home" -> {
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 8.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surfaceContainer,
                                                RoundedCornerShape(12.dp),
                                            )
                                            .padding(4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        pages.forEachIndexed { i, name ->
                                            val selected = pager.currentPage == i
                                            Surface(
                                                onClick = {
                                                    scope.launch {
                                                        if (
                                                            settings.motion &&
                                                                settings.performance != "light"
                                                        )
                                                            pager.animateScrollToPage(i)
                                                        else pager.scrollToPage(i)
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                selected = selected,
                                                shape = RoundedCornerShape(8.dp),
                                                color =
                                                    if (selected) MaterialTheme.colorScheme.surface
                                                    else MaterialTheme.colorScheme.surfaceContainer,
                                                contentColor =
                                                    if (selected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                            ) {
                                                Box(
                                                    Modifier.padding(vertical = 10.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Text(
                                                        name,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight =
                                                            if (selected) FontWeight.Bold
                                                            else FontWeight.Normal,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    HorizontalPager(
                                        pager,
                                        modifier = Modifier.fillMaxSize(),
                                        userScrollEnabled = !editingLayout,
                                    ) { tab ->
                                        when (tab) {
                                            0 -> RecordPage(data, open, { quick = true }, add)
                                            1 ->
                                                if (workspace != null)
                                                    Dashboard(
                                                        data,
                                                        workspace,
                                                        editingLayout,
                                                        { editingLayout = it },
                                                        { updated ->
                                                            vm.layouts(
                                                                data.workspaces.map {
                                                                    if (it.id == updated.id) updated
                                                                    else it
                                                                }
                                                            )
                                                        },
                                                        vm,
                                                        open,
                                                        toggle,
                                                        add,
                                                        { page = it },
                                                    )
                                                else EmptyState("CAPTURE")
                                            else -> Overview(data, { page = it })
                                        }
                                    }
                                }
                                "settings" -> SettingsPage(data, vm, { page = it }, activity)
                                "spaces" ->
                                    SpacesPage(data, vm, workspace?.id) {
                                        spaceId = it
                                        page = "home"
                                    }
                                "calendar" ->
                                    CalendarPage(data, open, toggle) { day ->
                                        edit = EditRequest("EVENT", day = day)
                                    }
                                "statistics",
                                "review" -> InsightsPage(page == "review", data, vm, open)
                                "trash" -> TrashPage(data, vm)
                                else -> CollectionPage(page, data, open, toggle, add)
                            }
                        }
                    }
                }
            }
        }
        if (search)
            SearchDialog(
                data,
                vm,
                { search = false },
                {
                    search = false
                    open(it)
                },
            )
        if (quick)
            QuickCapture({ quick = false }) { body ->
                val item =
                    Item(
                        kind = "CAPTURE",
                        title =
                            body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(100)
                                ?: "快速记录",
                        body = body,
                    )
                vm.run("已记录") {
                    repo.save(item)
                    withContext(Dispatchers.Main) {
                        quick = false
                        savedCapture = item
                    }
                }
            }
        savedCapture?.let { capture ->
            AlertDialog(
                onDismissRequest = { savedCapture = null },
                title = { Text("已存入记录") },
                text = {
                    Column {
                        Text("原始内容已经保存。可以继续整理为：")
                        Row {
                            listOf("TASK", "NOTE", "EVENT").forEach { k ->
                                TextButton(
                                    onClick = {
                                        savedCapture = null
                                        edit = EditRequest(k, source = capture)
                                    }
                                ) {
                                    Text(Kind.valueOf(k).label)
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { savedCapture = null }) { Text("稍后整理") } },
            )
        }
        edit?.let { request ->
            key(request.item?.id, request.kind) {
                ItemEditor(
                    request,
                    data,
                    vm,
                    { edit = null },
                    { item -> edit = EditRequest(item.kind, item) },
                    { kind, parent -> edit = EditRequest(kind, parent = parent) },
                )
            }
        }
        check?.let { item ->
            CheckinDialog(item, data, { check = null }) { value, state, date ->
                vm.run("打卡已保存") {
                    repo.checkin(item, value, state, date)
                    withContext(Dispatchers.Main) { check = null }
                }
            }
        }
    }
}

fun pageTitle(page: String) =
    when (page) {
        "home" -> "日常生活"
        "settings" -> "设置"
        "spaces" -> "工作空间"
        "calendar" -> "日历"
        "statistics" -> "统计"
        "review" -> "回顾"
        "trash" -> "回收站"
        else -> runCatching { Kind.valueOf(page).label }.getOrDefault(page)
    }

fun spaceIcon(icon: String) =
    when (icon) {
        "Work" -> ShikeIcons.WorkOutline
        "Study" -> ShikeIcons.School
        "Star" -> ShikeIcons.StarOutline
        else -> ShikeIcons.Home
    }

@Composable
private fun SideNav(
    data: AppData,
    space: Workspace?,
    page: String,
    onPage: (String) -> Unit,
    onSpace: (String) -> Unit,
) {
    Column(
        Modifier.width(220.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(ShikeIcons.Inventory2, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text("拾刻", style = MaterialTheme.typography.titleLarge)
        }
        Text("工作空间", Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium)
        data.workspaces.forEach { w ->
            NavigationDrawerItem(
                label = { Text(w.name) },
                selected = page == "home" && space?.id == w.id,
                onClick = { onSpace(w.id) },
                icon = { Icon(spaceIcon(w.icon), null) },
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        listOf("TASK", "NOTE", "HABIT", "PLAN", "calendar", "statistics", "review", "settings")
            .forEach { key ->
                NavigationDrawerItem(
                    label = { Text(pageTitle(key)) },
                    selected = page == key,
                    onClick = { onPage(key) },
                    icon = { Icon(kindIcon(if (key == "calendar") "EVENT" else key), null) },
                )
            }
    }
}

@Composable
private fun LockScreen(activity: MainActivity, settings: Appearance, onUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var attempts by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            ShikeIcons.Lock,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("解锁拾刻", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            pin,
            { pin = it.filter(Char::isDigit).take(12) },
            label = { Text("数字密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions =
                androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                ),
            modifier = Modifier.padding(vertical = 16.dp),
        )
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Button(
            enabled = !busy,
            onClick = {
                scope.launch {
                    busy = true
                    if (withContext(Dispatchers.Default) { Crypto.verify(pin, settings.lockHash) })
                        onUnlock()
                    else {
                        attempts++
                        error = "密码不正确"
                        if (attempts >= 5) {
                            error = "尝试次数过多，请稍候"
                            delay(5000)
                        }
                    }
                    busy = false
                }
            },
        ) {
            Text("解锁")
        }
        if (settings.biometric)
            TextButton(
                onClick = {
                    runCatching {
                            BiometricPrompt.Builder(activity)
                                .setTitle("解锁拾刻")
                                .setAllowedAuthenticators(
                                    android.hardware.biometrics.BiometricManager.Authenticators
                                        .BIOMETRIC_STRONG
                                )
                                .setNegativeButton("使用数字密码", activity.mainExecutor) { _, _ -> }
                                .build()
                                .authenticate(
                                    CancellationSignal(),
                                    activity.mainExecutor,
                                    object : BiometricPrompt.AuthenticationCallback() {
                                        override fun onAuthenticationSucceeded(
                                            result: BiometricPrompt.AuthenticationResult
                                        ) {
                                            onUnlock()
                                        }
                                    },
                                )
                        }
                        .onFailure { error = "此设备暂时无法使用生物识别" }
                }
            ) {
                Text("使用生物识别")
            }
    }
}

@Composable
private fun QuickCapture(close: () -> Unit, save: (String) -> Unit) {
    var body by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("快速记录") },
        text = {
            OutlinedTextField(
                body,
                { body = it },
                label = { Text("刚刚想到什么？") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    saving = true
                    save(body.trim())
                },
                enabled = body.isNotBlank() && !saving,
            ) {
                Text("保存记录")
            }
        },
        dismissButton = { TextButton(onClick = close) { Text("取消") } },
    )
}
