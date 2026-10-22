package com.aliothmoon.maafw.remote.internal

import com.aliothmoon.maafw.constant.DefaultDisplayConfig

/**
 * 内嵌浏览器 Activity 自己报上来的所在屏
 *
 * 特权侧唯一的查询手段是 getRunningTasks，部分 ROM 会把 excludeFromRecents 的任务整段藏掉：
 * 那边返回「屏上没任务」时，既可能是 Activity 真没起来，也可能是查不到。这一份由页面亲口上报，
 * 只当正向证据用——它说有就是真在，它说没有不能反推页面没起
 */
object EmbeddedBrowserPresence {

    @Volatile
    var displayId: Int = DefaultDisplayConfig.DISPLAY_NONE

    fun isOn(displayId: Int): Boolean = this.displayId == displayId
}
