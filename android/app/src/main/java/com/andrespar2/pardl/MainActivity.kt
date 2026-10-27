package com.andrespar2.pardl

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.andrespar2.pardl.core.Engine
import com.andrespar2.pardl.core.music.HistoryStore
import com.andrespar2.pardl.ui.ParDLAppRoot
import com.andrespar2.pardl.ui.theme.Appearance
import com.andrespar2.pardl.ui.theme.ParDLTheme

class MainActivity : ComponentActivity() {

    // Enlace recibido por "Compartir con ParDL".
    private val sharedUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Appearance.initialize(this)
        HistoryStore.initialize(this)
        Engine.ensureStarted(this)
        requestNotificationPermission()
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            ParDLTheme {
                ParDLAppRoot(
                    sharedUrl = sharedUrl.value,
                    onSharedConsumed = { sharedUrl.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                sharedUrl.value = extractFirstUrl(text)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS
            )
        }
    }

    /** Extrae el primer enlace http(s) de un texto compartido. */
    private fun extractFirstUrl(text: String): String {
        val regex = Regex("""https?://\S+""")
        return regex.find(text)?.value?.trim() ?: text.trim()
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1001
    }
}
