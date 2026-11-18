package com.dy.sparkmodule.core

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed/Xposed 模块入口。
 *
 * 说明：抖音经过混淆、加固与签名校验，其内部类名/方法名随版本变化剧烈，
 * 直接 Hook 私有逻辑非常容易失效。本模块默认采用「无障碍服务读取 UI 节点」
 * 的方式完成识别与发送，稳定性更好；此处仅作为作用域标识与预留 Hook 区。
 */
class SparkXposedEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != Const.AWEME_PACKAGE) return
        XposedBridge.log("DySparkModule: 已注入 ${lpparam.packageName}")

        // ========== 预留 Hook 区 ==========
        // 如需直接 Hook 抖音内部方法（例如会话列表数据源、消息发送接口），
        // 在此用 XposedHelpers.findAndHookMethod(...) 编写；
        // 类名/方法名/参数签名必须先在真机上用反编译工具（如 Jadx）确认，
        // 并随抖音版本更新同步维护。示例：
        //
        // XposedHelpers.findAndHookMethod(
        //     "com.ss.android.ugc.aweme.xxx.SomeClass",
        //     lpparam.classLoader,
        //     "someMethod",
        //     String::class.java,
        //     object : XC_MethodHook() {
        //         override fun afterHookedMethod(param: MethodHookParam) { ... }
        //     }
        // )
    }
}
