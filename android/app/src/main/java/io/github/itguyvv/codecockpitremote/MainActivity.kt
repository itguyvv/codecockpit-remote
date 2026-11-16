package io.github.itguyvv.codecockpitremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.itguyvv.codecockpitremote.ui.screens.ChatScreen
import io.github.itguyvv.codecockpitremote.ui.screens.ServersScreen
import io.github.itguyvv.codecockpitremote.ui.screens.SessionsScreen
import io.github.itguyvv.codecockpitremote.ui.theme.CodeCockpitRemoteTheme
import io.github.itguyvv.codecockpitremote.vm.AppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CodeCockpitRemoteTheme {
                val vm: AppViewModel = viewModel()
                val screen by vm.screen.collectAsStateWithLifecycle()
                when (val s = screen) {
                    is AppViewModel.Screen.Servers -> ServersScreen(vm)
                    is AppViewModel.Screen.Sessions -> SessionsScreen(vm)
                    is AppViewModel.Screen.Chat -> ChatScreen(vm, s.sessionId)
                }
            }
        }
    }
}
