package com.dy.sparkmodule.core

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.dy.sparkmodule.model.FriendConfig

/**
 * 无障碍自动化核心。
 *
 * 工作方式：在消息列表节点树中按昵称匹配目标好友 → 识别火花标识 → 进入会话 →
 * 若当天尚未发送，则向输入框写入预设文案并点击发送。
 *
 * 【真机适配提示】抖音界面节点的 resource-id / 文本 / 层级会随版本变化，
 * 首次使用请先开启「诊断模式」，进入消息列表与会话页，导出节点树，
 * 据此核对下方的匹配关键字与控件类型（见 README）。
 */
class SparkAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: SparkAccessibilityService? = null
    }

    val handler = Handler(Looper.getMainLooper())
    private var running = false
    private val pending = ArrayDeque<FriendConfig>()
    private var lastDumpAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        LogStore.log("无障碍服务已连接")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName != Const.AWEME_PACKAGE) return
        if (!Prefs.isDebugDump(this)) return
        val now = System.currentTimeMillis()
        if (now - lastDumpAt < 1500) return
        lastDumpAt = now
        rootInActiveWindow?.let { dumpTree(it, "事件节点树") }
    }

    override fun onInterrupt() {}

    // ====================== 对外入口 ======================

    /** 执行一轮当日续火花；可由定时接收器或面板「立即试跑」调用 */
    fun runOnce() {
        if (running) {
            LogStore.log("上一轮仍在执行，本次跳过")
            return
        }
        val friends = Prefs.getFriends(this).filterNot { Prefs.isFriendDoneToday(this, it.name) }
        if (friends.isEmpty()) {
            LogStore.log("今日所有目标好友均已续过火花，无需发送")
            return
        }
        pending.clear()
        pending.addAll(friends)
        running = true
        LogStore.log("本轮待处理 ${pending.size} 位好友")
        goToMessageList()
        handler.postDelayed({ processNext() }, 1200)
    }

    // ====================== 流程步骤 ======================

    private fun processNext() {
        if (pending.isEmpty()) {
            running = false
            LogStore.log("本轮续火花流程结束")
            return
        }
        val friend = pending.removeFirst()
        handler.postDelayed({ processFriend(friend) }, 900)
    }

    /** 尝试切到底部「消息」页 */
    private fun goToMessageList() {
        val root = rootInActiveWindow ?: return
        val tab = findByText(root, "消息") ?: findByDesc(root, "消息")
        tab?.let { clickableAncestor(it)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
    }

    private fun processFriend(friend: FriendConfig) {
        if (Prefs.isFriendDoneToday(this, friend.name)) {
            LogStore.log("${friend.name} 今日已续，跳过")
            processNext()
            return
        }
        val root = rootInActiveWindow
        if (root == null) {
            LogStore.log("${friend.name}：无法获取界面节点，跳过")
            processNext()
            return
        }

        val row = findRowForFriend(root, friend)
        if (row == null) {
            LogStore.log("${friend.name}：消息列表中未找到该好友，请核对昵称/备注")
            processNext()
            return
        }

        val sparkKw = Prefs.getSparkKeyword(this)
        val rowText = row.textSubtree()
        if (!rowText.contains(sparkKw)) {
            LogStore.log("${friend.name}：未检测到火花标识（关键字「$sparkKw」），跳过")
            processNext()
            return
        }

        clickableAncestor(row)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        LogStore.log("已进入与 ${friend.name} 的会话")
        handler.postDelayed({ sendInConversation(friend) }, 1100)
    }

    private fun sendInConversation(friend: FriendConfig) {
        val root = rootInActiveWindow
        if (root == null) {
            LogStore.log("${friend.name}：会话页节点获取失败，返回")
            backToList()
            return
        }

        // 会话内防重：读取气泡归属与时间，判断今天我方是否已发。
        // true=已发（跳过并补记本地状态）；false=未发（继续）；null=判断不清（不拦截，回退本地记录）
        if (Prefs.isMsgCheckEnabled(this)) {
            when (conversationRepliedToday(root)) {
                true -> {
                    Prefs.markFriendDoneToday(this, friend.name)
                    LogStore.log("${friend.name}：会话内检测到今天我方已发，跳过不重复发送")
                    backToList()
                    handler.postDelayed({ processNext() }, 900)
                    return
                }
                false -> LogStore.log("${friend.name}：会话内判断今天我方未发，继续")
                null -> LogStore.log("${friend.name}：会话归属判断不清，按本地记录继续")
            }
        }

        val input = findEditText(root)
        if (input == null) {
            LogStore.log("${friend.name}：未找到输入框，可能界面未加载完成")
            backToList()
            processNext()
            return
        }

        val text = Prefs.pickText(this, friend)
        val args = Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text
        )
        val ok = input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        LogStore.log("${friend.name}：写入文案「$text」结果=$ok")

        handler.postDelayed({
            val sendBtn = findSendButton(root)
            if (sendBtn != null && sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Prefs.markFriendDoneToday(this, friend.name)
                LogStore.log("${friend.name}：已发送并标记今日已续")
            } else {
                LogStore.log("${friend.name}：未找到可点击的发送按钮，请核对发送关键字")
            }
            backToList()
            handler.postDelayed({ processNext() }, 900)
        }, 600)
    }

    private fun backToList() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    // ====================== 节点查找工具 ======================

    private fun findRowForFriend(root: AccessibilityNodeInfo, friend: FriendConfig): AccessibilityNodeInfo? {
        var hit: AccessibilityNodeInfo? = null
        traverse(root) { node ->
            val t = node.text?.toString().orEmpty()
            if (friend.matches(t) && hit == null) hit = node
        }
        return hit
    }

    private fun findByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        var hit: AccessibilityNodeInfo? = null
        traverse(root) { node ->
            if (node.text?.toString()?.trim() == text && hit == null) hit = node
        }
        return hit
    }

    private fun findByDesc(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        var hit: AccessibilityNodeInfo? = null
        traverse(root) { node ->
            if (node.contentDescription?.toString()?.contains(desc) == true && hit == null) hit = node
        }
        return hit
    }

    private fun findEditText(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 优先按类名找 EditText；找不到再按 hint 关键字兜底
        var edit: AccessibilityNodeInfo? = null
        traverse(root) { node ->
            val cls = node.className?.toString().orEmpty()
            if (cls.endsWith("EditText") && edit == null) edit = node
        }
        edit?.let { return it }
        traverse(root) { node ->
            val hint = node.hintText?.toString().orEmpty()
            if ((hint.contains("消息") || hint.contains("说点什么")) && edit == null) edit = node
        }
        return edit
    }

    private fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val kw = Prefs.getSendKeyword(this)
        var btn: AccessibilityNodeInfo? = null
        traverse(root) { node ->
            val t = node.text?.toString().orEmpty()
            val d = node.contentDescription?.toString().orEmpty()
            if ((t == kw || d == kw) && btn == null) btn = node
        }
        return btn
    }

    /**
     * 判断会话内今天我方是否已发过消息。
     * 归属优先按我方气泡 resource-id 关键字判断，未配置时按气泡靠右（中线右侧）判断；
     * 日期以会话中的「今天」时间戳为界，时间戳之后出现我方气泡视为今天已发。
     * @return true 今天我方已发；false 今天我方未发；null 无法判断（不拦截，回退本地记录）
     */
    private fun conversationRepliedToday(root: AccessibilityNodeInfo): Boolean? {
        val selfKw = Prefs.getSelfBubbleIdKeyword(this)
        val useSide = Prefs.isUseSideJudgment(this)
        val todayKw = Prefs.getTodayKeyword(this)
        val screenW = resources.displayMetrics.widthPixels
        val timeRegex = Regex("\\d{1,2}:\\d{2}")

        class Bubble(val index: Int, val isSelf: Boolean)
        val bubbles = mutableListOf<Bubble>()
        var lastTodayStampIndex = -1
        var index = 0

        traverse(root) { node ->
            val text = node.text?.toString().orEmpty().trim()
            val id = node.viewIdResourceName.orEmpty()
            val cls = node.className?.toString().orEmpty()

            val isTimeText = text.contains(todayKw) || timeRegex.matches(text)
            if (text.contains(todayKw)) lastTodayStampIndex = index

            // 排除时间戳、输入框等非气泡节点
            if (text.isBlank() || cls.endsWith("EditText") || isTimeText) {
                index++
                return@traverse
            }

            val selfById = selfKw.isNotBlank() && id.contains(selfKw)
            val sideSelf = if (useSide) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                bounds.width() > 0 && bounds.centerX() > screenW / 2
            } else false

            val isSelf: Boolean? = when {
                selfById -> true
                useSide -> sideSelf
                else -> null
            }
            if (isSelf != null) bubbles.add(Bubble(index, isSelf))
            index++
        }

        if (bubbles.isEmpty()) {
            LogStore.log("防重：未识别到可判断归属的气泡")
            return null
        }
        if (lastTodayStampIndex < 0) {
            LogStore.log("防重：未看到「$todayKw」时间标记，无法确认日期，保守不拦截")
            return null
        }

        val selfAfterToday = bubbles.any { it.isSelf && it.index > lastTodayStampIndex }
        if (Prefs.isDebugDump(this)) {
            LogStore.log("防重：气泡 ${bubbles.size} 条，今日时间戳序号=$lastTodayStampIndex，今日后我方气泡=$selfAfterToday")
        }
        return selfAfterToday
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = node
        while (cur != null) {
            if (cur.isClickable) return cur
            cur = cur.parent
        }
        return node
    }

    private fun AccessibilityNodeInfo.textSubtree(): String {
        val sb = StringBuilder()
        traverse(this) { sb.append(it.text?.toString().orEmpty()).append(" ") }
        return sb.toString()
    }

    private fun traverse(node: AccessibilityNodeInfo?, block: (AccessibilityNodeInfo) -> Unit) {
        if (node == null) return
        block(node)
        for (i in 0 until node.childCount) {
            traverse(node.getChild(i), block)
        }
    }

    private fun dumpTree(root: AccessibilityNodeInfo, title: String) {
        val sb = StringBuilder("===== $title =====\n")
        val inner = mutableListOf<String>()
        traverse(root) { node ->
            inner.add(
                "cls=${node.className?.toString()?.substringAfterLast('.')} " +
                    "text=${node.text} desc=${node.contentDescription} " +
                    "click=${node.isClickable} id=${node.viewIdResourceName}"
            )
        }
        sb.append(inner.joinToString("\n"))
        LogStore.log(sb.toString())
    }
}
