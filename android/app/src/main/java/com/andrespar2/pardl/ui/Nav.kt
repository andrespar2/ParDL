package com.andrespar2.pardl.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.andrespar2.pardl.core.DownloadKind
import com.andrespar2.pardl.core.music.HistoryStore

/** Pantallas de la aplicación. */
sealed interface Screen {
    data object Home : Screen
    data class Download(val kind: DownloadKind, val nonce: Int) : Screen
    data object Downloads : Screen
    data object History : Screen
    data object Providers : Screen
    data object Browser : Screen
    data object Sites : Screen
    data object Settings : Screen
    data object About : Screen
}

@Composable
fun ParDLAppRoot(
    sharedUrl: String?,
    onSharedConsumed: () -> Unit
) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var prefillUrl by remember { mutableStateOf<String?>(null) }
    var downloadNonce by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        HistoryStore.initialize(context)
    }

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

    AmbientBackground {
        Box(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            when (val current = screen) {
                is Screen.Home -> HomeScreen(
                    onVideo = {
                        prefillUrl = null; downloadNonce++
                        screen = Screen.Download(DownloadKind.VIDEO, downloadNonce)
                    },
                    onAudio = {
                        prefillUrl = null; downloadNonce++
                        screen = Screen.Download(DownloadKind.AUDIO, downloadNonce)
                    },
                    onMusic = { screen = Screen.Browser },
                    onDownloads = { screen = Screen.Downloads },
                    onHistory = { screen = Screen.History },
                    onProviders = { screen = Screen.Providers },
                    onBrowser = { screen = Screen.Browser },
                    onSites = { screen = Screen.Sites },
                    onSettings = { screen = Screen.Settings },
                    onAbout = { screen = Screen.About }
                )

                is Screen.Download -> DownloadScreen(
                    kind = current.kind,
                    navKey = current.nonce,
                    initialUrl = prefillUrl,
                    onBack = { screen = Screen.Home }
                )

                is Screen.Downloads -> DownloadsScreen(
                    onBack = { screen = Screen.Home },
                    onLegacy = { kind ->
                        prefillUrl = null; downloadNonce++
                        screen = Screen.Download(kind, downloadNonce)
                    }
                )

                is Screen.History -> HistoryScreen(onBack = { screen = Screen.Home })
                is Screen.Providers -> ProvidersScreen(onBack = { screen = Screen.Home })
                is Screen.Browser -> BrowserScreen(
                    onBack = { screen = Screen.Home },
                    onDownload = { url, kind ->
                        prefillUrl = url
                        downloadNonce++
                        screen = Screen.Download(kind, downloadNonce)
                    }
                )
                is Screen.Sites -> SitesScreen(onBack = { screen = Screen.Home })
                is Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Home })
                is Screen.About -> AboutScreen(onBack = { screen = Screen.Home })
            }
        }
    }
}
