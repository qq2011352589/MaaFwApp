package com.aliothmoon.maafw.cloud

import java.security.SecureRandom

/**
 * 内嵌浏览器投放的一次性口令
 *
 * [CloudBrowserActivity] 必须导出：虚拟屏由特权进程（shell 身份）创建，屏的 owner 就是它，
 * 只有 owner 有权把 Activity 投上去——app 自己投会被 `isCallerAllowedToLaunchOnDisplay` 拒掉
 * （真机现场：`SecurityException: ... with launchDisplayId=2`）。导出之后任意应用都能带着 URL
 * 起这个页面，而页面用的是本应用的 WebView 档案（里面有云游戏登录 Cookie），所以 URL 与 mode
 * 只认这一轮签发的口令：每次准备运行新发一个，口令不匹配就退回默认门户，不给外部应用递参数。
 */
object CloudBrowserLaunchTicket {

    private const val LENGTH = 32
    private const val ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val random = SecureRandom()

    @Volatile
    private var current: String? = null

    /** 为本轮运行签发口令，返回值随 [com.aliothmoon.maafw.runner.EmbeddedBrowserPayload] 交给特权进程 */
    fun issue(): String {
        val token = buildString(LENGTH) {
            repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
        current = token
        return token
    }

    /** 只认最近一次签发的那个：外部应用既猜不到，也没机会在签发前塞进来 */
    fun matches(candidate: String?): Boolean {
        val expected = current
        return candidate != null && expected != null && candidate == expected
    }
}
