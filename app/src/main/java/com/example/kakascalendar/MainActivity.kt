package com.example.kakascalendar

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kakascalendar.ui.components.AppBackground
import com.example.kakascalendar.ui.screens.AppRoot
import com.example.kakascalendar.ui.screens.LockScreen
import com.example.kakascalendar.ui.theme.KakaTheme
import com.example.kakascalendar.ui.theme.isAppDark

class MainActivity : FragmentActivity() {
    private val vm: MainVm by viewModels()

    /** Locked on cold start and after 30s in the background (only matters if app lock is on). */
    private val locked = mutableStateOf(true)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by vm.prefs.collectAsStateWithLifecycle()
            val p = prefs
            KakaTheme(p?.themeMode ?: 0, p?.accent ?: 0, p?.dynamicColor ?: false) {
                val dark = isAppDark(p?.themeMode ?: 0)
                LaunchedEffect(dark) {
                    enableEdgeToEdge(
                        statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                        navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    )
                }
                when {
                    p == null -> AppBackground { Box(Modifier.fillMaxSize()) }
                    p.lock && locked.value -> LockScreen(onUnlock = ::authenticate)
                    else -> {
                        // Turning the lock on mid-session shouldn't lock you out immediately
                        SideEffect { if (!p.lock) locked.value = false }
                        AppRoot(vm, p)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt != 0L && System.currentTimeMillis() - stoppedAt > 30_000) locked.value = true
    }

    private fun authenticate() {
        val auth = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) {
            locked.value = false // no screen lock on the device: nothing to check against
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                locked.value = false
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Kakascalendar")
                .setSubtitle("Your throne, your secrets")
                .setAllowedAuthenticators(auth)
                .build()
        )
    }
}
