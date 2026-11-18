package io.github.itguyvv.codecockpitremote

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.itguyvv.codecockpitremote.data.LanguagePrefs
import io.github.itguyvv.codecockpitremote.ui.screens.ChatScreen
import io.github.itguyvv.codecockpitremote.ui.screens.LockScreen
import io.github.itguyvv.codecockpitremote.ui.screens.OnboardingScreen
import io.github.itguyvv.codecockpitremote.ui.screens.ServersScreen
import io.github.itguyvv.codecockpitremote.ui.screens.SessionsScreen
import io.github.itguyvv.codecockpitremote.ui.screens.SettingsScreen
import io.github.itguyvv.codecockpitremote.ui.theme.CodeCockpitRemoteTheme
import io.github.itguyvv.codecockpitremote.vm.AppViewModel

/**
 * FragmentActivity (not ComponentActivity) because BiometricPrompt requires one.
 * Gate order: app lock → first-run onboarding → the screen stack.
 */
class MainActivity : FragmentActivity() {
    private var downloadReceiver: BroadcastReceiver? = null

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguagePrefs.wrap(base))
    }

    override fun onDestroy() {
        downloadReceiver?.let { unregisterReceiver(it) }
        downloadReceiver = null
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val vm: AppViewModel = ViewModelProvider(this)[AppViewModel::class.java]
        // The system DownloadManager targets this package when the APK download finishes;
        // spoofed broadcasts are harmless — the installer validates the file itself
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                vm.onDownloadComplete(intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1))
            }
        }
        ContextCompat.registerReceiver(this, downloadReceiver!!, filter, ContextCompat.RECEIVER_EXPORTED)
        setContent {
            CodeCockpitRemoteTheme {
                val vm: AppViewModel = viewModel()
                val screen by vm.screen.collectAsStateWithLifecycle()
                val appLockEnabled by vm.appLockEnabled.collectAsStateWithLifecycle()
                val onboardingDone by vm.onboardingDone.collectAsStateWithLifecycle()

                var unlocked by remember { mutableStateOf(false) }
                DisposableEffect(Unit) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP) unlocked = false
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    when {
                        appLockEnabled && !unlocked -> LockScreen(onUnlocked = { unlocked = true })
                        onboardingDone == false -> OnboardingScreen(onFinished = { vm.setOnboardingDone() })
                        else -> Crossfade(targetState = screen, label = "screen") { s ->
                            when (s) {
                                is AppViewModel.Screen.Servers -> ServersScreen(vm)
                                is AppViewModel.Screen.Sessions -> SessionsScreen(vm)
                                is AppViewModel.Screen.Chat -> ChatScreen(vm, s.sessionId)
                                is AppViewModel.Screen.Settings -> SettingsScreen(vm)
                            }
                        }
                    }
                }
            }
        }
    }
}
