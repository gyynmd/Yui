package top.trangle.mbga.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import top.trangle.mbga.xposed.HookEnv
import top.trangle.mbga.xposed.YLog

/**
 * Publishes the settings of this module to the Xposed framework.
 *
 * The framework stores module settings in its own database: the module app writes them through
 * [XposedService.getRemotePreferences] (group [GROUP]) and every hooked process reads the very
 * same group with `XposedModule.getRemotePreferences(GROUP)`.
 *
 * The settings UI keeps using the normal `SharedPreferences` of the module app, and every change
 * is mirrored into the framework here. Without a bound framework service the module simply works
 * with its local preferences (hooks then see no values).
 */
object RemotePrefsBridge {
    /** Group name shared with the hook side, see [HookEnv]. */
    const val GROUP = "config"

    @Volatile private var remote: SharedPreferences? = null
    private var local: SharedPreferences? = null
    private var started = false

    private val pushListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> pushAll() }

    fun init(context: Context) {
        if (started) return
        started = true

        val appContext = context.applicationContext
        local =
            runCatching { PreferenceManager.getDefaultSharedPreferences(appContext) }
                .onFailure { YLog.error("failed to open local preferences", it) }
                .getOrNull()
        local?.registerOnSharedPreferenceChangeListener(pushListener)

        val listener =
            object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    YLog.info(
                        "framework service bound: ${service.frameworkName} ${service.frameworkVersion}",
                    )
                    remote =
                        runCatching { service.getRemotePreferences(GROUP) }
                            .onFailure { YLog.error("failed to get remote preferences", it) }
                            .getOrNull()
                    pushAll()
                }

                override fun onServiceDied(service: XposedService) {
                    YLog.info("framework service died")
                    remote = null
                }
            }

        runCatching { XposedServiceHelper.registerListener(listener) }
            .onFailure { YLog.error("failed to register framework service listener", it) }
    }

    /** Mirrors every locally stored value into the framework preferences. */
    fun pushAll() {
        val target = remote ?: return
        val source = local ?: return
        runCatching {
            val values = source.all
            val editor = target.edit().clear()
            values.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            editor.apply()
            YLog.debug("pushed ${values.size} preferences to the framework")
        }.onFailure { YLog.error("failed to push preferences", it) }
    }
}
