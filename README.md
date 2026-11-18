# 抖音续火花辅助 LSPosed 模块（Android / Kotlin）

> ⚠️ **风险与合规提示（请先阅读）**
>
> 1. 自动读取界面、自动发送消息属于自动化操作，**可能违反抖音用户协议**，存在账号被限流、封禁的风险，请自行评估并承担后果。
> 2. 本工程仅用于 **Android 插件开发与无障碍技术学习研究**，请勿用于骚扰陌生人、批量群发、刷量等违规用途。
> 3. 抖音经过混淆、加固与风控，界面节点和内部接口随版本频繁变化，本模块**不保证在任意版本上长期可用**，需要按本文「真机适配」一节自行核对维护。
> 4. 本工程已在本地与 GitHub Actions（ubuntu + JDK17 + SDK34）云编译通过并产出 debug APK；但**尚未在真机上做自动化流程验证**，界面节点匹配需按第六节在你的抖音版本上核对适配。

---

## 一、它能做什么

- 以 LSPosed 模块形式安装到已 root 手机，作用域勾选抖音。
- 面板里**添加指定好友**：填昵称（必填）和备注名（可选），二者在消息列表节点文本中**任一命中即匹配**；可为其配置**专属文案**；不填专属文案时，从多条**全局轮换文案**里随机发一条。
- 设置**每日定时时间**，到点自动拉起抖音、进入对应会话、发送预设文案。
- 按天记录**当日是否已续**：当天已发过的好友不会重复发送；面板「今日状态」实时显示每位好友 ✓已续 / ○未续。
- **会话内消息归属防重**：进入会话后读取消息气泡，判断今天我方是否已发过——已发则跳过并补记本地状态；判断不清时不拦截，回退本地记录兜底，避免重复打扰。
- **从消息列表导入好友**：一键扫描抖音消息列表可见会话，勾选后批量加入，无需逐个手动添加；每位好友可在列表里单独修改专属文案。
- **火花状态识别**：可开关「无火花标识也直接发送」（默认开启，适配火花仅为图标、无文字的版本）；支持熄灭火花关键词（默认「火花已熄灭、火花消失」），识别到熄灭标记也正常发送重燃。
- **结果提示**：发送成功/失败均有 Toast 弹窗提示（成功「【好友】续火花发送成功」，失败「【好友】发送失败，请检查界面」）。
- **日志瘦身**：日常运行日志只保留好友匹配、火花检测、进入会话、发送结果、防重判断等业务记录；完整节点树仅在手动抓取诊断时生成，不再刷屏。
- 内置**诊断模式**：把当前界面节点树写入日志，便于核对控件与关键字。

## 二、实现路线（重要）

抖音内部类名/方法名被混淆且加固，直接 Hook 私有逻辑极脆弱。本模块采用更稳的路线：

- **LSPosed 壳**：`SparkXposedEntry` 作为模块入口与作用域标识，预留了 Hook 区（默认不 Hook 内部方法）。
- **无障碍服务**：`SparkAccessibilityService` 读取消息列表 / 会话界面的 UI 节点，按昵称或备注名匹配好友、识别火花标识、定位输入框与发送按钮，模拟输入并点击发送。
- **定时调度**：`SparkScheduler` 用精确闹钟每日触发，`BootReceiver` 开机后恢复任务。

## 三、工程结构

```
DouyinSparkModule/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml          # 模块标识、作用域、无障碍服务、接收器
        ├── assets/xposed_init            # Xposed 入口类名
        ├── res/                          # 布局、字符串、无障碍配置、图标
        └── java/com/dy/sparkmodule/
            ├── Const.kt                  # 抖音包名等常量
            ├── core/
            │   ├── Prefs.kt              # 好友/文案/定时/已续状态读写
            │   ├── LogStore.kt           # 环形日志与节点 dump
            │   ├── SparkXposedEntry.kt   # LSPosed 入口（预留 Hook 区）
            │   ├── SparkScheduler.kt      # 每日精确闹钟
            │   ├── SparkAlarmReceiver.kt  # 定时触发执行
            │   ├── BootReceiver.kt        # 开机恢复定时
            │   └── SparkAccessibilityService.kt  # 无障碍自动化核心
            ├── model/FriendConfig.kt
            └── ui/
                ├── MainActivity.kt        # 配置面板
                └── FriendAdapter.kt
```

## 四、编译步骤

1. 安装 **Android Studio（Hedgehog 或以上）**，SDK Platform 34、Build Tools 34、JDK 17。
2. 打开本目录 `DouyinSparkModule`，等待 Gradle 同步；若提示缺少 wrapper，执行一次 `gradle wrapper --gradle-version 8.7` 或让 AS 自动生成。
3. 直接运行 `app` 的 **debug 构建**即可（模块无需 release 签名校验）；生成的 APK 在 `app/build/outputs/apk/debug/`。

### 依赖排障

- Xposed/LSPosed API 为 `compileOnly`，不打包进 APK。若 `https://api.xposed.info/` 拉取失败：
  - 方案 A：把 Xposed API jar 下载后放入 `app/libs/`，并在 `app/build.gradle.kts` 改为
    `compileOnly(files("libs/xxx.jar"))`；
  - 方案 B：使用你本机 LSPosed 管理器导出的 API 依赖坐标替换。
- 若 AndroidX / Material 版本与本机 SDK 不符，按 AS 提示升降版本号即可。

## 五、安装与启用（已 root + LSPosed）

1. 把编译出的 APK 装到手机。
2. 打开 **LSPosed 管理器** → 模块 → 启用本模块 → **作用域勾选「抖音」**（`com.ss.android.ugc.aweme`）。
3. **重启手机**（或在 LSPosed 中强制停止抖音后重进）使模块生效。
4. 打开本模块 App：
   - 填写每日时间、全局文案、目标好友；
   - 点「无障碍设置」，在系统无障碍列表里**为本模块开启服务**；
   - 打开「启用每日定时自动续火花」开关。
5. 首次先点「**立即试跑**」，观察日志与实际界面，确认识别与发送正常后再交给定时执行。

> 部分机型需要给本模块「后台弹出界面 / 自启动 / 后台保活」权限，否则定时拉起抖音可能被系统拦截。

## 六、真机适配（必做一次）

不同抖音版本的节点文本、`resource-id`、层级可能不同。首次使用请：

1. 在面板打开「**诊断模式**」。
2. 手动进入抖音**消息列表**和某个**会话页**，回到面板点「**导出日志**」，查看节点树，重点核对：
   - 好友昵称显示在哪个节点（`text` / `contentDescription`）；
   - 火花标识用什么文字（默认关键字「火花」，可在面板改成实际文案，如「连续聊天天」等）；
   - 输入框类名（是否为 `EditText`，hint 文案是什么）；
   - 发送按钮文字或描述（默认关键字「发送」，按实际修改）；
   - **我方气泡**的 `resource-id`（如有稳定关键字，填到面板「我方气泡 ID 关键字」；留空则按气泡靠右判断归属）；会话里表示「今天」的时间戳文字（默认「今天」，按实际修改）。
3. 把核对后的关键字填回面板；若节点结构差异较大，在 `SparkAccessibilityService.kt` 的 `findRowForFriend / findEditText / findSendButton / conversationRepliedToday` 中按 `viewIdResourceName` 精确匹配。

### 常用节点查看方式

- 手机开发者选项中开启「**显示布局边界 / UI 审查**」；或用电脑 `uiautomator dump` 导出当前界面 XML 对照。
- 本模块诊断日志已记录每个节点的 `className / text / desc / clickable / viewIdResourceName`。

## 七、可扩展点

- **直接 Hook 内部接口**：在 `SparkXposedEntry` 预留区用 `XposedHelpers.findAndHookMethod` 编写；类名/方法名需先用 Jadx 反编译确认。
- **更多匹配规则**：可继续扩展按抖音 ID、会话排序等匹配方式；昵称/备注双匹配已在 `FriendConfig.matches` 中实现。
- **防重判断**：已实现「本地当日记录 + 会话内气泡归属/时间戳」双层判断；`conversationRepliedToday` 在归属或日期判断不清时返回 `null` 不拦截，避免漏发。若气泡布局特殊，可在该方法中按 `viewIdResourceName` 进一步精确归属。

## 八、常见问题

| 现象 | 排查方向 |
|---|---|
| 定时到点没反应 | 无障碍是否开启；模块是否在 LSPosed 作用域内；精确闹钟/自启动/后台弹出权限是否被系统限制；看日志是否有「无障碍服务未连接」 |
| 找不到好友 | 昵称/备注与列表显示是否完全一致；诊断模式看节点 text；是否需要按备注而非昵称匹配 |
| 找不到输入框/发送按钮 | 按第六节核对 hint、按钮文字，修改面板关键字或代码匹配逻辑 |
| 重复发送 | 本地「当日已续」记录是否被清除；会话内防重是否开启；气泡归属/时间戳关键字是否核对正确；日志里防重判定是 true / false / null |
| 该发却被跳过 | 防重误判：气泡左右位置或 ID 识别错把对方气泡当成我方；可先关闭「会话内防重」只用本地记录，或在诊断日志核对气泡 bounds/id |
| 抖音更新后失效 | 重新跑诊断模式核对节点；必要时更新 Hook/匹配代码 |
