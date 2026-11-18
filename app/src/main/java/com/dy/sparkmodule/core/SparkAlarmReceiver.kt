package com.dy.sparkmodule.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 定时触发：先注册次日闹钟，再拉起抖音并交由无障碍服务执行续火花 */
class SparkAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != SparkScheduler.ACTION_TRIGGER) return
        val ctx = context.applicationContext

        // 无论本次是否执行成功，都先把下一天的闹钟排上
        SparkScheduler.scheduleNext(ctx)

        if (!Prefs.isEnabled(ctx)) {
            LogStore.log("定时任务已停用，跳过")
            return
        }

        val svc = SparkAccessibilityService.instance
        if (svc == null) {
            LogStore.log("无障碍服务未连接：请在系统设置中为本模块开启无障碍权限")
            launchAweme(ctx)
            return
        }

        launchAweme(ctx)
        // 等待抖音前台起来后由服务执行；这里先通知服务尝试跑一轮
        svc.handler.postDelayed({ svc.runOnce() }, 1500)
    }

    private fun launchAweme(ctx: Context) {
        val pm = ctx.packageManager
        val launch = pm.getLaunchIntentForPackage(Const.AWEME_PACKAGE)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(launch)
            LogStore.log("已拉起抖音")
        } else {
            LogStore.log("未找到抖音包 ${Const.AWEME_PACKAGE}")
        }
    }
}
