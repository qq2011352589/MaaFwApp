package com.aliothmoon.maafw.cloud

/**
 * 特权进程与 app 进程共同读的启动参数
 *
 * 特权进程用 [CloudBrowserActivity] 的类名拼 Intent，不直接引用 Activity 类：
 * 那个类会拉 AndroidX 与 WebView 的一串依赖进 app_process 的类加载器
 */
object CloudBrowserContract {
    const val ACTIVITY_CLASS = "com.aliothmoon.maafw.cloud.CloudBrowserActivity"

    const val EXTRA_MODE = "com.aliothmoon.maafw.cloud.extra.MODE"
    const val EXTRA_URL = "com.aliothmoon.maafw.cloud.extra.URL"

    const val MODE_PC = "pc"
    const val MODE_MOBILE = "mobile"

    const val DEFAULT_URL = "https://endfield.hypergryph.com/cloud"
}
