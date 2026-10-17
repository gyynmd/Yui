package top.trangle.mbga.hook

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.util.concurrent.atomic.AtomicBoolean
import top.trangle.mbga.BILI_IN_PKG_ID
import top.trangle.mbga.xposed.HookEnv
import top.trangle.mbga.xposed.YLog
import top.trangle.mbga.xposed.method
import top.trangle.mbga.xposed.toClass

/**
 * Entry point of the module, listed in `META-INF/xposed/java_init.list`.
 *
 * This is a libxposed API 102 module: it extends [XposedModule] and is driven by framework
 * callbacks instead of the legacy `IXposedHookLoadPackage` interface.
 */
class HookEntry : XposedModule() {
    private val hookersLoaded = AtomicBoolean(false)

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        runCatching { YLog.info("$BUILD_TAG loaded in process: ${param.processName}") }
    }

    override fun onPackageReady(param: PackageReadyParam) {
        // Nothing here may escape into the framework: a throwing callback would kill the host.
        runCatching { setup(param) }
            .onFailure { YLog.error("failed to set up module for ${param.packageName}", it) }
    }

    private fun setup(param: PackageReadyParam) {
        val packageName = param.packageName
        if (packageName != BILI_IN_PKG_ID && packageName != LEGACY_BILI_PKG_ID) return

        HookEnv.init(
            module = this,
            hostPackage = packageName,
            classLoader = param.classLoader,
        )
        YLog.info("hooking $packageName")

        // The version dependent hooks need the host Context, which is only available once the
        // Application is created. Hook that point and install every hooker right before the
        // host's Application.onCreate() runs.
        runCatching {
            "android.app.Instrumentation".toClass()
                .method {
                    name = "callApplicationOnCreate"
                    paramCount(1)
                }
                .hook {
                    before {
                        val application = args[0] as? Context ?: return@before
                        HookEnv.attachContext(application)
                        loadHookers()
                    }
                }
        }.onFailure { YLog.error("failed to hook Instrumentation.callApplicationOnCreate", it) }

        // Fallback for the same moment in case the hook above could not be installed.
        runCatching {
            "android.app.Application".toClass()
                .method {
                    name = "onCreate"
                    emptyParam()
                }
                .hook {
                    before {
                        val application = instance as? Context ?: return@before
                        HookEnv.attachContext(application)
                        loadHookers()
                    }
                }
        }.onFailure { YLog.error("failed to hook Application.onCreate", it) }

        // Processes that are already bound when the module is injected.
        HookEnv.appContext?.let { context ->
            HookEnv.attachContext(context)
            loadHookers()
        }
    }

    private fun loadHookers() {
        if (!hookersLoaded.compareAndSet(false, true)) return
        hookers.forEach { hooker ->
            runCatching { hooker.load() }
                .onFailure { YLog.error("failed to load ${hooker.javaClass.simpleName}", it) }
        }
        YLog.info("all hookers loaded for ${HookEnv.hostPackage}")
    }

    private companion object {
        const val BUILD_TAG = "MBGA libxposed102-r5"
        const val LEGACY_BILI_PKG_ID = "tv.danmaku.bili"

        val hookers =
            listOf(
                MainActivityHooker,
                VideoPlayerHooker,
                VideoDetailHooker,
                SettingEntryHooker,
                VideoCommentHooker,
                HomeViewHooker,
                SearchViewHooker,
                MineViewHooker,
            )
    }
}
