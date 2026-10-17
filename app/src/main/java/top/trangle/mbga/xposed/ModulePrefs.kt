package top.trangle.mbga.xposed

import android.content.SharedPreferences

/**
 * Reads the settings of the module with the same ergonomics the hookers were written against:
 * every getter has a default value.
 */
class ModulePrefs internal constructor(private val delegate: SharedPreferences) {
    val all: Map<String, *>
        get() = delegate.all

    val isAvailable: Boolean
        get() = delegate.all.isNotEmpty()

    fun getBoolean(key: String, defValue: Boolean = false): Boolean =
        runCatching { delegate.getBoolean(key, defValue) }.getOrDefault(defValue)

    fun getInt(key: String, defValue: Int = 0): Int =
        runCatching { delegate.getInt(key, defValue) }.getOrDefault(defValue)

    fun getLong(key: String, defValue: Long = 0L): Long =
        runCatching { delegate.getLong(key, defValue) }.getOrDefault(defValue)

    fun getString(key: String, defValue: String? = null): String =
        runCatching { delegate.getString(key, defValue) ?: defValue ?: "" }.getOrDefault("")

    fun getStringSet(key: String): Set<String> =
        runCatching { delegate.getStringSet(key, emptySet()) ?: emptySet() }
            .getOrDefault(emptySet())
}
