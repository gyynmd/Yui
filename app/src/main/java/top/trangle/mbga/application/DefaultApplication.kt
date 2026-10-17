package top.trangle.mbga.application

import android.app.Application
import com.google.android.material.color.DynamicColors
import top.trangle.mbga.prefs.RemotePrefsBridge
import top.trangle.mbga.xposed.YLog

class DefaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { RemotePrefsBridge.init(this) }
            .onFailure { YLog.error("failed to initialize preference bridge", it) }
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
