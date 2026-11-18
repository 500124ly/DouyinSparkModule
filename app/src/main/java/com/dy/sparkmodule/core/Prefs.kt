package com.dy.sparkmodule.core

import android.content.Context
import com.dy.sparkmodule.model.FriendConfig
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random

/**
 * 配置读写：基于 SharedPreferences + JSON。
 * 面板、定时接收器、无障碍服务同属本 APK 进程，读写一致。
 */
object Prefs {
    private const val FILE = "dy_spark_prefs"

    private const val KEY_FRIENDS = "friends_json"
    private const val KEY_TEXTS = "texts_json"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"
    private const val KEY_DONE = "done_map_json"
    private const val KEY_SPARK_KEYWORD = "spark_keyword"
    private const val KEY_SEND_KEYWORD = "send_keyword"
    private const val KEY_DEBUG_DUMP = "debug_dump"
    private const val KEY_MSG_CHECK = "msg_check_enabled"
    private const val KEY_SELF_BUBBLE_ID = "self_bubble_id"
    private const val KEY_USE_SIDE = "use_side_judgment"
    private const val KEY_TODAY_KEYWORD = "today_keyword"
    private const val KEY_IGNORE_SPARK = "ignore_spark"
    private const val KEY_EXTINGUISH_KW = "extinguish_keyword"

    private fun sp(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /** 跨天时清空上一天的「已续」记录 */
    private fun clearIfNewDay(ctx: Context) {
        val sp = sp(ctx)
        val storedDate = sp.getString("done_date", "")
        val today = today()
        if (storedDate != today) {
            sp.edit().putString(KEY_DONE, JSONObject().toString())
                .putString("done_date", today).apply()
        }
    }

    // ---------- 好友列表 ----------

    fun getFriends(ctx: Context): MutableList<FriendConfig> {
        val raw = sp(ctx).getString(KEY_FRIENDS, "") ?: ""
        if (raw.isBlank()) return mutableListOf()
        val arr = JSONArray(raw)
        val list = mutableListOf<FriendConfig>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(FriendConfig.fromJson(o))
        }
        return list
    }

    fun setFriends(ctx: Context, list: List<FriendConfig>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp(ctx).edit().putString(KEY_FRIENDS, arr.toString()).apply()
    }

    // ---------- 全局轮换文案 ----------

    fun getGlobalTexts(ctx: Context): MutableList<String> {
        val raw = sp(ctx).getString(KEY_TEXTS, "") ?: ""
        if (raw.isBlank()) return mutableListOf()
        val arr = JSONArray(raw)
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) list.add(arr.optString(i))
        return list.filter { it.isNotBlank() }.toMutableList()
    }

    fun setGlobalTexts(ctx: Context, texts: List<String>) {
        val arr = JSONArray()
        texts.filter { it.isNotBlank() }.forEach { arr.put(it.trim()) }
        sp(ctx).edit().putString(KEY_TEXTS, arr.toString()).apply()
    }

    /** 为某好友挑选本次要发送的文案：专属优先，否则全局随机轮换 */
    fun pickText(ctx: Context, friend: FriendConfig): String {
        friend.customText?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        val globals = getGlobalTexts(ctx)
        if (globals.isNotEmpty()) return globals[Random().nextInt(globals.size)]
        return "在吗" // 兜底占位，建议在面板里填写正式文案
    }

    // ---------- 定时开关 ----------

    fun isEnabled(ctx: Context) = sp(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, enabled: Boolean) =
        sp(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply()

    fun getHour(ctx: Context) = sp(ctx).getInt(KEY_HOUR, 9)
    fun getMinute(ctx: Context) = sp(ctx).getInt(KEY_MINUTE, 0)

    fun setSchedule(ctx: Context, hour: Int, minute: Int) =
        sp(ctx).edit().putInt(KEY_HOUR, hour).putInt(KEY_MINUTE, minute).apply()

    // ---------- 当日已续状态 ----------

    fun isFriendDoneToday(ctx: Context, name: String): Boolean {
        clearIfNewDay(ctx)
        val raw = sp(ctx).getString(KEY_DONE, "") ?: return false
        val map = runCatching { JSONObject(raw) }.getOrNull() ?: return false
        return map.optJSONObject(today())?.has(name) == true
    }

    fun markFriendDoneToday(ctx: Context, name: String) {
        clearIfNewDay(ctx)
        val sp = sp(ctx)
        val map = runCatching { JSONObject(sp.getString(KEY_DONE, "") ?: "") }.getOrNull() ?: JSONObject()
        val day = map.optJSONObject(today()) ?: JSONObject()
        day.put(name, "1")
        map.put(today(), day)
        sp.edit().putString(KEY_DONE, map.toString()).apply()
    }

    fun getDoneNamesToday(ctx: Context): Set<String> {
        clearIfNewDay(ctx)
        val raw = sp(ctx).getString(KEY_DONE, "") ?: return emptySet()
        val map = runCatching { JSONObject(raw) }.getOrNull() ?: return emptySet()
        val day = map.optJSONObject(today()) ?: return emptySet()
        val out = mutableSetOf<String>()
        val it = day.keys()
        while (it.hasNext()) out.add(it.next())
        return out
    }

    // ---------- 节点匹配关键字（真机适配用） ----------

    fun getSparkKeyword(ctx: Context) =
        sp(ctx).getString(KEY_SPARK_KEYWORD, "火花") ?: "火花"

    fun setSparkKeyword(ctx: Context, v: String) =
        sp(ctx).edit().putString(KEY_SPARK_KEYWORD, v).apply()

    fun getSendKeyword(ctx: Context) =
        sp(ctx).getString(KEY_SEND_KEYWORD, "发送") ?: "发送"

    fun setSendKeyword(ctx: Context, v: String) =
        sp(ctx).edit().putString(KEY_SEND_KEYWORD, v).apply()

    fun isDebugDump(ctx: Context) = sp(ctx).getBoolean(KEY_DEBUG_DUMP, false)
    fun setDebugDump(ctx: Context, v: Boolean) =
        sp(ctx).edit().putBoolean(KEY_DEBUG_DUMP, v).apply()

    // ---------- 会话内消息归属防重 ----------

    /** 是否进入会话后读取气泡判断今天我方是否已发（关闭则纯靠本地记录防重） */
    fun isMsgCheckEnabled(ctx: Context) = sp(ctx).getBoolean(KEY_MSG_CHECK, true)
    fun setMsgCheckEnabled(ctx: Context, v: Boolean) =
        sp(ctx).edit().putBoolean(KEY_MSG_CHECK, v).apply()

    /** 我方气泡 resource-id 含有关键字；留空则改用左右位置判断 */
    fun getSelfBubbleIdKeyword(ctx: Context) =
        sp(ctx).getString(KEY_SELF_BUBBLE_ID, "") ?: ""
    fun setSelfBubbleIdKeyword(ctx: Context, v: String) =
        sp(ctx).edit().putString(KEY_SELF_BUBBLE_ID, v.trim()).apply()

    /** 归属判断：气泡靠右视为我方（resource-id 关键字优先） */
    fun isUseSideJudgment(ctx: Context) = sp(ctx).getBoolean(KEY_USE_SIDE, true)
    fun setUseSideJudgment(ctx: Context, v: Boolean) =
        sp(ctx).edit().putBoolean(KEY_USE_SIDE, v).apply()

    /** 会话时间戳里表示「今天」的关键字 */
    fun getTodayKeyword(ctx: Context) =
        sp(ctx).getString(KEY_TODAY_KEYWORD, "今天") ?: "今天"
    fun setTodayKeyword(ctx: Context, v: String) =
        sp(ctx).edit().putString(KEY_TODAY_KEYWORD, v.trim().ifBlank { "今天" }).apply()

    // ---------- 火花状态识别 ----------

    /** 开启后：匹配到好友即进入会话发送，不再强制要求页面出现火花文字（适配火花仅为图标的版本） */
    fun isIgnoreSpark(ctx: Context) = sp(ctx).getBoolean(KEY_IGNORE_SPARK, true)
    fun setIgnoreSpark(ctx: Context, v: Boolean) =
        sp(ctx).edit().putBoolean(KEY_IGNORE_SPARK, v).apply()

    /** 熄灭火花关键词（逗号分隔）；识别到即视为火花已熄灭，正常发送消息重燃 */
    fun getExtinguishKeyword(ctx: Context) =
        sp(ctx).getString(KEY_EXTINGUISH_KW, "火花已熄灭,火花消失") ?: "火花已熄灭,火花消失"
    fun setExtinguishKeyword(ctx: Context, v: String) =
        sp(ctx).edit().putString(KEY_EXTINGUISH_KW, v.trim()).apply()

    /** 解析为熄灭关键词列表，支持中英文逗号 */
    fun getExtinguishKeywords(ctx: Context): List<String> =
        getExtinguishKeyword(ctx).split(",", "，").map { it.trim() }.filter { it.isNotBlank() }
}
