package top.trangle.mbga.utils

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import top.trangle.mbga.xposed.HookEnv
import top.trangle.mbga.xposed.ModulePrefs
import top.trangle.mbga.xposed.YLog

/** Base class of every hooker of this module. */
abstract class MyHooker {
    protected abstract fun onHook()

    protected val appContext: Context?
        get() = HookEnv.appContext

    protected val packageName: String
        get() = HookEnv.hostPackage

    protected val prefs: ModulePrefs
        get() = HookEnv.prefs

    fun load() {
        runCatching { onHook() }
            .onFailure { YLog.error("failed to load ${javaClass.simpleName}", it) }
    }

    fun subHook(block: () -> Unit) {
        runCatching(block).onFailure { YLog.error(it.toString()) }
    }

    fun versionSpecifiedSubHook(
        block: () -> Unit,
        versionRange: LongRange,
    ) {
        val version = hostVersionCode()
        if (version <= 0L) {
            return YLog.error("unable to get host version, versionSpecifiedSubHook not hooked")
        }
        if (version in versionRange) {
            YLog.debug("loading version specified hook")
            runCatching(block).onFailure { YLog.error(it.toString()) }
        }
    }

    private fun hostVersionCode(): Long {
        if (HookEnv.hostVersionCode > 0L) return HookEnv.hostVersionCode
        val pkgInfo = appContext?.packageManager?.getPackageInfo(packageName, 0) ?: return 0L
        val version = PackageInfoCompat.getLongVersionCode(pkgInfo)
        HookEnv.hostVersionCode = version
        return version
    }
}
