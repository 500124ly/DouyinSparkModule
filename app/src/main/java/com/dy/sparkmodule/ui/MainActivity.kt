package com.dy.sparkmodule.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dy.sparkmodule.core.Const
import com.dy.sparkmodule.R
import com.dy.sparkmodule.core.LogStore
import com.dy.sparkmodule.core.Prefs
import com.dy.sparkmodule.core.SparkAccessibilityService
import com.dy.sparkmodule.core.SparkScheduler
import com.dy.sparkmodule.model.FriendConfig
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var friends: MutableList<FriendConfig>
    private lateinit var adapter: FriendAdapter

    private lateinit var switchEnable: SwitchMaterial
    private lateinit var editTime: TextInputEditText
    private lateinit var editTexts: TextInputEditText
    private lateinit var editSparkKw: TextInputEditText
    private lateinit var editSendKw: TextInputEditText
    private lateinit var switchDebug: SwitchMaterial
    private lateinit var switchMsgCheck: SwitchMaterial
    private lateinit var editSelfBubbleId: TextInputEditText
    private lateinit var editTodayKw: TextInputEditText
    private lateinit var statusText: TextView
    private lateinit var logText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switchEnable = findViewById(R.id.switchEnable)
        editTime = findViewById(R.id.editTime)
        editTexts = findViewById(R.id.editTexts)
        editSparkKw = findViewById(R.id.editSparkKw)
        editSendKw = findViewById(R.id.editSendKw)
        switchDebug = findViewById(R.id.switchDebug)
        switchMsgCheck = findViewById(R.id.switchMsgCheck)
        editSelfBubbleId = findViewById(R.id.editSelfBubbleId)
        editTodayKw = findViewById(R.id.editTodayKw)
        statusText = findViewById(R.id.statusText)
        logText = findViewById(R.id.logText)
        val recycler: RecyclerView = findViewById(R.id.recyclerFriends)

        friends = Prefs.getFriends(this)
        adapter = FriendAdapter(friends) { idx ->
            friends.removeAt(idx)
            adapter.notifyDataSetChanged()
            persist()
            refreshStatus()
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        loadPrefs()

        switchEnable.setOnCheckedChangeListener { _, enabled ->
            persist()
            if (enabled) SparkScheduler.scheduleNext(this) else SparkScheduler.cancel(this)
        }
        switchDebug.setOnCheckedChangeListener { _, v -> Prefs.setDebugDump(this, v) }
        switchMsgCheck.setOnCheckedChangeListener { _, v -> Prefs.setMsgCheckEnabled(this, v) }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAddFriend)
            .setOnClickListener { showAddDialog() }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOpenAccessibility)
            .setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRunOnce)
            .setOnClickListener { runOnce() }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnExportLog)
            .setOnClickListener {
                val path = LogStore.dumpToFile(this)
                logText.text = LogStore.snapshot()
                Toast.makeText(this, "日志已导出：$path", Toast.LENGTH_LONG).show()
            }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnClearLog)
            .setOnClickListener { LogStore.clear(); logText.text = "" }
    }

    private fun loadPrefs() {
        switchEnable.isChecked = Prefs.isEnabled(this)
        editTime.setText(String.format("%02d:%02d", Prefs.getHour(this), Prefs.getMinute(this)))
        editTexts.setText(Prefs.getGlobalTexts(this).joinToString("\n"))
        editSparkKw.setText(Prefs.getSparkKeyword(this))
        editSendKw.setText(Prefs.getSendKeyword(this))
        switchDebug.isChecked = Prefs.isDebugDump(this)
        switchMsgCheck.isChecked = Prefs.isMsgCheckEnabled(this)
        editSelfBubbleId.setText(Prefs.getSelfBubbleIdKeyword(this))
        editTodayKw.setText(Prefs.getTodayKeyword(this))
    }

    private fun persist() {
        val hm = editTime.text.toString().trim().split(":")
        if (hm.size == 2) {
            val h = hm[0].toIntOrNull() ?: 9
            val m = hm[1].toIntOrNull() ?: 0
            Prefs.setSchedule(this, h.coerceIn(0, 23), m.coerceIn(0, 59))
        }
        Prefs.setGlobalTexts(this, editTexts.text.toString().split("\n"))
        Prefs.setSparkKeyword(this, editSparkKw.text.toString().trim().ifBlank { "火花" })
        Prefs.setSendKeyword(this, editSendKw.text.toString().trim().ifBlank { "发送" })
        Prefs.setSelfBubbleIdKeyword(this, editSelfBubbleId.text.toString().trim())
        Prefs.setTodayKeyword(this, editTodayKw.text.toString().trim())
        Prefs.setFriends(this, friends)
    }

    private fun showAddDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val nameEt = EditText(this).apply { hint = "好友昵称（必填）" }
        val noteEt = EditText(this).apply { hint = "备注名（可选，与昵称任一命中即可）" }
        val textEt = EditText(this).apply { hint = "专属文案（可留空，用全局轮换）" }
        box.addView(nameEt)
        box.addView(noteEt)
        box.addView(textEt)

        AlertDialog.Builder(this)
            .setTitle("添加目标好友")
            .setView(box)
            .setPositiveButton("添加") { _, _ ->
                val name = nameEt.text.toString().trim()
                if (name.isBlank()) return@setPositiveButton
                val note = noteEt.text.toString().trim().ifBlank { null }
                val custom = textEt.text.toString().trim().ifBlank { null }
                friends.add(FriendConfig(name, note, custom))
                adapter.notifyDataSetChanged()
                persist()
                refreshStatus()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun runOnce() {
        persist()
        val svc = SparkAccessibilityService.instance
        if (svc == null) {
            Toast.makeText(this, "请先在无障碍设置中开启本模块服务", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        packageManager.getLaunchIntentForPackage(Const.AWEME_PACKAGE)?.let { startActivity(it) }
        Toast.makeText(this, "已触发试跑，请看下方日志", Toast.LENGTH_SHORT).show()
        svc.handler.postDelayed({ svc.runOnce() }, 1500)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        logText.text = LogStore.snapshot()
    }

    private fun refreshStatus() {
        val done = Prefs.getDoneNamesToday(this)
        val sb = StringBuilder("今日状态：\n")
        friends.forEach { f ->
            val marked = done.contains(f.name)
            val head = if (f.note.isNullOrBlank()) f.name else "${f.name}（备注：${f.note}）"
            sb.append(if (marked) "✓ " else "○ ")
                .append(head)
                .append(if (marked) "（已续）" else "（未续）")
                .append("\n")
        }
        statusText.text = sb.toString()
    }
}
