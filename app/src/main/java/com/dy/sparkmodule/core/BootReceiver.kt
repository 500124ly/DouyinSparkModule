package com.dy.sparkmodule.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机自恢复每日定时任务 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val ctx = context.applicationContext
            if (Prefs.isEnabled(ctx)) SparkScheduler.scheduleNext(ctx)
        }
    }
}
