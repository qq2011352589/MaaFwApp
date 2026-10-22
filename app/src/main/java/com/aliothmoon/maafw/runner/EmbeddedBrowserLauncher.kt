package com.aliothmoon.maafw.runner

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import com.aliothmoon.maafw.cloud.CloudBrowserContract
import timber.log.Timber

/**
 * 从 app 进程把内嵌浏览器投放到指定屏
 *
 * 只靠特权进程（shell/root）投放会被部分 ROM 的启动管控吞掉：调用返回正常，任务却凭空不出现，
 * 现场只剩「任务未出现在虚拟屏」这句没有信息量的结论。用户点「开始任务」时 app 在前台，
 * 起自己的 Activity 不走那条管控，所以在特权进程补投之前先试一次；投不上也只记日志不拒跑，
 * shell 身份的权限比这里更大，还有机会
 */
class EmbeddedBrowserLauncher(private val context: Context) {

    /**
     * 返回 null 表示投放调用已被系统受理（不等于任务已出现，由特权进程侧轮询确认）；
     * 否则返回失败原因，直接透传给用户——被拦、被拒时异常本身就是唯一的现场
     */
    fun launch(displayId: Int, browser: EmbeddedBrowserPayload): String? {
        val intent = Intent(Intent.ACTION_MAIN)
            .setClassName(context.packageName, CloudBrowserContract.ACTIVITY_CLASS)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
            )
            .putExtra(CloudBrowserContract.EXTRA_MODE, browser.mode)
            .putExtra(CloudBrowserContract.EXTRA_URL, browser.url)
        val options = ActivityOptions.makeBasic().apply { launchDisplayId = displayId }
        return runCatching { context.startActivity(intent, options.toBundle()) }
            .exceptionOrNull()
            ?.also { Timber.e(it, "EmbeddedBrowserLauncher: launch to display %d failed", displayId) }
            ?.let { "${it.javaClass.simpleName}: ${it.message}" }
    }
}
