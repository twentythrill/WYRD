package com.wyrd.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.wyrd.app.ui.MainViewModel
import com.wyrd.app.ui.screens.HomeScreen
import com.wyrd.app.ui.screens.SettingsScreen
import com.wyrd.app.ui.theme.WyrdTheme

enum class Screen {
    HOME,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var isFirstResume = true

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestHighRefreshRate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val appTheme by viewModel.appTheme.collectAsState()
            WyrdTheme(appTheme = appTheme) {
                var currentScreen by remember { mutableStateOf(Screen.HOME) }

                BackHandler(enabled = currentScreen == Screen.SETTINGS) {
                    viewModel.hapticManager.click()
                    currentScreen = Screen.HOME
                }

                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        if (targetState == Screen.SETTINGS) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width / 3 } + fadeOut()
                            )
                        } else {
                            (slideInHorizontally { width -> -width / 3 } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut()
                            )
                        }
                    },
                    label = "screen_navigation"
                ) { screen ->
                    when (screen) {
                        Screen.HOME -> {
                            HomeScreen(
                                viewModel = viewModel,
                                onNavigateSettings = { currentScreen = Screen.SETTINGS }
                            )
                        }
                        Screen.SETTINGS -> {
                            SettingsScreen(
                                viewModel = viewModel,
                                onBack = { currentScreen = Screen.HOME }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requestHighRefreshRate()
        if (isFirstResume) {
            isFirstResume = false
            return
        }
        viewModel.refreshSync()
    }

    private fun requestHighRefreshRate() {
        try {
            val currentDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            } ?: return

            val supportedModes = currentDisplay.supportedModes
            if (supportedModes.isNullOrEmpty()) return

            val currentMode = currentDisplay.mode
            val highestRateMode = supportedModes
                .filter {
                    it.physicalWidth == currentMode.physicalWidth &&
                        it.physicalHeight == currentMode.physicalHeight
                }
                .maxByOrNull { it.refreshRate }
                ?: supportedModes.maxByOrNull { it.refreshRate }
                ?: return

            val layoutParams: WindowManager.LayoutParams = window.attributes
            if (layoutParams.preferredDisplayModeId != highestRateMode.modeId ||
                layoutParams.preferredRefreshRate != highestRateMode.refreshRate
            ) {
                layoutParams.preferredDisplayModeId = highestRateMode.modeId
                layoutParams.preferredRefreshRate = highestRateMode.refreshRate
                window.attributes = layoutParams
            }
        } catch (_: Exception) {
            // Gracefully ignore if OEM display service restricts mode queries
        }
    }
}

