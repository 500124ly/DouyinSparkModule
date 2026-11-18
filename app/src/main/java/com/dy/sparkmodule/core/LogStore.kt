package com.dy.sparkmodule.core

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 简易环形日志：内存保存最近 200 条，供面板「诊断」查看与节点 dump 排查 */
object LogStore {
    private const val MAX = 200
    private val buf = ArrayDeque<String>()
    private val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @Synchronized
    fun log(msg: String) {
        val line = "${ts.format(Date())}  $msg"
        buf.addLast(line)
        while (buf.size > MAX) buf.removeFirst()
    }

    @Synchronized
    fun snapshot(): String = buf.joinToString("\n")

    @Synchronized
    fun clear() = buf.clear()

    fun dumpToFile(ctx: Context): String {
        val dir = ctx.filesDir.resolve("spark_logs")
        dir.mkdirs()
        val file = dir.resolve("nodes_${System.currentTimeMillis()}.txt")
        file.writeText(snapshot())
        return file.absolutePath
    }
}
