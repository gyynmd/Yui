package top.trangle.mbga.xposed

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable

object HookPriority {
    const val HIGHEST: Int = XposedInterface.PRIORITY_HIGHEST
    const val DEFAULT: Int = XposedInterface.PRIORITY_DEFAULT
    const val LOWEST: Int = XposedInterface.PRIORITY_LOWEST
}

/** Declarative description of a hook, mirroring the DSL the project was originally written with. */
class HookSpec {
    internal var beforeAction: ((HookContext) -> Unit)? = null
        private set

    internal var afterAction: ((HookContext) -> Unit)? = null
        private set

    internal var replaceAction: ((ReplaceContext) -> Any?)? = null
        private set

    fun before(block: HookContext.() -> Unit) {
        beforeAction = block
    }

    fun after(block: HookContext.() -> Unit) {
        afterAction = block
    }

    fun replaceAny(block: ReplaceContext.() -> Any?) {
        replaceAction = block
    }

    fun replaceUnit(block: ReplaceContext.() -> Unit) {
        replaceAction = { replaceContext ->
            block(replaceContext)
            replaceContext.value
        }
    }

    internal fun dispatch(chain: XposedInterface.Chain): Any? {
        // A hook installed by this module must never be the reason the host app dies: every
        // failure below is logged and, when possible, replaced by the original call.
        val context =
            try {
                HookContext(chain)
            } catch (tr: Throwable) {
                YLog.error("failed to create hook context for ${chain.executable}", tr)
                return chain.proceed()
            }

        beforeAction?.let { action ->
            runCatching { action(context) }
                .onFailure { YLog.error("before hook failed for ${chain.executable}", it) }
        }

        val replace = replaceAction
        context.result =
            if (replace != null) {
                val replaceContext = ReplaceContext(context)
                try {
                    replace(replaceContext)
                } catch (tr: Throwable) {
                    YLog.error("replace hook failed for ${chain.executable}", tr)
                    // Fall back to the original implementation unless it already ran.
                    if (replaceContext.proceeded) throw tr
                    chain.proceed(context.args.toTypedArray())
                }
            } else {
                chain.proceed(context.args.toTypedArray())
            }

        afterAction?.let { action ->
            runCatching { action(context) }
                .onFailure { YLog.error("after hook failed for ${chain.executable}", it) }
        }
        return context.result
    }
}

/** State available to `before` / `after` blocks. */
class HookContext internal constructor(internal val chain: XposedInterface.Chain) {
    val instance: Any? = chain.thisObject

    val args: MutableList<Any?> = chain.args.toMutableList()

    var result: Any? = null

    val prefs: ModulePrefs
        get() = HookEnv.prefs

    fun resultNull() {
        result = null
    }

    fun resultTrue() {
        result = true
    }

    fun resultFalse() {
        result = false
    }
}

/** State available to `replaceAny` / `replaceUnit` blocks. */
class ReplaceContext internal constructor(private val context: HookContext) {
    val instance: Any?
        get() = context.instance

    val args: MutableList<Any?>
        get() = context.args

    val prefs: ModulePrefs
        get() = HookEnv.prefs

    internal var value: Any? = null
        private set

    internal var proceeded: Boolean = false
        private set

    /** Invokes the (possibly further hooked) original method with the current arguments. */
    fun callOriginal(): Any? {
        proceeded = true
        val result = context.chain.proceed(context.args.toTypedArray())
        value = result
        return result
    }
}

internal fun hookExecutable(
    executable: Executable,
    priority: Int,
    spec: HookSpec.() -> Unit,
) {
    val module = HookEnv.module
    if (module == null) {
        YLog.warn("module is not initialized, skip hook: $executable")
        return
    }
    val hookSpec =
        try {
            HookSpec().apply(spec)
        } catch (tr: Throwable) {
            YLog.error("failed to build hook spec for $executable", tr)
            return
        }
    runCatching {
        module.hook(executable)
            .setPriority(priority)
            .intercept { chain -> hookSpec.dispatch(chain) }
    }.onFailure { YLog.error("failed to hook $executable", it) }
}
