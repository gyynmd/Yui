package top.trangle.mbga.xposed

import android.content.Context
import android.content.SharedPreferences
import io.github.libxposed.api.XposedModule

/**
 * Global environment shared by all hookers.
 *
 * Filled in by [top.trangle.mbga.hook.HookEntry] when the module is injected into the host
 * package, therefore every hooker can resolve classes and preferences from here.
 */
object HookEnv {
    const val MODULE_PKG = "top.trangle.mbga"
    const val LOG_TAG = "MBGA"

    /** Group used by the module app to publish its settings, see `RemotePrefsBridge`. */
    const val REMOTE_PREFS_GROUP = "config"

    var module: XposedModule? = null
        private set

    var hostPackage: String = ""
        private set

    /** versionCode of the host application, 0 when unknown. */
    var hostVersionCode: Long = 0L
        internal set

    var classLoader: ClassLoader = HookEnv::class.java.classLoader
        private set

    private var resolvedPrefs: SharedPreferences? = null
    private var attachedContext: Context? = null

    val prefs: ModulePrefs
        get() = ModulePrefs(resolvedPrefs ?: EmptyPreferences)

    val prefsReady: Boolean
        get() = resolvedPrefs != null

    /** The host [Context], or null when it is not available yet. */
    val appContext: Context?
        get() = attachedContext ?: currentApplication()

    fun attachContext(context: Context) {
        attachedContext = context
    }

    fun init(
        module: XposedModule,
        hostPackage: String,
        classLoader: ClassLoader,
    ) {
        this.module = module
        this.hostPackage = hostPackage
        this.classLoader = classLoader
        this.resolvedPrefs = resolvePreferences(module)
        YLog.info(
            "module prefs: " +
                (resolvedPrefs?.let { runCatching { "ok(${it.all.size} keys)" }.getOrElse { "error" } }
                    ?: "unavailable"),
        )
    }

    private fun currentApplication(): Context? =
        runCatching {
            Class.forName("android.app.ActivityThread")
                .getDeclaredMethod("currentApplication")
                .invoke(null) as? Context
        }.getOrNull()

    /**
     * Reads the settings published by the module app.
     *
     * The module app writes them through `XposedService.getRemotePreferences("config")`, which the
     * framework stores itself, so the hooked process only has to read the same group. The legacy
     * file-name based groups are kept as a fallback for frameworks that sync the preferences file.
     */
    private fun resolvePreferences(module: XposedModule): SharedPreferences? {
        val groups = listOf(REMOTE_PREFS_GROUP, "${MODULE_PKG}_preferences", "mbga_preferences")
        var fallback: SharedPreferences? = null
        for (group in groups) {
            val prefs = runCatching { module.getRemotePreferences(group) }.getOrNull() ?: continue
            if (fallback == null) fallback = prefs
            val empty = runCatching { prefs.all.isEmpty() }.getOrDefault(true)
            if (!empty) return prefs
        }
        return fallback
    }
}
