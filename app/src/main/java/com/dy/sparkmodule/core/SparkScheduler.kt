package com.dy.sparkmodule.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * 每日定时调度。使用精确闹钟（setExactAndAllowWhileIdle），
 * 触发后由 [SparkAlarmReceiver] 执行流程并再注册下一天。
 */
object SparkScheduler {
    const val ACTION_TRIGGER = "com.dy.sparkmodule.action.TRIGGER"
    private const val REQUEST_CODE = 1001

    private fun pendingIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, SparkAlarmReceiver::class.java).setAction(ACTION_TRIGGER)
        return PendingIntent.getBroadcast(
            ctx, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 按面板配置注册下一次（并循环到每天同一时刻） */
    fun scheduleNext(ctx: Context) {
        if (!Prefs.isEnabled(ctx)) {
            cancel(ctx)
            return
        }
        val hour = Prefs.getHour(ctx)
        val minute = Prefs.getMinute(ctx)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }

        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(ctx)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            } else {
                // 无精确闹钟权限时退回不定时重复，保证基本可用
                am.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP, cal.timeInMillis,
                    AlarmManager.INTERVAL_DAY, pi
                )
            }
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
        LogStore.log("已注册每日 ${hour}:${String.format("%02d", minute)} 定时任务")
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(ctx))
        LogStore.log("已取消定时任务")
    }
}
