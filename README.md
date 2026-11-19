# 拾刻 Android 试用版

拾刻是一款本地整理个人任务、习惯、笔记和计划的应用。试用版支持 Android 12（API 31）及以上，首次打开进入空白的「日常生活」工作空间。

## 使用

- 点右下角加号快速记录。保存后可以继续创建任务、笔记或日程，并保留原始记录。长按加号打开快捷创建菜单。
- 在「记录」「今日」「综合」之间点击切换或左右滑动。打开「综合」可进入全部功能。宽屏设备另有侧栏。
- 在「今日」点右上角的布局按钮编辑积木。积木菜单支持配置、调整宽度、复制、锁定、折叠和移除。可将积木归入组合并创建布局快照。
- 「设置 → 数据保护」可导出加密备份、导入恢复和启用应用锁。导入成功后会替换本机现有记录。请保管好备份密码。

## 构建

需要 JDK 17 或 21、Android SDK 35 和 Android Build Tools 35.0.0。Android Studio 同步工程后可直接运行 `app`。使用命令行时，设置 `ANDROID_HOME` 或在 `local.properties` 中填写自己的 `sdk.dir`。

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

Windows 使用 `gradlew.bat`。调试包路径为 `app/build/outputs/apk/debug/app-debug.apk`。使用 `assemblePreview` 可生成压缩后的试用包。两个试用构建均使用调试签名，正式发布需要配置发布签名。升级前请保留加密备份，以便在签名变化时迁移。

详见 [技术设计与验收](docs/design-and-acceptance.md)。
