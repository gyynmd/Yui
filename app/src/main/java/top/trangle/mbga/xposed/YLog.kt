package top.trangle.mbga.xposed

import android.util.Log

/** Thin wrapper around the libxposed log so that hookers keep a single entry point. */
object YLog {
    fun debug(msg: String) = write(Log.DEBUG, msg, null)

    fun info(msg: String) = write(Log.INFO, msg, null)

    fun warn(msg: String) = write(Log.WARN, msg, null)

    fun error(msg: String) = write(Log.ERROR, msg, null)

    fun error(msg: String, tr: Throwable) = write(Log.ERROR, msg, tr)

    private fun write(priority: Int, msg: String, tr: Throwable?) {
        runCatching { Log.println(priority, HookEnv.LOG_TAG, msg) }
        runCatching {
            val module = HookEnv.module ?: return
            if (tr != null) module.log(priority, HookEnv.LOG_TAG, msg, tr)
            else module.log(priority, HookEnv.LOG_TAG, msg)
        }
    }
}
