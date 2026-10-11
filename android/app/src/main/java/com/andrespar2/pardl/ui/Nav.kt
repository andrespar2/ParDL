package com.andrespar2.pardl.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.andrespar2.pardl.core.DownloadKind
import com.andrespar2.pardl.ui.theme.AmoledBlack

/** Pantallas de la aplicación. */
sealed interface Screen {
    object Home : Screen
    data class Download(val kind: DownloadKind, val nonce: Int) : Screen
    object Sites : Screen
    object Settings : Screen
    object About : Screen
}

@Composable
fun ParDLAppRoot(sharedUrl: String?, onSharedConsumed: () -> Unit) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var prefillUrl by remember { mutableStateOf<String?>(null) }
    // Cambia en cada navegación a la pantalla de descarga para renovar su estado.
    var downloadNonce by remember { mutableStateOf(0) }

    // Maneja "Compartir enlace con ParDL".
    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            prefillUrl = sharedUrl
            downloadNonce++
            screen = Screen.Download(DownloadKind.VIDEO, downloadNonce)
            onSharedConsumed()
        }
    }

    BackHandler(enabled = screen !is Screen.Home) {
        screen = Screen.Home
    }

    Surface(color = AmoledBlack, modifier = Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(AmoledBlack)
                .systemBarsPadding()
        ) {
            when (val s = screen) {
                is Screen.Home -> HomeScreen(
                    onVideo = {
                        prefillUrl = null; downloadNonce++
                        screen = Screen.Download(DownloadKind.VIDEO, downloadNonce)
                    },
                    onAudio = {
                        prefillUrl = null; downloadNonce++
                        screen = Screen.Download(DownloadKind.AUDIO, downloadNonce)
                    },
                    onSites = { screen = Screen.Sites },
                    onSettings = { screen = Screen.Settings },
                    onAbout = { screen = Screen.About }
                )

                is Screen.Download -> DownloadScreen(
                    kind = s.kind,
                    navKey = s.nonce,
                    initialUrl = prefillUrl,
                    onBack = { screen = Screen.Home }
                )

                is Screen.Sites -> SitesScreen(onBack = { screen = Screen.Home })
                is Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Home })
                is Screen.About -> AboutScreen(onBack = { screen = Screen.Home })
            }
        }
    }
}
