package top.trangle.mbga.hook

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import java.util.concurrent.atomic.AtomicBoolean
import top.trangle.mbga.utils.MyHooker
import top.trangle.mbga.xposed.HookEnv
import top.trangle.mbga.xposed.YLog
import top.trangle.mbga.xposed.method
import top.trangle.mbga.xposed.toClass

object MainActivityHooker : MyHooker() {
    private val firstLaunchAlertShown = AtomicBoolean(false)

    override fun onHook() {
        subHook(this::hookMainCreate)
    }

    private fun hookMainCreate() {
        "tv.danmaku.bili.MainActivityV2".toClass().method { name = "onCreate" }
            .hook {
                after {
                    val activity = instance as? Activity ?: return@after

                    // Show the settings on the very first launch only, and never let the
                    // launch of another app break the host.
                    if (!firstLaunchAlertShown.compareAndSet(false, true)) return@after
                    if (!HookEnv.prefs.all.isEmpty()) return@after

                    runCatching {
                        val intent =
                            Intent().apply {
                                component =
                                    ComponentName(
                                        "top.trangle.mbga",
                                        "top.trangle.mbga.views.SettingsActivity",
                                    )
                                putExtra("show_first_launch_alert", true)
                            }
                        activity.startActivity(intent)
                    }.onFailure { YLog.error("failed to open module settings", it) }
                }
            }
    }
}
