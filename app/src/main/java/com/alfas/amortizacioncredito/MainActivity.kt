package com.alfas.amortizacioncredito

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.alfas.amortizacioncredito.ui.screens.AmortizationScreen
import com.alfas.amortizacioncredito.ui.AmortizationViewModel
import com.alfas.amortizacioncredito.ui.screens.SplashScreen
import com.alfas.amortizacioncredito.ui.theme.AmortizacionCreditoTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AmortizationViewModel by viewModels()

    companion object {
        // Garantiza que la animación de Splash inicial solo se ejecute una única vez por sesión/arranque en frío
        var hasShownSplashThisSession = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmortizacionCreditoTheme {
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { _ -> }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                var showSplash by rememberSaveable { mutableStateOf(!hasShownSplashThisSession) }

                Crossfade(
                    targetState = showSplash,
                    animationSpec = tween(durationMillis = 400),
                    label = "splash_transition"
                ) { isSplash ->
                    if (isSplash) {
                        SplashScreen(
                            onTimeout = {
                                hasShownSplashThisSession = true
                                showSplash = false
                            }
                        )
                    } else {
                        AmortizationScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}