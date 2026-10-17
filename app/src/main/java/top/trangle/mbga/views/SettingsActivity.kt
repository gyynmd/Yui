package top.trangle.mbga.views

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import top.trangle.mbga.R
import top.trangle.mbga.databinding.ActivitySettingsBinding

val PREFS_NEED_RESTART =
    arrayOf(
        "mine_add_search",
        "mine_add_im",
        "home_dense_vid_card",
    )

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.extras?.getBoolean("show_first_launch_alert") == true) {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.app_first_launch_message)
                .setNegativeButton(R.string.app_first_launch_goback) { _, _ -> this.finish() }
                .setPositiveButton(R.string.app_first_launch_stay) { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
                .show()
        }
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_content, MySettingsFragment())
            .commit()
    }

    class MySettingsFragment : PreferenceFragmentCompat() {
        private var needRestart = false

        private val prefsListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (
                    !needRestart &&
                    key != null &&
                    (key.startsWith("tabs_disable#") || PREFS_NEED_RESTART.contains(key))
                ) {
                    needRestart = true
                }
            }

        override fun onCreatePreferences(
            savedInstanceState: Bundle?,
            rootKey: String?,
        ) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
        }

        override fun onResume() {
            super.onResume()
            preferenceManager.sharedPreferences
                ?.registerOnSharedPreferenceChangeListener(prefsListener)
        }

        override fun onPause() {
            preferenceManager.sharedPreferences
                ?.unregisterOnSharedPreferenceChangeListener(prefsListener)
            makePreferencesReadable()
            super.onPause()
        }

        /**
         * Best effort: let frameworks that read the module preferences file directly see the
         * current settings. Older code used MODE_WORLD_READABLE here, which throws
         * SecurityException on Android 7.0+ and crashed the settings screen.
         */
        private fun makePreferencesReadable() {
            runCatching {
                val context = requireContext()
                val file =
                    File(
                        context.applicationInfo.dataDir,
                        "shared_prefs/${context.packageName}_preferences.xml",
                    )
                if (file.exists()) file.setReadable(true, false)
            }
        }

        override fun onViewCreated(
            view: View,
            savedInstanceState: Bundle?,
        ) {
            super.onViewCreated(view, savedInstanceState)
            val onBackPressedDispatcher = activity?.onBackPressedDispatcher ?: return
            onBackPressedDispatcher.addCallback {
                val quit: () -> Unit = {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
                if (!needRestart) {
                    quit()
                } else {
                    MaterialAlertDialogBuilder(view.context)
                        .setMessage(R.string.bili_need_restart_message)
                        .setPositiveButton(R.string.common_got_it) { _, _ ->
                            quit()
                        }
                        .create()
                        .show()
                }
            }
        }
    }
}
